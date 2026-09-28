"""R2 notifications: prefs API (new contract), pipeline hooks, FCM seam.

Hooks are unit-tested DIRECTLY here (no edits to listings.py / feed.py /
other tracks' files); the coordinator wires the call sites after merge.
No test in this file may touch the network: the FCM wrapper is monkeypatched
everywhere except the explicit degrade-to-logging test.
"""
from __future__ import annotations

from datetime import datetime, timezone

import pytest

import app.notify as notify_mod
from app.notify import (
    MemoryNotificationRepo,
    dispatch_event,
    on_credit_expiry_warning,
    on_listing_expiry_nudge,
    on_new_listing,
    on_ripe_window_entry,
    send_push,
)
from app.wantlist import MemoryWantRepo

# 2026-09-27/28: America/New_York is EDT (UTC-4).
DAY_10AM_EDT = datetime(2026, 9, 28, 14, 0, tzinfo=timezone.utc)   # 10:00 local
EVE_6PM_EDT = datetime(2026, 9, 28, 22, 0, tzinfo=timezone.utc)    # 18:00 local


@pytest.fixture()
def frozen(monkeypatch):
    def _freeze(at: datetime):
        monkeypatch.setattr(notify_mod, "_now", lambda: at)
    return _freeze


@pytest.fixture()
def push_spy(monkeypatch):
    """Monkeypatch the FCM wrapper: record calls, report delivered."""
    calls = []

    def fake(token, title, body, data=None):
        calls.append({"token": token, "title": title, "body": body, "data": data or {}})
        return {"status": "sent", "reason": "delivered"}

    monkeypatch.setattr(notify_mod, "send_push", fake)
    return calls


def _want_repo() -> MemoryWantRepo:
    wrepo = MemoryWantRepo()
    wrepo.create({"id": "w1", "user_uid": "bob", "variety": "Fig", "types": []})
    return wrepo


def _fig_tree() -> dict:
    return {"id": "t1", "variety": "Brown Turkey Fig", "owner_uid": "alice", "type": "tree"}


# --- prefs API: new contract -------------------------------------------------

def test_prefs_put_new_contract_roundtrip(mem_notify, mock_verify, auth_headers):
    client, _ = mem_notify
    body = {
        "categories": {
            "harvestAlerts": False,
            "wantMatches": True,
            "expiryNudges": True,
            "creditWarnings": True,
            "bookingReminders": False,
        },
        "quietHours": {"start": "22:00", "end": "06:30"},
    }
    r = client.put("/v1/users/me/notification-prefs", json=body, headers=auth_headers)
    assert r.status_code == 200
    out = r.json()
    assert out["user_uid"] == "alice"
    assert out["categories"] == body["categories"]
    assert out["quiet_hours"] is True
    assert out["quietHours"] == {"start": "22:00", "end": "06:30"}
    r = client.get("/v1/users/me/notification-prefs", headers=auth_headers)
    assert r.status_code == 200
    assert r.json()["quietHours"] == {"start": "22:00", "end": "06:30"}
    assert r.json()["categories"]["harvestAlerts"] is False


def test_prefs_get_defaults_when_unset(mem_notify, mock_verify, auth_headers):
    client, _ = mem_notify
    out = client.get("/v1/users/me/notification-prefs", headers=auth_headers).json()
    assert out["categories"] == {
        "harvestAlerts": True,
        "wantMatches": True,
        "expiryNudges": True,
        "creditWarnings": True,
        "bookingReminders": True,
    }
    assert out["quiet_hours"] is True
    assert out["quietHours"] == {"start": "21:00", "end": "08:00"}


@pytest.mark.parametrize(
    "bad",
    [
        {"start": "25:00", "end": "08:00"},
        {"start": "9pm", "end": "08:00"},
        {"start": "12:60", "end": "08:00"},
        {"start": "08:00", "end": "8:00"},
    ],
)
def test_prefs_rejects_bad_hhmm(mem_notify, mock_verify, auth_headers, bad):
    client, _ = mem_notify
    r = client.put("/v1/users/me/notification-prefs",
                   json={"quietHours": bad}, headers=auth_headers)
    assert r.status_code == 422


def test_prefs_explicit_null_disables_quiet_hours(mem_notify, mock_verify, auth_headers):
    client, _ = mem_notify
    r = client.put("/v1/users/me/notification-prefs",
                   json={"quietHours": None}, headers=auth_headers)
    assert r.status_code == 200
    assert r.json()["quiet_hours"] is False
    assert r.json()["quietHours"] is None


def test_prefs_omitted_quiet_hours_keeps_existing(mem_notify, mock_verify, auth_headers):
    client, _ = mem_notify
    client.put("/v1/users/me/notification-prefs",
               json={"quietHours": {"start": "22:00", "end": "06:30"}},
               headers=auth_headers)
    r = client.put("/v1/users/me/notification-prefs",
                   json={"categories": {"wantMatches": False}}, headers=auth_headers)
    assert r.json()["quietHours"] == {"start": "22:00", "end": "06:30"}


# --- pipeline hooks ----------------------------------------------------------

def test_ripe_window_notifies_wanters_with_payload(frozen, push_spy):
    frozen(DAY_10AM_EDT)
    nrepo = MemoryNotificationRepo()
    nrepo.register_token("bob", "tok-bob")
    out = on_ripe_window_entry(_fig_tree(), notify_repo=nrepo, want_repo=_want_repo())
    assert out["event_type"] == "ripe_window_entry"
    assert out["category"] == "harvestAlerts"
    assert out["recipients"] == 1
    assert out["delivered"] == 1
    assert len(push_spy) == 1
    call = push_spy[0]
    assert call["token"] == "tok-bob"
    assert call["data"]["tree_id"] == "t1"
    assert "Brown Turkey Fig" in call["body"]
    assert nrepo._log[0]["category"] == "harvestAlerts"
    assert nrepo._log[0]["outcome"] == "sent"


def test_quiet_hours_custom_window_suppresses_hook(frozen, push_spy):
    nrepo = MemoryNotificationRepo()
    wrepo = _want_repo()
    nrepo.set_prefs("bob", {}, True, "09:00", "17:00")  # quiet during the "day"

    frozen(DAY_10AM_EDT)  # 10:00 local -> inside window
    out = on_ripe_window_entry(_fig_tree(), notify_repo=nrepo, want_repo=wrepo)
    assert out["recipients"] == 1
    assert out["delivered"] == 0
    assert out["by_reason"] == {"quiet_hours": 1}
    assert push_spy == []
    assert nrepo._log[0]["outcome"] == "skipped_quiet_hours"

    frozen(EVE_6PM_EDT)  # 18:00 local -> outside window
    out = on_ripe_window_entry(_fig_tree(), notify_repo=nrepo, want_repo=wrepo)
    assert out["delivered"] == 1  # quiet-hour skips don't consume the dedupe window


def test_category_opt_out_suppresses_hook(frozen, push_spy):
    frozen(DAY_10AM_EDT)
    tree, wrepo = _fig_tree(), _want_repo()

    nrepo = MemoryNotificationRepo()
    nrepo.set_prefs("bob", {"harvestAlerts": False}, True)
    out = on_ripe_window_entry(tree, notify_repo=nrepo, want_repo=wrepo)
    assert out["delivered"] == 0
    assert out["by_reason"] == {"opted_out": 1}
    assert push_spy == []

    # legacy snake_case spelling suppresses the same category group
    nrepo2 = MemoryNotificationRepo()
    nrepo2.set_prefs("bob", {"harvest_alerts": False}, True)
    out = on_ripe_window_entry(tree, notify_repo=nrepo2, want_repo=wrepo)
    assert out["by_reason"] == {"opted_out": 1}
    assert push_spy == []


def test_new_listing_matches_wants_excludes_owner(frozen):
    frozen(DAY_10AM_EDT)
    nrepo = MemoryNotificationRepo()
    wrepo = MemoryWantRepo()
    wrepo.create({"id": "w1", "user_uid": "alice", "variety": "Basil", "types": []})  # owner
    wrepo.create({"id": "w2", "user_uid": "bob", "variety": "basil", "types": []})
    wrepo.create({"id": "w3", "user_uid": "carol", "variety": "Tomato", "types": []})
    listing = {"id": "l1", "owner_uid": "alice", "variety": "Genovese Basil", "type": "seedling"}
    out = on_new_listing(listing, notify_repo=nrepo, want_repo=wrepo)
    assert out["category"] == "wantMatches"
    assert out["recipients"] == 1
    assert out["results"][0]["uid"] == "bob"
    # no token registered and no FCM creds -> honest would_send
    assert out["results"][0]["status"] == "would_send"


def test_expiry_nudge_marks_and_dedupe(frozen, push_spy):
    frozen(DAY_10AM_EDT)
    nrepo = MemoryNotificationRepo()
    nrepo.register_token("alice", "tok-alice")
    listing = {"id": "l9", "owner_uid": "alice", "variety": "Mint", "type": "seedling"}

    out48 = on_listing_expiry_nudge(listing, 48, notify_repo=nrepo)
    out12 = on_listing_expiry_nudge(listing, 12, notify_repo=nrepo)
    assert out48["category"] == "expiryNudges"
    assert out48["results"][0]["uid"] == "alice"
    assert out48["delivered"] == 1 and out12["delivered"] == 1
    assert "48h" in push_spy[0]["body"] and "12h" in push_spy[1]["body"]

    out48b = on_listing_expiry_nudge(listing, 48, notify_repo=nrepo)
    assert out48b["delivered"] == 0
    assert out48b["by_reason"] == {"duplicate": 1}

    refs = [e["ref"] for e in nrepo._log]
    assert refs == ["listing:l9:expiry:48h", "listing:l9:expiry:12h", "listing:l9:expiry:48h"]
    assert [e["outcome"] for e in nrepo._log] == ["sent", "sent", "skipped_duplicate"]
    assert {e["category"] for e in nrepo._log} == {"expiryNudges"}


def test_credit_warning_payload(frozen, push_spy):
    frozen(DAY_10AM_EDT)
    nrepo = MemoryNotificationRepo()
    nrepo.register_token("alice", "tok-a")
    out = on_credit_expiry_warning("alice", 7, 25, notify_repo=nrepo)
    assert out["event_type"] == "credit_expiry_warning"
    assert out["category"] == "creditWarnings"
    assert out["delivered"] == 1
    assert "25" in push_spy[0]["body"] and "7" in push_spy[0]["body"]
    assert nrepo._log[0]["ref"] == "credits:alice:expiry:7d"


def test_dispatch_unknown_event_raises():
    with pytest.raises(ValueError, match="unknown event_type"):
        dispatch_event("bogus", {}, notify_repo=MemoryNotificationRepo())


# --- FCM seam ----------------------------------------------------------------

def test_send_push_degrades_without_firebase():
    # No firebase_admin init in this env: must degrade to logging, no network.
    out = send_push("tok123", "Hello", "world", {"a": "b"})
    assert out == {"status": "would_send", "reason": "fcm_uninitialized"}


def test_fcm_token_registration(mem_notify, mock_verify, auth_headers):
    client, repo = mem_notify
    r = client.post("/v1/users/me/fcm-token",
                    json={"token": "tok123", "platform": "android"},
                    headers=auth_headers)
    assert r.status_code == 201
    # L4d: the secret token is never echoed — success flag only.
    assert r.json() == {"user_uid": "alice", "registered": True, "platform": "android"}
    assert "token" not in r.json()
    assert repo.get_tokens("alice") == ["tok123"]
    # idempotent re-register
    r = client.post("/v1/users/me/fcm-token", json={"token": "tok123"},
                    headers=auth_headers)
    assert r.status_code == 201
    assert r.json()["registered"] is True
    assert repo.get_tokens("alice") == ["tok123"]


def test_fcm_token_requires_auth(client):
    assert client.post("/v1/users/me/fcm-token", json={"token": "x"}).status_code == 401


def test_fcm_token_rejects_blank(mem_notify, mock_verify, auth_headers):
    client, _ = mem_notify
    r = client.post("/v1/users/me/fcm-token", json={"token": "   "}, headers=auth_headers)
    assert r.status_code == 422


def test_registered_token_gets_token_targeted_send(frozen, push_spy,
                                                   mem_notify, mock_verify, auth_headers):
    client, nrepo = mem_notify
    client.post("/v1/users/me/fcm-token", json={"token": "tok-alice"},
                headers=auth_headers)
    frozen(DAY_10AM_EDT)
    wrepo = MemoryWantRepo()
    wrepo.create({"id": "w1", "user_uid": "alice", "variety": "Fig", "types": []})
    tree = {"id": "t1", "variety": "Fig", "owner_uid": "carol", "type": "tree"}
    out = on_ripe_window_entry(tree, notify_repo=nrepo, want_repo=wrepo)
    assert out["delivered"] == 1
    assert [c["token"] for c in push_spy] == ["tok-alice"]
    assert out["results"][0]["via"] == "1_token(s)"


# --- H13: topic fallback deprecation ------------------------------------------

def _tree_payload(uids):
    return {
        "tree": {"id": "t1", "variety": "Fig", "owner_uid": "carol", "type": "tree"},
        "user_uids": uids,
    }


def test_topic_fallback_still_default_when_no_tokens(frozen, mem_notify, monkeypatch):
    # H13: default behavior preserved — no tokens -> legacy topic path.
    from app.notify import _deliver_to_recipient

    monkeypatch.delenv("FCM_TOPIC_FALLBACK_ENABLED", raising=False)
    frozen(DAY_10AM_EDT)
    _, repo = mem_notify
    out = _deliver_to_recipient(
        "alice", "harvestAlerts", "T", "B", {}, "ref-1", repo, DAY_10AM_EDT
    )
    assert out["via"] == "topic_fallback"
    assert out["status"] == "would_send"  # no FCM creds in CI


def test_topic_fallback_disabled_skips(frozen, mem_notify, monkeypatch):
    # H13: FCM_TOPIC_FALLBACK_ENABLED=0 kills the topic path without
    # breaking token sends.
    from app.notify import _deliver_to_recipient

    monkeypatch.setenv("FCM_TOPIC_FALLBACK_ENABLED", "0")
    frozen(DAY_10AM_EDT)
    _, repo = mem_notify
    out = _deliver_to_recipient(
        "alice", "harvestAlerts", "T", "B", {}, "ref-1", repo, DAY_10AM_EDT
    )
    assert out == {
        "uid": "alice",
        "status": "skipped",
        "reason": "topic_fallback_disabled",
    }


def test_sensitive_send_refuses_topic_path(frozen, mem_notify, monkeypatch):
    # H13 code-level guard: sensitive content never rides the ACL-less topic.
    from app.notify import send_notification

    monkeypatch.delenv("FCM_TOPIC_FALLBACK_ENABLED", raising=False)
    frozen(DAY_10AM_EDT)
    _, repo = mem_notify

    out = dispatch_event(
        "ripe_window_entry", _tree_payload(["alice"]),
        notify_repo=repo, now=DAY_10AM_EDT, sensitive=True,
    )
    assert out["results"][0]["reason"] == "sensitive_not_routed_via_topic"
    assert out["delivered"] == 0

    out = send_notification(
        "alice", "harvestAlerts", "T", "B", repo=repo, ref="s1", sensitive=True
    )
    assert out == {"status": "skipped", "reason": "sensitive_not_routed_via_topic"}


def test_sensitive_send_allowed_with_explicit_token_sender(frozen, mem_notify):
    # A caller-owned sender (e.g. token-targeted) is not blocked by the guard.
    frozen(DAY_10AM_EDT)
    _, repo = mem_notify
    calls = []

    def token_sender(uid, title, body, data):
        calls.append(uid)

    from app.notify import default_fcm_sender, send_notification

    out = send_notification(
        "alice", "harvestAlerts", "T", "B", repo=repo, ref="s2",
        sender=token_sender, sensitive=True,
    )
    assert out["status"] == "sent"
    assert calls == ["alice"]
    assert token_sender is not default_fcm_sender


# --- M21: deregistration + pruning --------------------------------------------

def test_fcm_token_deregistration(mem_notify, mock_verify, auth_headers):
    client, repo = mem_notify
    client.post("/v1/users/me/fcm-token", json={"token": "tok123"},
                headers=auth_headers)
    r = client.request("DELETE", "/v1/users/me/fcm-token",
                       json={"token": "tok123"}, headers=auth_headers)
    assert r.status_code == 200
    assert r.json() == {"user_uid": "alice", "removed": True}
    assert repo.get_tokens("alice") == []
    # Idempotent: unknown token -> removed false, still 200 (never 404).
    r = client.request("DELETE", "/v1/users/me/fcm-token",
                       json={"token": "tok123"}, headers=auth_headers)
    assert r.status_code == 200
    assert r.json()["removed"] is False


def test_fcm_token_delete_requires_auth(client):
    assert client.request(
        "DELETE", "/v1/users/me/fcm-token", json={"token": "x"}
    ).status_code == 401


def test_prune_dead_token_on_unregistered(frozen, mem_notify, monkeypatch):
    # M21: FCM unregistered-token errors prune the row.
    from app.notify import _deliver_to_recipient

    frozen(DAY_10AM_EDT)
    _, repo = mem_notify
    repo.register_token("alice", "dead-tok")
    repo.register_token("alice", "live-tok")

    def mixed_push(token, title, body, data=None):
        if token == "dead-tok":
            return {"status": "failed", "reason": "fcm_error", "prune_token": True}
        return {"status": "sent", "reason": "delivered"}

    monkeypatch.setattr(notify_mod, "send_push", mixed_push)
    out = _deliver_to_recipient(
        "alice", "harvestAlerts", "T", "B", {}, "ref-1", repo, DAY_10AM_EDT
    )
    assert out["status"] == "sent"
    assert repo.get_tokens("alice") == ["live-tok"]  # dead one pruned


def test_send_push_flags_prune_on_unregistered(monkeypatch):
    # send_push surfaces prune_token=True for dead-token errors, False otherwise.
    import sys
    import types

    class UnregisteredError(Exception):
        pass

    def boom(exc):
        def _send(msg):
            raise exc
        return _send

    fake_messaging = types.SimpleNamespace(
        Message=lambda **kw: kw,
        Notification=lambda **kw: kw,
        UnregisteredError=UnregisteredError,
        SenderIdMismatchError=type("SenderIdMismatchError", (Exception,), {}),
        send=boom(UnregisteredError("registration-token-not-registered")),
    )
    fake_admin = types.ModuleType("firebase_admin")
    fake_admin.get_app = lambda: object()
    fake_admin.messaging = fake_messaging
    monkeypatch.setitem(sys.modules, "firebase_admin", fake_admin)

    out = send_push("tok123", "T", "B")
    assert out["status"] == "failed"
    assert out["prune_token"] is True

    fake_messaging.send = boom(RuntimeError("internal"))
    out = send_push("tok123", "T", "B")
    assert out["status"] == "failed"
    assert out["prune_token"] is False
