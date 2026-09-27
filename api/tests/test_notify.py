"""API-081: notification guards — opt-in, quiet hours, dedupe, daily cap."""
from __future__ import annotations

from datetime import datetime, timezone

import pytest

import app.notify as notify_mod
from app.notify import MemoryNotificationRepo, send_notification

# 2026-09-27/28: America/New_York is EDT (UTC-4).
DAY_10AM_EDT = datetime(2026, 9, 28, 14, 0, tzinfo=timezone.utc)   # 10:00 local
NIGHT_10PM_EDT = datetime(2026, 9, 28, 2, 0, tzinfo=timezone.utc)   # 22:00 local
NIGHT_7AM_EDT = datetime(2026, 9, 28, 11, 0, tzinfo=timezone.utc)   # 07:00 local
EDGE_8AM_EDT = datetime(2026, 9, 28, 12, 0, tzinfo=timezone.utc)   # 08:00 local


@pytest.fixture()
def frozen(monkeypatch):
    def _freeze(at: datetime):
        monkeypatch.setattr(notify_mod, "_now", lambda: at)
    return _freeze


@pytest.fixture()
def recorder():
    calls = []

    def sender(uid, title, body, data):
        calls.append((uid, title, body, data))

    sender.calls = calls
    return sender


def test_prefs_roundtrip(mem_notify, mock_verify, auth_headers):
    client, _ = mem_notify
    r = client.put("/v1/users/me/notification-prefs",
                   json={"categories": {"harvest_alerts": False}, "quiet_hours": False},
                   headers=auth_headers)
    assert r.status_code == 200
    body = r.json()
    assert body["categories"] == {"harvest_alerts": False}
    assert body["quiet_hours"] is False
    r = client.get("/v1/users/me/notification-prefs", headers=auth_headers)
    assert r.json()["categories"] == {"harvest_alerts": False}


def test_prefs_require_auth(client):
    assert client.put("/v1/users/me/notification-prefs", json={}).status_code == 401


def test_send_delivers_with_working_sender(frozen, recorder):
    frozen(DAY_10AM_EDT)
    repo = MemoryNotificationRepo()
    out = send_notification("alice", "harvest_alerts", "Ripe!", "Figs are ripe",
                            repo=repo, sender=recorder, ref="tree-1")
    assert out == {"status": "sent", "reason": "delivered"}
    assert len(recorder.calls) == 1


def test_send_would_send_without_fcm_creds(frozen):
    frozen(DAY_10AM_EDT)
    repo = MemoryNotificationRepo()
    out = send_notification("alice", "harvest_alerts", "Ripe!", "Figs are ripe",
                            repo=repo, ref="tree-1")
    assert out["status"] == "would_send"
    assert out["reason"] == "fcm_unavailable"


def test_opted_out_category_skipped(frozen, recorder):
    frozen(DAY_10AM_EDT)
    repo = MemoryNotificationRepo()
    repo.set_prefs("alice", {"harvest_alerts": False}, True)
    out = send_notification("alice", "harvest_alerts", "Ripe!", "x", repo=repo, sender=recorder)
    assert out == {"status": "skipped", "reason": "opted_out"}
    assert recorder.calls == []


def test_quiet_hours_skipped(frozen, recorder):
    frozen(NIGHT_10PM_EDT)
    repo = MemoryNotificationRepo()
    out = send_notification("alice", "harvest_alerts", "Ripe!", "x", repo=repo, sender=recorder)
    assert out == {"status": "skipped", "reason": "quiet_hours"}
    assert recorder.calls == []


def test_quiet_hours_boundary(frozen, recorder):
    repo = MemoryNotificationRepo()
    frozen(NIGHT_7AM_EDT)  # 07:00 local -> quiet
    assert send_notification("a", "c", "t", "b", repo=repo, sender=recorder)["reason"] == "quiet_hours"
    frozen(EDGE_8AM_EDT)  # 08:00 local -> not quiet
    assert send_notification("a", "c", "t", "b", repo=repo, sender=recorder)["status"] == "sent"


def test_quiet_hours_can_be_disabled(frozen, recorder):
    frozen(NIGHT_10PM_EDT)
    repo = MemoryNotificationRepo()
    repo.set_prefs("alice", {}, False)
    out = send_notification("alice", "harvest_alerts", "Ripe!", "x", repo=repo, sender=recorder)
    assert out["status"] == "sent"


def test_duplicate_category_ref_skipped_within_24h(frozen, recorder):
    frozen(DAY_10AM_EDT)
    repo = MemoryNotificationRepo()
    kw = dict(repo=repo, sender=recorder, ref="tree-1")
    assert send_notification("alice", "harvest_alerts", "t", "b", **kw)["status"] == "sent"
    out = send_notification("alice", "harvest_alerts", "t", "b", **kw)
    assert out == {"status": "skipped", "reason": "duplicate"}
    # different ref is a new notification
    assert send_notification("alice", "harvest_alerts", "t", "b",
                             repo=repo, sender=recorder, ref="tree-2")["status"] == "sent"


def test_daily_cap_enforced(frozen, recorder, monkeypatch):
    frozen(DAY_10AM_EDT)
    monkeypatch.setenv("NOTIFY_DAILY_CAP", "2")
    repo = MemoryNotificationRepo()
    assert send_notification("a", "c", "t", "b", repo=repo, sender=recorder, ref="r1")["status"] == "sent"
    assert send_notification("a", "c", "t", "b", repo=repo, sender=recorder, ref="r2")["status"] == "sent"
    out = send_notification("a", "c", "t", "b", repo=repo, sender=recorder, ref="r3")
    assert out == {"status": "skipped", "reason": "rate_capped"}
    assert len(recorder.calls) == 2


def test_log_records_every_attempt(frozen, recorder):
    frozen(DAY_10AM_EDT)
    repo = MemoryNotificationRepo()
    send_notification("a", "c", "t", "b", repo=repo, sender=recorder, ref="r1")
    send_notification("a", "c", "t", "b", repo=repo, sender=recorder, ref="r1")  # duplicate
    outcomes = [e["outcome"] for e in repo._log]
    assert outcomes == ["sent", "skipped_duplicate"]
