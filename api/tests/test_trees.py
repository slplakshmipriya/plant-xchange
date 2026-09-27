"""API-050: tree listings (visit rules) + ripe alerts."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest

from app import notify as notify_mod


@pytest.fixture()
def mem_all(client):
    from app import listings as listings_mod
    from app import users as users_mod
    from app import wantlist as wantlist_mod
    from conftest import wire_credit_repo

    urepo = users_mod.MemoryUserRepo()
    lrepo = listings_mod.MemoryListingRepo()
    wrepo = wantlist_mod.MemoryWantRepo()
    nrepo = notify_mod.MemoryNotificationRepo()
    client.app.dependency_overrides[users_mod.get_user_repo] = lambda: urepo
    client.app.dependency_overrides[listings_mod.get_listing_repo] = lambda: lrepo
    client.app.dependency_overrides[wantlist_mod.get_want_repo] = lambda: wrepo
    client.app.dependency_overrides[notify_mod.get_notification_repo] = lambda: nrepo
    wire_credit_repo(client)
    return client, urepo, lrepo, wrepo, nrepo


@pytest.fixture()
def frozen(monkeypatch):
    def _freeze(at: datetime):
        monkeypatch.setattr(notify_mod, "_now", lambda: at)
    return _freeze


def _tree_payload(**kw):
    base = {
        "type": "tree",
        "photos": ["https://example.com/lemon.jpg"],
        "variety": "Meyer lemon",
        "quantity": 60,
        "unit": "lbs",
        "credit_cost": 1,
        "spray_disclosure": "unsprayed",
        "visit_rules": "Daylight only, use side gate.",
        "status": "live",
        "expires_at": (datetime.now(timezone.utc) + timedelta(days=30)).isoformat(),
    }
    base.update(kw)
    return base


def test_tree_carries_visit_rules(mem_all, mock_verify, auth_headers):
    client, urepo, _, _, _ = mem_all
    urepo.upsert("alice", display_name="Alice")
    r = client.post("/v1/listings", json=_tree_payload(), headers=auth_headers)
    assert r.status_code == 201, r.text
    assert r.json()["visit_rules"] == "Daylight only, use side gate."

    lid = r.json()["id"]
    r = client.patch(f"/v1/listings/{lid}", json={"visit_rules": "  Weekends only. "},
                     headers=auth_headers)
    assert r.json()["visit_rules"] == "Weekends only."


def test_ripe_alert_notifies_want_matches(mem_all, mock_verify, auth_headers, frozen, monkeypatch):
    frozen(datetime(2026, 9, 28, 12, 0, tzinfo=timezone.utc))
    import app.listings as listings_mod
    monkeypatch.setattr(listings_mod, "utcnow",
                        lambda: datetime(2026, 9, 28, 12, 0, tzinfo=timezone.utc))
    client, urepo, _, wrepo, nrepo = mem_all
    urepo.upsert("alice", display_name="Alice")
    urepo.upsert("bob", display_name="Bob")
    wrepo.create({"id": "w1", "user_uid": "bob", "variety": "lemon", "types": []})

    r = client.post("/v1/listings", json=_tree_payload(), headers=auth_headers)
    lid = r.json()["id"]

    r = client.post(f"/v1/trees/{lid}/ripe-alert", headers=auth_headers)
    assert r.status_code == 200, r.text
    assert r.json()["notified"] == 1

    alerts = [e for e in nrepo._log if e["category"] == "ripe_alert"]
    assert len(alerts) == 1
    assert alerts[0]["user_uid"] == "bob"
    assert alerts[0]["ref"] == f"{lid}:2026-09-28"

    # Same-day re-trigger is deduped by the notify layer.
    r = client.post(f"/v1/trees/{lid}/ripe-alert", headers=auth_headers)
    assert r.json()["notified"] == 0
    alerts = [e for e in nrepo._log if e["category"] == "ripe_alert"
              and e["outcome"] in ("sent", "would_send")]
    assert len(alerts) == 1


def test_ripe_alert_owner_only_and_tree_only(mem_all, mock_verify, auth_headers):
    import app.auth as auth_mod

    client, urepo, _, _, _ = mem_all
    urepo.upsert("alice", display_name="Alice")
    r = client.post("/v1/listings", json=_tree_payload(), headers=auth_headers)
    lid = r.json()["id"]

    orig = auth_mod.verify_id_token
    auth_mod.verify_id_token = lambda t: {"uid": "mallory"} if t == "good-token" else orig(t)
    try:
        r = client.post(f"/v1/trees/{lid}/ripe-alert", headers=auth_headers)
        assert r.status_code == 403
    finally:
        auth_mod.verify_id_token = orig

    # A seedling listing is not a tree.
    r = client.post("/v1/listings",
                    json={**_tree_payload(), "type": "seedling", "variety": "tomato"},
                    headers=auth_headers)
    sid = r.json()["id"]
    r = client.post(f"/v1/trees/{sid}/ripe-alert", headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "not_tree_listing"


def test_ripe_alert_unknown_listing_is_404(mem_all, mock_verify, auth_headers):
    client, *_ = mem_all
    r = client.post("/v1/trees/nope/ripe-alert", headers=auth_headers)
    assert r.status_code == 404
