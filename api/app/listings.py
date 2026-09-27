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
from pydantic import BaseModel, Field

from .auth import ensure_owner, get_current_uid
from .config import get_settings
from .db import get_db_conn
from .users import UserRepo, get_user_repo

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
    }


# ---------------------------------------------------------------- repository

class ListingRepo(Protocol):
    def create(self, data: dict[str, Any]) -> dict[str, Any]: ...
    def get(self, listing_id: str) -> dict[str, Any] | None: ...
    def update(self, listing_id: str, fields: dict[str, Any]) -> dict[str, Any] | None: ...
    def set_status(self, listing_id: str, status: str) -> dict[str, Any] | None: ...
    def sweep_expired(self, now: datetime) -> int: ...


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
        "expires_at, geo_lat, geo_lon, spray_disclosure, status, created_at FROM listings"
    )

    def create(self, data: dict[str, Any]) -> dict[str, Any]:
        window = data.get("pickup_window")
        row = self._conn.execute(
            "INSERT INTO listings (id, owner_uid, type, photos, variety, quantity, unit, "
            "credit_cost, pickup_window, expires_at, geo_lat, geo_lon, spray_disclosure, status) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,tstzrange(%s,%s,'[)'),%s,%s,%s,%s,%s) "
            + "RETURNING id",
            (
                data["id"], data["owner_uid"], data["type"], data["photos"],
                data.get("variety"), data.get("quantity"), data.get("unit"),
                data["credit_cost"],
                window[0] if window else None, window[1] if window else None,
                data.get("expires_at"), data.get("geo_lat"), data.get("geo_lon"),
                data["spray_disclosure"], data.get("status", "draft"),
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


class MemoryListingRepo:
    def __init__(self):
        self._rows: dict[str, dict[str, Any]] = {}

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


def get_listing_repo(conn=Depends(get_db_conn)) -> ListingRepo:
    return PostgresListingRepo(conn)


# ---------------------------------------------------------------- API models

class PickupWindow(BaseModel):
    start: datetime
    end: datetime


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
    })
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
    updated = repo.update(listing_id, fields)
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
