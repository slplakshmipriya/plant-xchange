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

router = APIRouter(prefix="/v1", tags=["feed"])

PAGE_LIMIT = 20
MAX_LIMIT = 50
_WINDOW_HOURS = 168.0  # 7 days: normalization window for urgency/freshness
WANT_MATCH_BOOST = 1.0


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


@router.get("/feed")
def get_feed(
    limit: int = Query(default=PAGE_LIMIT, ge=1, le=MAX_LIMIT),
    cursor: str | None = Query(default=None),
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    """Ranked discovery feed of live listings (fuzzed geo, no PII)."""
    offset = _decode_cursor(cursor)
    now = utcnow()
    live = repo.list_live()
    # Want-list boost is wired by the want-list module (API-030); feed itself
    # stays decoupled — score_listing takes an explicit boost.
    ranked = sorted(live, key=lambda r: (-score_listing(r, now), r["id"]))
    page = ranked[offset:offset + limit]
    next_cursor = _encode_cursor(offset + limit) if offset + limit < len(ranked) else None
    return {
        "items": [public_listing(r) for r in page],
        "next_cursor": next_cursor,
    }
