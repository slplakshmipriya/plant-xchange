"""Notification service (API-081).

``send_notification(uid, category, title, body, data, ...)`` is the single
choke point for every push the backend emits (harvest alerts, want-list
matches, expiry nudges, ...). Guards, in order:

1. Per-category opt-in (``notification_prefs``; missing category = opted in).
2. Quiet hours 21:00–08:00 in ``USER_TZ`` (default America/New_York), unless
   the user disabled them.
3. Dedupe: same (category, ref) within 24h is skipped.
4. Per-user cap: ``NOTIFY_DAILY_CAP`` sends per rolling 24h (default 5).

Delivery is FCM via firebase-admin. When credentials are absent (local dev,
CI), the send is recorded as ``would_send`` — test-visible and honest about
what happened. Every attempt is written to ``notification_log``.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Any, Protocol
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from .auth import get_current_uid
from .config import get_settings
from .db import get_db_conn

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/v1/users/me", tags=["notifications"])

DELIVERED = ("sent", "would_send")  # outcomes that count toward dedupe/cap
DEDUPE_WINDOW = timedelta(hours=24)
CAP_WINDOW = timedelta(hours=24)
QUIET_START_HOUR = 21  # 21:00 local ...
QUIET_END_HOUR = 8     # ... to 08:00 local


def _now() -> datetime:
    """UTC now. Monkeypatchable seam for tests (freeze time)."""
    return datetime.now(timezone.utc)


def _local_hour(now: datetime) -> int:
    return now.astimezone(ZoneInfo(get_settings().user_tz)).hour


def in_quiet_hours(now: datetime | None = None) -> bool:
    hour = _local_hour(now or _now())
    return hour >= QUIET_START_HOUR or hour < QUIET_END_HOUR


class NotificationRepo(Protocol):
    def get_prefs(self, uid: str) -> dict[str, Any] | None: ...
    def set_prefs(self, uid: str, categories: dict[str, bool], quiet_hours: bool) -> dict[str, Any]: ...
    def has_recent(self, uid: str, category: str, ref: str, since: datetime) -> bool: ...
    def count_since(self, uid: str, since: datetime) -> int: ...
    def log(self, uid: str, category: str, ref: str, outcome: str) -> None: ...


class PostgresNotificationRepo:
    def __init__(self, conn):
        self._conn = conn

    def get_prefs(self, uid: str) -> dict[str, Any] | None:
        row = self._conn.execute(
            "SELECT user_uid, categories, quiet_hours FROM notification_prefs WHERE user_uid = %s",
            (uid,),
        ).fetchone()
        return dict(row) if row else None

    def set_prefs(self, uid: str, categories: dict[str, bool], quiet_hours: bool) -> dict[str, Any]:
        import json as _json

        self._conn.execute(
            "INSERT INTO notification_prefs (user_uid, categories, quiet_hours) VALUES (%s,%s,%s) "
            "ON CONFLICT (user_uid) DO UPDATE SET categories = EXCLUDED.categories, "
            "quiet_hours = EXCLUDED.quiet_hours",
            (uid, _json.dumps(categories), quiet_hours),
        )
        self._conn.commit()
        return self.get_prefs(uid)

    def has_recent(self, uid: str, category: str, ref: str, since: datetime) -> bool:
        row = self._conn.execute(
            "SELECT 1 FROM notification_log WHERE user_uid = %s AND category = %s "
            "AND ref = %s AND sent_at > %s AND outcome = ANY(%s) LIMIT 1",
            (uid, category, ref, since, list(DELIVERED)),
        ).fetchone()
        return row is not None

    def count_since(self, uid: str, since: datetime) -> int:
        row = self._conn.execute(
            "SELECT COUNT(*) AS n FROM notification_log WHERE user_uid = %s "
            "AND sent_at > %s AND outcome = ANY(%s)",
            (uid, since, list(DELIVERED)),
        ).fetchone()
        return int(row["n"])

    def log(self, uid: str, category: str, ref: str, outcome: str) -> None:
        self._conn.execute(
            "INSERT INTO notification_log (user_uid, category, ref, outcome) VALUES (%s,%s,%s,%s)",
            (uid, category, ref, outcome),
        )
        self._conn.commit()


class MemoryNotificationRepo:
    def __init__(self):
        self._prefs: dict[str, dict[str, Any]] = {}
        self._log: list[dict[str, Any]] = []

    def get_prefs(self, uid: str) -> dict[str, Any] | None:
        prefs = self._prefs.get(uid)
        return dict(prefs) if prefs else None

    def set_prefs(self, uid: str, categories: dict[str, bool], quiet_hours: bool) -> dict[str, Any]:
        self._prefs[uid] = {"user_uid": uid, "categories": dict(categories), "quiet_hours": quiet_hours}
        return dict(self._prefs[uid])

    def has_recent(self, uid: str, category: str, ref: str, since: datetime) -> bool:
        return any(
            e["user_uid"] == uid and e["category"] == category and e["ref"] == ref
            and e["sent_at"] > since and e["outcome"] in DELIVERED
            for e in self._log
        )

    def count_since(self, uid: str, since: datetime) -> int:
        return sum(
            1 for e in self._log
            if e["user_uid"] == uid and e["sent_at"] > since and e["outcome"] in DELIVERED
        )

    def log(self, uid: str, category: str, ref: str, outcome: str) -> None:
        self._log.append({"user_uid": uid, "category": category, "ref": ref,
                          "outcome": outcome, "sent_at": _now()})


def get_notification_repo(conn=Depends(get_db_conn)) -> NotificationRepo:
    return PostgresNotificationRepo(conn)


def default_fcm_sender(uid: str, title: str, body: str, data: dict[str, str]) -> None:
    """Send via firebase-admin to the per-user topic.

    MVP delivery contract: the Android client MUST subscribe to topic
    ``user_{uid}`` after sign-in (see AND-002 follow-up). Token-targeted
    sends (via a device-registration endpoint) are the Wave 2 hardening —
    topics have no ACLs, so treat them as a convenience channel, never as
    carrying secrets.
    Raises when credentials/app are unavailable.
    """
    import firebase_admin
    from firebase_admin import messaging

    firebase_admin.get_app()  # raises ValueError when uninitialized
    messaging.send(messaging.Message(
        notification=messaging.Notification(title=title, body=body),
        data={k: str(v) for k, v in (data or {}).items()},
        topic=f"user_{uid}",
    ))


def send_notification(
    uid: str,
    category: str,
    title: str,
    body: str,
    data: dict[str, str] | None = None,
    *,
    ref: str = "",
    repo: NotificationRepo,
    sender=default_fcm_sender,
) -> dict[str, str]:
    """Run the guard chain and deliver (or honestly record why not)."""
    now = _now()
    prefs = repo.get_prefs(uid) or {"categories": {}, "quiet_hours": True}

    if not prefs["categories"].get(category, True):
        repo.log(uid, category, ref, "skipped_opted_out")
        return {"status": "skipped", "reason": "opted_out"}

    if prefs["quiet_hours"] and in_quiet_hours(now):
        repo.log(uid, category, ref, "skipped_quiet_hours")
        return {"status": "skipped", "reason": "quiet_hours"}

    if ref and repo.has_recent(uid, category, ref, now - DEDUPE_WINDOW):
        repo.log(uid, category, ref, "skipped_duplicate")
        return {"status": "skipped", "reason": "duplicate"}

    cap = get_settings().notify_daily_cap
    if repo.count_since(uid, now - CAP_WINDOW) >= cap:
        repo.log(uid, category, ref, "skipped_rate_capped")
        return {"status": "skipped", "reason": "rate_capped"}

    try:
        sender(uid, title, body, data or {})
    except Exception as exc:  # noqa: BLE001 — no creds in dev/CI is expected
        logger.info("FCM unavailable, recording would-send: %s", type(exc).__name__)
        repo.log(uid, category, ref, "would_send")
        return {"status": "would_send", "reason": "fcm_unavailable"}

    repo.log(uid, category, ref, "sent")
    return {"status": "sent", "reason": "delivered"}


class PrefsIn(BaseModel):
    categories: dict[str, bool] = Field(default_factory=dict)
    quiet_hours: bool = True


@router.put("/notification-prefs")
def put_notification_prefs(
    data: PrefsIn,
    uid: str = Depends(get_current_uid),
    repo: NotificationRepo = Depends(get_notification_repo),
) -> dict[str, Any]:
    prefs = repo.set_prefs(uid, data.categories, data.quiet_hours)
    return {
        "user_uid": uid,
        "categories": prefs["categories"],
        "quiet_hours": prefs["quiet_hours"],
    }


@router.get("/notification-prefs")
def get_notification_prefs(
    uid: str = Depends(get_current_uid),
    repo: NotificationRepo = Depends(get_notification_repo),
) -> dict[str, Any]:
    prefs = repo.get_prefs(uid) or {"categories": {}, "quiet_hours": True}
    return {"user_uid": uid, "categories": prefs["categories"], "quiet_hours": prefs["quiet_hours"]}
