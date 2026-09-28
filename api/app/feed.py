"""Feed & discovery (API-021).

``GET /v1/feed`` returns live listings ranked by a transparent score:

- **urgency** (weight 0.6): expires soon first — PRD ranks by *time remaining*,
  not recency. A seedling with 1 day left outranks one listed an hour ago with
  6 days left.
- **freshness** (weight 0.2): recently listed breaks urgency ties.
- **want-list boost** (API-030): +1.0 when a seedling listing matches one of the
  caller's want-list varieties. Wired in by the want-list module via the
  ``boost`` parameter of ``score_listing``.

Cursor pagination: opaque base64 offset cursor. Geo is fuzzed via
``listings.public_listing`` — true coordinates never leave the server.
"""

from __future__ import annotations

import base64
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query

from .auth import get_current_uid
from .listings import ListingRepo, get_listing_repo, public_listing, utcnow
from .sitter import SitterRepo, _display_name, _serialize_profile, get_sitter_repo
from .users import UserRepo, get_user_repo
from .wantlist import WANT_MATCH_BOOST, WantRepo, get_want_repo, variety_matches

router = APIRouter(prefix="/v1", tags=["feed"])

PAGE_LIMIT = 20
MAX_LIMIT = 50
_WINDOW_HOURS = 168.0  # 7 days: normalization window for urgency/freshness
WANT_MATCH_BOOST = 1.0

# API-123: Explore way-cards. "pick" is pick-your-own -> tree listings;
# "sitting" lists sitter profiles instead of listings.
WAYS = ("seedling", "harvest", "pick", "sitting")
_WAY_TO_LISTING_TYPE = {"seedling": "seedling", "harvest": "harvest", "pick": "tree"}


def _parse_dt(value: Any) -> datetime | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        dt = value
    else:
        try:
            dt = datetime.fromisoformat(value)
        except (ValueError, TypeError):
            return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def score_listing(row: dict[str, Any], now: datetime, boost: float = 0.0) -> float:
    """Ranking score. Higher = shown first. Deterministic and testable."""
    exp = _parse_dt(row.get("expires_at"))
    hours_left = max((exp - now).total_seconds() / 3600.0, 0.0) if exp else _WINDOW_HOURS
    urgency = 1.0 - min(hours_left, _WINDOW_HOURS) / _WINDOW_HOURS

    created = _parse_dt(row.get("created_at"))
    age_hours = max((now - created).total_seconds() / 3600.0, 0.0) if created else _WINDOW_HOURS
    freshness = 1.0 - min(age_hours, _WINDOW_HOURS) / _WINDOW_HOURS

    return 0.6 * urgency + 0.2 * freshness + boost


def _encode_cursor(offset: int) -> str:
    return base64.urlsafe_b64encode(str(offset).encode()).decode()


def _decode_cursor(cursor: str | None) -> int:
    if not cursor:
        return 0
    try:
        offset = int(base64.urlsafe_b64decode(cursor.encode()).decode())
    except Exception:
        raise HTTPException(400, {"code": "invalid_cursor", "message": "Malformed feed cursor"})
    if offset < 0:
        raise HTTPException(400, {"code": "invalid_cursor", "message": "Malformed feed cursor"})
    return offset


def _expiry_key(row: dict[str, Any]) -> tuple[bool, float, str]:
    """Freshest-first by expires_at ascending, nulls last (API-123)."""
    dt = _parse_dt(row.get("expires_at"))
    return (dt is None, dt.timestamp() if dt else 0.0, str(row.get("id") or ""))


@router.get("/feed")
def get_feed(
    limit: int = Query(default=PAGE_LIMIT, ge=1, le=MAX_LIMIT),
    cursor: str | None = Query(default=None),
    way: str | None = Query(default=None, pattern="^(seedling|harvest|pick|sitting)$"),
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
    want_repo: WantRepo = Depends(get_want_repo),
    sitter_repo: SitterRepo = Depends(get_sitter_repo),
    user_repo: UserRepo = Depends(get_user_repo),
) -> dict[str, Any]:
    """Ranked discovery feed of live listings (fuzzed geo, no PII).

    Without ``way``: the scored, cursor-paginated feed (API-021).
    With ``way`` (API-123): exact ``{"listings": [...]}`` for one Explore
    way-card, ranked freshest-first by expiry ascending (nulls last).
    """
    if way is not None:
        if way == "sitting":
            profiles = [
                _serialize_profile(r, _display_name(user_repo, r["uid"]))
                for r in sitter_repo.list_active()
            ]
            return {"listings": profiles[:limit]}
        live = [r for r in repo.list_live() if r.get("type") == _WAY_TO_LISTING_TYPE[way]]
        ranked = sorted(live, key=_expiry_key)
        return {"listings": [public_listing(r) for r in ranked[:limit]]}

    offset = _decode_cursor(cursor)
    now = utcnow()
    live = repo.list_live()
    # API-030: seedling listings matching the caller's want-list get a boost.
    wants = [w["variety"] for w in want_repo.list_for_user(uid)]

    def boost(row: dict[str, Any]) -> float:
        if row.get("type") != "seedling":
            return 0.0
        return WANT_MATCH_BOOST if any(
            variety_matches(w, row.get("variety")) for w in wants
        ) else 0.0

    ranked = sorted(live, key=lambda r: (-score_listing(r, now, boost(r)), r["id"]))
    page = ranked[offset:offset + limit]
    next_cursor = _encode_cursor(offset + limit) if offset + limit < len(ranked) else None
    return {
        "items": [public_listing(r) for r in page],
        "next_cursor": next_cursor,
    }
