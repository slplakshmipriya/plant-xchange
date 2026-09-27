"""Listing CRUD + lifecycle state machine (API-020).

One engine serves seedling, harvest, and tree listings. Rules enforced
server-side:

- Photo required: a listing cannot go live (or be created live) without >= 1
  photo URL.
- ``spray_disclosure`` is mandatory text on every listing (PRD: no silent
  pesticide use).
- ``credit_cost`` bounded to 1..3 (DB CHECK + API validation).
- Lifecycle is a strict state machine; illegal transitions are 422, never
  silently coerced. Terminal states: completed, expired, cancelled.
- SEC-010: responses carry FUZZED geo (~0.5 mi jitter) via ``fuzz_location``.
  True coordinates never leave the server. (Exact address stays hidden until
  the exchange-confirm flow lands in a later wave.)
- Expiry: ``POST /v1/internal/sweep`` flips live->expired past ``expires_at``.
  Idempotent; service-to-service auth via ``X-Sweep-Secret``.
"""

from __future__ import annotations

import hmac
import math
import random
import uuid
from datetime import datetime, timezone
from typing import Any, Protocol

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field, field_validator

from .auth import ensure_owner, get_current_uid
from .config import get_settings
from .db import get_db_conn
from .notify import NotificationRepo, get_notification_repo, send_notification
from .users import UserRepo, get_user_repo
from .wantlist import WantRepo, get_want_repo, notify_matches

router = APIRouter(prefix="/v1", tags=["listings"])
internal_router = APIRouter(prefix="/v1/internal", tags=["internal"])

LISTING_TYPES = ("seedling", "harvest", "tree")
STATUSES = ("draft", "live", "claimed", "completed", "expired", "cancelled")

# Server-side lifecycle. Only these transitions are legal.
TRANSITIONS: dict[str, set[str]] = {
    "draft": {"live", "cancelled"},
    "live": {"claimed", "expired", "cancelled"},
    "claimed": {"completed", "cancelled", "live"},
    "completed": set(),
    "expired": set(),
    "cancelled": set(),
}

EDITABLE_STATUSES = ("draft", "live")  # PATCH allowed only in these states


def can_transition(frm: str, to: str) -> bool:
    return to in TRANSITIONS.get(frm, set())


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _coerce_utc(value: datetime | None) -> datetime | None:
    """Treat naive datetimes as UTC. Clients that omit the offset (e.g. local
    ISO strings) must not 500 the naive/aware comparison or poison the sweep
    job — assume UTC and say so in the stored value."""
    if isinstance(value, datetime) and value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


# ---------------------------------------------------------------- geo fuzzing

def fuzz_location(lat: float, lon: float, rng: random.Random | None = None) -> tuple[float, float]:
    """Jitter a coordinate by up to ~0.5 mi in a random direction (SEC-010).

    ``rng`` is injectable for deterministic tests; routes use system randomness.
    """
    r = rng or random.SystemRandom()
    miles = r.uniform(0.05, 0.5)  # never return the exact point
    theta = r.uniform(0, 2 * math.pi)
    dlat = miles / 69.0 * math.cos(theta)
    dlon = miles / (69.0 * math.cos(math.radians(lat))) * math.sin(theta)
    return lat + dlat, lon + dlon


def public_listing(row: dict[str, Any], rng: random.Random | None = None) -> dict[str, Any]:
    """Public serializer: fuzzed geo, no owner PII (owner is just a uid)."""
    lat, lon = row.get("geo_lat"), row.get("geo_lon")
    flat, flon = (fuzz_location(lat, lon, rng) if lat is not None and lon is not None else (None, None))
    window = row.get("pickup_window")
    return {
        "id": str(row["id"]),
        "owner_uid": row["owner_uid"],
        "type": row["type"],
        "photos": list(row.get("photos") or []),
        "variety": row.get("variety"),
        "quantity": float(row["quantity"]) if row.get("quantity") is not None else None,
        "unit": row.get("unit"),
        "credit_cost": row["credit_cost"],
        "pickup_window": {"start": window[0], "end": window[1]} if window else None,
        "expires_at": row.get("expires_at"),
        "geo_lat": flat,
        "geo_lon": flon,
        "spray_disclosure": row.get("spray_disclosure"),
        "status": row["status"],
        "created_at": row.get("created_at"),
        "remaining_qty": (float(row["remaining_qty"])
                          if row.get("remaining_qty") is not None else None),
        "visit_rules": row.get("visit_rules"),
    }


# ---------------------------------------------------------------- repository

class ListingRepo(Protocol):
    def create(self, data: dict[str, Any]) -> dict[str, Any]: ...
    def get(self, listing_id: str) -> dict[str, Any] | None: ...
    def update(self, listing_id: str, fields: dict[str, Any]) -> dict[str, Any] | None: ...
    def set_status(self, listing_id: str, status: str) -> dict[str, Any] | None: ...
    def sweep_expired(self, now: datetime) -> int: ...
    def list_live(self) -> list[dict[str, Any]]: ...
    def decrement_remaining(self, listing_id: str, delta: float) -> dict[str, Any] | None:
        """Atomically subtract delta from remaining (COALESCE remaining_qty, quantity).
        Returns the updated row, or None when the listing is missing or delta
        exceeds what is available."""
        ...
    def log_harvest_event(self, listing_id: str, recorder_uid: str,
                          delta_kg: float, remaining_after: float) -> None: ...
    def list_harvest_events(self, listing_id: str) -> list[dict[str, Any]]: ...


class PostgresListingRepo:
    def __init__(self, conn):
        self._conn = conn

    @staticmethod
    def _row(row) -> dict:
        d = dict(row)
        lo = d.pop("window_start", None)
        hi = d.pop("window_end", None)
        d["pickup_window"] = (lo.isoformat() if lo else None, hi.isoformat() if hi else None) if lo or hi else None
        for k in ("expires_at", "created_at"):
            v = d.get(k)
            d[k] = v.isoformat() if hasattr(v, "isoformat") else v
        return d

    _SELECT = (
        "SELECT id, owner_uid, type, photos, variety, quantity, unit, credit_cost, "
        "lower(pickup_window) AS window_start, upper(pickup_window) AS window_end, "
        "expires_at, geo_lat, geo_lon, spray_disclosure, status, created_at, "
        "COALESCE(remaining_qty, quantity) AS remaining_qty, visit_rules FROM listings"
    )

    def create(self, data: dict[str, Any]) -> dict[str, Any]:
        window = data.get("pickup_window")
        row = self._conn.execute(
            "INSERT INTO listings (id, owner_uid, type, photos, variety, quantity, unit, "
            "credit_cost, pickup_window, expires_at, geo_lat, geo_lon, spray_disclosure, status, "
            "remaining_qty, visit_rules) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,tstzrange(%s,%s,'[)'),%s,%s,%s,%s,%s,%s,%s) "
            + "RETURNING id",
            (
                data["id"], data["owner_uid"], data["type"], data["photos"],
                data.get("variety"), data.get("quantity"), data.get("unit"),
                data["credit_cost"],
                window[0] if window else None, window[1] if window else None,
                data.get("expires_at"), data.get("geo_lat"), data.get("geo_lon"),
                data["spray_disclosure"], data.get("status", "draft"),
                data.get("remaining_qty"), data.get("visit_rules"),
            ),
        ).fetchone()
        self._conn.commit()
        return self.get(str(row["id"]))

    def get(self, listing_id: str) -> dict[str, Any] | None:
        row = self._conn.execute(self._SELECT + " WHERE id = %s", (listing_id,)).fetchone()
        return self._row(row) if row else None

    def update(self, listing_id: str, fields: dict[str, Any]) -> dict[str, Any] | None:
        if not fields:
            return self.get(listing_id)
        window = fields.pop("pickup_window", None)
        sets, params = [], []
        for k, v in fields.items():
            sets.append(f"{k} = %s")
            params.append(v)
        if window is not None:
            sets.append("pickup_window = tstzrange(%s,%s,'[)')")
            params.extend([window[0], window[1]])
        params.append(listing_id)
        self._conn.execute(f"UPDATE listings SET {', '.join(sets)} WHERE id = %s", params)
        self._conn.commit()
        return self.get(listing_id)

    def set_status(self, listing_id: str, status: str) -> dict[str, Any] | None:
        self._conn.execute("UPDATE listings SET status = %s WHERE id = %s", (status, listing_id))
        self._conn.commit()
        return self.get(listing_id)

    def sweep_expired(self, now: datetime) -> int:
        cur = self._conn.execute(
            "UPDATE listings SET status = 'expired' "
            "WHERE status = 'live' AND expires_at IS NOT NULL AND expires_at < %s",
            (now,),
        )
        self._conn.commit()
        return cur.rowcount or 0

    def list_live(self) -> list[dict[str, Any]]:
        rows = self._conn.execute(
            self._SELECT + " WHERE status = 'live' "
            "ORDER BY expires_at NULLS LAST, created_at DESC"
        ).fetchall()
        return [self._row(r) for r in rows]

    def decrement_remaining(self, listing_id: str, delta: float) -> dict[str, Any] | None:
        # Single atomic UPDATE: concurrent pickers cannot oversell the harvest.
        cur = self._conn.execute(
            "UPDATE listings SET remaining_qty = COALESCE(remaining_qty, quantity) - %s "
            "WHERE id = %s AND COALESCE(remaining_qty, quantity) >= %s",
            (delta, listing_id, delta),
        )
        self._conn.commit()
        if not cur.rowcount:
            return None
        return self.get(listing_id)

    def log_harvest_event(self, listing_id: str, recorder_uid: str,
                          delta_kg: float, remaining_after: float) -> None:
        self._conn.execute(
            "INSERT INTO harvest_events (id, listing_id, recorder_uid, delta_kg, remaining_after) "
            "VALUES (%s,%s,%s,%s,%s)",
            (str(uuid.uuid4()), listing_id, recorder_uid, delta_kg, remaining_after),
        )
        self._conn.commit()

    def list_harvest_events(self, listing_id: str) -> list[dict[str, Any]]:
        rows = self._conn.execute(
            "SELECT id, listing_id, recorder_uid, delta_kg, remaining_after, created_at "
            "FROM harvest_events WHERE listing_id = %s ORDER BY created_at",
            (listing_id,),
        ).fetchall()
        out = []
        for r in rows:
            d = dict(r)
            c = d.get("created_at")
            d["created_at"] = c.isoformat() if hasattr(c, "isoformat") else c
            d["delta_kg"] = float(d["delta_kg"])
            d["remaining_after"] = float(d["remaining_after"])
            out.append(d)
        return out


class MemoryListingRepo:
    def __init__(self):
        self._rows: dict[str, dict[str, Any]] = {}
        self._harvest_events: list[dict[str, Any]] = []

    def create(self, data: dict[str, Any]) -> dict[str, Any]:
        row = dict(data)
        row.setdefault("created_at", utcnow().isoformat())
        self._rows[row["id"]] = row
        return dict(row)

    def get(self, listing_id: str) -> dict[str, Any] | None:
        row = self._rows.get(listing_id)
        return dict(row) if row else None

    def update(self, listing_id: str, fields: dict[str, Any]) -> dict[str, Any] | None:
        row = self._rows.get(listing_id)
        if row is None:
            return None
        row.update(fields)
        return dict(row)

    def set_status(self, listing_id: str, status: str) -> dict[str, Any] | None:
        return self.update(listing_id, {"status": status})

    def sweep_expired(self, now: datetime) -> int:
        n = 0
        for row in self._rows.values():
            exp = row.get("expires_at")
            try:
                exp_dt = datetime.fromisoformat(exp) if isinstance(exp, str) else exp
            except (ValueError, TypeError):
                continue
            if row["status"] == "live" and exp_dt and exp_dt < now:
                row["status"] = "expired"
                n += 1
        return n

    def list_live(self) -> list[dict[str, Any]]:
        return [dict(r) for r in self._rows.values() if r.get("status") == "live"]

    def decrement_remaining(self, listing_id: str, delta: float) -> dict[str, Any] | None:
        row = self._rows.get(listing_id)
        if row is None:
            return None
        avail = row.get("remaining_qty")
        if avail is None:
            avail = row.get("quantity")
        if avail is None or float(avail) < delta:
            return None
        new_remaining = float(avail) - delta
        if new_remaining < 0 and new_remaining > -1e-9:  # float dust
            new_remaining = 0.0
        row["remaining_qty"] = new_remaining
        return dict(row)

    def log_harvest_event(self, listing_id: str, recorder_uid: str,
                          delta_kg: float, remaining_after: float) -> None:
        self._harvest_events.append({
            "id": str(uuid.uuid4()),
            "listing_id": listing_id,
            "recorder_uid": recorder_uid,
            "delta_kg": float(delta_kg),
            "remaining_after": float(remaining_after),
            "created_at": utcnow().isoformat(),
        })

    def list_harvest_events(self, listing_id: str) -> list[dict[str, Any]]:
        return [dict(e) for e in self._harvest_events if e["listing_id"] == listing_id]


def get_listing_repo(conn=Depends(get_db_conn)) -> ListingRepo:
    return PostgresListingRepo(conn)


# ---------------------------------------------------------------- API models

class PickupWindow(BaseModel):
    start: datetime
    end: datetime

    @field_validator("start", "end", mode="after")
    @classmethod
    def _utc_window(cls, v):
        return _coerce_utc(v)


class ListingIn(BaseModel):
    type: str = Field(pattern="^(seedling|harvest|tree)$")
    photos: list[str] = Field(min_length=1)
    variety: str | None = Field(default=None, max_length=120)
    quantity: float | None = Field(default=None, gt=0)
    unit: str | None = Field(default=None, max_length=20)
    credit_cost: int = Field(ge=1, le=3)
    pickup_window: PickupWindow | None = None
    expires_at: datetime | None = None
    geo_lat: float | None = Field(default=None, ge=-90, le=90)
    geo_lon: float | None = Field(default=None, ge=-180, le=180)
    spray_disclosure: str = Field(min_length=1, max_length=2000)
    status: str = Field(default="draft", pattern="^(draft|live)$")
    visit_rules: str | None = Field(default=None, max_length=2000)

    @field_validator("expires_at", mode="after")
    @classmethod
    def _utc_expires(cls, v):
        return _coerce_utc(v)


class ListingPatch(BaseModel):
    photos: list[str] | None = Field(default=None, min_length=1)
    variety: str | None = Field(default=None, max_length=120)
    quantity: float | None = Field(default=None, gt=0)
    unit: str | None = Field(default=None, max_length=20)
    credit_cost: int | None = Field(default=None, ge=1, le=3)
    pickup_window: PickupWindow | None = None
    expires_at: datetime | None = None
    spray_disclosure: str | None = Field(default=None, min_length=1, max_length=2000)
    status: str | None = Field(default=None, pattern="^(draft|live|claimed|completed|cancelled)$")
    visit_rules: str | None = Field(default=None, max_length=2000)

    @field_validator("expires_at", mode="after")
    @classmethod
    def _utc_expires(cls, v):
        return _coerce_utc(v)


def _validate_common(data: ListingIn | ListingPatch) -> None:
    for url in data.photos or []:
        if not url.startswith(("https://", "http://")):
            raise HTTPException(400, {"code": "invalid_photo_url", "message": "photo URLs must be http(s)"})
    window = data.pickup_window
    if window and window.end <= window.start:
        raise HTTPException(400, {"code": "invalid_window", "message": "pickup window end must be after start"})
    if data.expires_at and data.expires_at <= utcnow():
        raise HTTPException(400, {"code": "invalid_expiry", "message": "expires_at must be in the future"})


@router.post("/listings", status_code=201)
def create_listing(
    data: ListingIn,
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
    user_repo: UserRepo = Depends(get_user_repo),
    want_repo: WantRepo = Depends(get_want_repo),
    notify_repo: NotificationRepo = Depends(get_notification_repo),
) -> dict[str, Any]:
    _validate_common(data)
    if user_repo.get(uid) is None:
        raise HTTPException(400, {"code": "profile_required",
                                  "message": "Create a profile (POST /v1/users) before listing"})
    row = repo.create({
        "id": str(uuid.uuid4()),
        "owner_uid": uid,
        "type": data.type,
        "photos": data.photos,
        "variety": data.variety,
        "quantity": data.quantity,
        "unit": data.unit,
        "credit_cost": data.credit_cost,
        "pickup_window": (data.pickup_window.start.isoformat(), data.pickup_window.end.isoformat())
        if data.pickup_window else None,
        "expires_at": data.expires_at.isoformat() if data.expires_at else None,
        "geo_lat": data.geo_lat,
        "geo_lon": data.geo_lon,
        "spray_disclosure": data.spray_disclosure.strip(),
        "status": data.status,
        # Harvest listings track what is left to pick (API-040).
        "remaining_qty": data.quantity if data.type == "harvest" else None,
        "visit_rules": data.visit_rules.strip() if data.visit_rules else None,
    })
    if row["status"] == "live":
        # A listing going live is the match event (API-030).
        notify_matches(row, want_repo, notify_repo)
    return public_listing(row)


@router.get("/listings/{listing_id}")
def get_listing(
    listing_id: str,
    repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    row = repo.get(listing_id)
    if row is None:
        raise HTTPException(404, {"code": "listing_not_found", "message": "No such listing"})
    return public_listing(row)


@router.patch("/listings/{listing_id}")
def patch_listing(
    listing_id: str,
    data: ListingPatch,
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
    want_repo: WantRepo = Depends(get_want_repo),
    notify_repo: NotificationRepo = Depends(get_notification_repo),
) -> dict[str, Any]:
    row = repo.get(listing_id)
    if row is None:
        raise HTTPException(404, {"code": "listing_not_found", "message": "No such listing"})
    ensure_owner(row["owner_uid"], uid)
    if row["status"] not in EDITABLE_STATUSES:
        raise HTTPException(422, {"code": "listing_locked",
                                  "message": f"Cannot edit a listing in status '{row['status']}'"})
    _validate_common(data)
    fields: dict[str, Any] = {k: v for k, v in data.model_dump(exclude_unset=True).items()
                              if v is not None and k != "status"}
    if "pickup_window" in fields and fields["pickup_window"]:
        w = fields["pickup_window"]
        fields["pickup_window"] = (w["start"], w["end"])
    if data.status and data.status != row["status"]:
        if not can_transition(row["status"], data.status):
            raise HTTPException(422, {"code": "invalid_transition",
                                      "message": f"Cannot move listing from '{row['status']}' to '{data.status}'"})
        fields["status"] = data.status
    if "spray_disclosure" in fields:
        fields["spray_disclosure"] = fields["spray_disclosure"].strip()
    if "visit_rules" in fields and fields["visit_rules"]:
        fields["visit_rules"] = fields["visit_rules"].strip()
    updated = repo.update(listing_id, fields)
    if updated and row["status"] != "live" and updated.get("status") == "live":
        # draft -> live is the match event (API-030).
        notify_matches(updated, want_repo, notify_repo)
    return public_listing(updated)


@router.post("/listings/{listing_id}/cancel")
def cancel_listing(
    listing_id: str,
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    row = repo.get(listing_id)
    if row is None:
        raise HTTPException(404, {"code": "listing_not_found", "message": "No such listing"})
    ensure_owner(row["owner_uid"], uid)
    if not can_transition(row["status"], "cancelled"):
        raise HTTPException(422, {"code": "invalid_transition",
                                  "message": f"Cannot cancel a listing in status '{row['status']}'"})
    return public_listing(repo.set_status(listing_id, "cancelled"))


class HarvestEventIn(BaseModel):
    listing_id: str = Field(min_length=1)
    delta_kg: float = Field(gt=0, le=10000)


@router.post("/harvest-events", status_code=201, tags=["listings"])
def record_harvest_event(
    data: HarvestEventIn,
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    """Record kilos picked from a harvest listing (owner only, live only).

    Atomic decrement — concurrent pickers cannot oversell. When the harvest
    is fully picked (remaining hits 0) the listing completes via the legal
    live -> claimed -> completed path.
    """
    row = repo.get(data.listing_id)
    if row is None:
        raise HTTPException(404, {"code": "listing_not_found", "message": "No such listing"})
    ensure_owner(row["owner_uid"], uid)
    if row["type"] != "harvest":
        raise HTTPException(422, {"code": "not_harvest_listing",
                                  "message": "Harvest events apply to harvest listings only"})
    if row["status"] != "live":
        raise HTTPException(422, {"code": "listing_not_live",
                                  "message": f"Cannot record a pick on a '{row['status']}' listing"})
    if row.get("remaining_qty") is None:
        raise HTTPException(422, {"code": "quantity_not_tracked",
                                  "message": "This harvest listing has no quantity to pick from"})
    updated = repo.decrement_remaining(data.listing_id, data.delta_kg)
    if updated is None:
        raise HTTPException(422, {"code": "insufficient_quantity",
                                  "message": "Not that much harvest remaining"})
    remaining = float(updated["remaining_qty"])
    repo.log_harvest_event(data.listing_id, uid, data.delta_kg, remaining)
    if remaining == 0:
        # Fully picked: walk the legal transitions, no state-machine bypass.
        repo.set_status(data.listing_id, "claimed")
        updated = repo.set_status(data.listing_id, "completed")
    return {
        "listing": public_listing(updated),
        "delta_kg": data.delta_kg,
        "remaining_kg": remaining,
    }


@router.get("/listings/{listing_id}/harvest-events", tags=["listings"])
def get_harvest_events(
    listing_id: str,
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    """Pick audit log for a harvest listing. Owner and authenticated viewers."""
    row = repo.get(listing_id)
    if row is None:
        raise HTTPException(404, {"code": "listing_not_found", "message": "No such listing"})
    return {"listing_id": listing_id, "events": repo.list_harvest_events(listing_id)}


@router.post("/trees/{listing_id}/ripe-alert", tags=["trees"])
def ripe_alert(
    listing_id: str,
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
    want_repo: WantRepo = Depends(get_want_repo),
    notify_repo: NotificationRepo = Depends(get_notification_repo),
) -> dict[str, Any]:
    """Notify want-list matches that a tree is ripe for picking (API-050).

    Owner-only. The notify layer's 24h dedupe on ref ``<listing>:<date>``
    makes this safe to re-trigger — each user gets at most one ripe alert
    per tree per day.
    """
    from .wantlist import RIPE_ALERT_CATEGORY, find_matches

    row = repo.get(listing_id)
    if row is None:
        raise HTTPException(404, {"code": "listing_not_found", "message": "No such listing"})
    ensure_owner(row["owner_uid"], uid)
    if row["type"] != "tree":
        raise HTTPException(422, {"code": "not_tree_listing",
                                  "message": "Ripe alerts apply to tree listings only"})
    today = utcnow().date().isoformat()
    ref = f"{listing_id}:{today}"
    notified = 0
    for entry in find_matches(row, want_repo.list_all()):
        result = send_notification(
            entry["user_uid"],
            RIPE_ALERT_CATEGORY,
            "Fruit is ripe near you",
            f"{row.get('variety') or 'A tree'} you want is ripe for picking.",
            data={"listing_id": listing_id},
            ref=ref,
            repo=notify_repo,
        )
        if result["status"] in ("sent", "would_send"):
            notified += 1
    return {"listing_id": listing_id, "notified": notified, "date": today}


@internal_router.post("/sweep")
def sweep_expired(
    request: Request,
    repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    """Idempotent expiry job. Auth: shared secret header (NOT a user token)."""
    secret = get_settings().sweep_secret
    if not secret:
        raise HTTPException(503, {"code": "sweep_not_configured",
                                  "message": "SWEEP_SECRET is not configured"})
    presented = request.headers.get("x-sweep-secret", "")
    if not hmac.compare_digest(secret, presented):
        raise HTTPException(401, {"code": "unauthorized", "message": "Bad sweep secret"})
    n = repo.sweep_expired(utcnow())
    return {"expired": n}
