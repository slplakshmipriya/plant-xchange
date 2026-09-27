"""API-030: want-list CRUD + match engine on listing go-live."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest

from app import notify as notify_mod
from app.wantlist import find_matches, variety_matches


@pytest.fixture()
def mem_all(client):
    """client + user/listing/want/notify memory repos."""
    from app import listings as listings_mod
    from app import users as users_mod
    from app import wantlist as wantlist_mod

    urepo = users_mod.MemoryUserRepo()
    lrepo = listings_mod.MemoryListingRepo()
    wrepo = wantlist_mod.MemoryWantRepo()
    nrepo = notify_mod.MemoryNotificationRepo()
    client.app.dependency_overrides[users_mod.get_user_repo] = lambda: urepo
    client.app.dependency_overrides[listings_mod.get_listing_repo] = lambda: lrepo
    client.app.dependency_overrides[wantlist_mod.get_want_repo] = lambda: wrepo
    client.app.dependency_overrides[notify_mod.get_notification_repo] = lambda: nrepo
    return client, urepo, lrepo, wrepo, nrepo


@pytest.fixture()
def frozen(monkeypatch):
    def _freeze(at: datetime):
        monkeypatch.setattr(notify_mod, "_now", lambda: at)
    return _freeze


def _noon():
    # 12:00 UTC = 08:00 EDT — outside quiet hours (21:00-08:00 local).
    return datetime(2026, 9, 28, 12, 0, tzinfo=timezone.utc)


def _live_payload(**kw):
    base = {
        "type": "seedling",
        "photos": ["https://example.com/a.jpg"],
        "variety": "Cherokee Purple tomato",
        "quantity": 6,
        "unit": "starts",
        "credit_cost": 2,
        "spray_disclosure": "none",
        "status": "live",
        "expires_at": (datetime.now(timezone.utc) + timedelta(days=3)).isoformat(),
    }
    base.update(kw)
    return base


# ------------------------------------------------ matching rule

@pytest.mark.parametrize("want,listing,expected", [
    ("tomato", "Cherokee Purple tomato", True),
    ("Cherokee Purple tomato", "tomato", True),   # either direction
    ("TOMATO", "cherokee purple tomato", True),   # case-insensitive
    ("basil", "tomato", False),
    ("", "tomato", False),
    ("tomato", None, False),
])
def test_variety_matches(want, listing, expected):
    assert variety_matches(want, listing) is expected


def test_find_matches_excludes_owner_and_wrong_type():
    listing = {"id": "l1", "owner_uid": "alice", "type": "seedling", "variety": "tomato"}
    entries = [
        {"user_uid": "alice", "variety": "tomato", "types": []},          # owner: skip
        {"user_uid": "bob", "variety": "tomato", "types": ["harvest"]},   # type mismatch
        {"user_uid": "carol", "variety": "tomato", "types": ["seedling"]},
        {"user_uid": "dave", "variety": "tomato", "types": []},           # any type
        {"user_uid": "erin", "variety": "basil", "types": []},            # no match
    ]
    got = {e["user_uid"] for e in find_matches(listing, entries)}
    assert got == {"carol", "dave"}


# ------------------------------------------------ CRUD

def test_want_crud_owner_scoped(mem_all, mock_verify, auth_headers):
    client, urepo, _, _, _ = mem_all
    urepo.upsert("alice", display_name="Alice")

    r = client.post("/v1/want-list", json={"variety": "  tomato ", "types": ["seedling"]},
                    headers=auth_headers)
    assert r.status_code == 201, r.text
    wid = r.json()["id"]
    assert r.json()["variety"] == "tomato"  # trimmed

    r = client.get("/v1/want-list", headers=auth_headers)
    assert [w["id"] for w in r.json()["items"]] == [wid]

    r = client.patch(f"/v1/want-list/{wid}", json={"variety": "basil"}, headers=auth_headers)
    assert r.json()["variety"] == "basil"

    r = client.delete(f"/v1/want-list/{wid}", headers=auth_headers)
    assert r.status_code == 204
    r = client.get("/v1/want-list", headers=auth_headers)
    assert r.json()["items"] == []


def test_want_rejects_bad_type(mem_all, mock_verify, auth_headers):
    client, urepo, _, _, _ = mem_all
    urepo.upsert("alice", display_name="Alice")
    r = client.post("/v1/want-list", json={"variety": "tomato", "types": ["nope"]},
                    headers=auth_headers)
    assert r.status_code == 400
    assert r.json()["code"] == "invalid_want_type"


# ------------------------------------------------ match engine

def test_match_push_on_create_live(mem_all, mock_verify, auth_headers, frozen):
    frozen(_noon())
    client, urepo, _, wrepo, nrepo = mem_all
    urepo.upsert("alice", display_name="Alice")
    urepo.upsert("bob", display_name="Bob")
    wrepo.create({"id": "w1", "user_uid": "bob", "variety": "tomato", "types": []})

    r = client.post("/v1/listings", json=_live_payload(), headers=auth_headers)
    assert r.status_code == 201, r.text
    listing_id = r.json()["id"]

    logged = [e for e in nrepo._log if e["category"] == "match"]
    assert len(logged) == 1
    assert logged[0]["user_uid"] == "bob"
    assert logged[0]["ref"] == listing_id
    assert logged[0]["outcome"] == "would_send"  # no FCM creds in test


def test_match_push_on_draft_to_live(mem_all, mock_verify, auth_headers, frozen):
    frozen(_noon())
    client, urepo, _, wrepo, nrepo = mem_all
    urepo.upsert("alice", display_name="Alice")
    urepo.upsert("bob", display_name="Bob")
    wrepo.create({"id": "w1", "user_uid": "bob", "variety": "tomato", "types": []})

    r = client.post("/v1/listings", json=_live_payload(status="draft"), headers=auth_headers)
    lid = r.json()["id"]
    assert not [e for e in nrepo._log if e["category"] == "match"]

    r = client.patch(f"/v1/listings/{lid}", json={"status": "live"}, headers=auth_headers)
    assert r.status_code == 200, r.text
    logged = [e for e in nrepo._log if e["category"] == "match"]
    assert len(logged) == 1 and logged[0]["user_uid"] == "bob"


def test_match_deduped_per_listing(mem_all, mock_verify, auth_headers, frozen):
    """At-most-one match push per (user, listing) per 24h — relisting the same
    payload twice does not double-notify."""
    frozen(_noon())
    client, urepo, _, wrepo, nrepo = mem_all
    urepo.upsert("alice", display_name="Alice")
    urepo.upsert("bob", display_name="Bob")
    wrepo.create({"id": "w1", "user_uid": "bob", "variety": "tomato", "types": []})

    for _ in range(2):
        r = client.post("/v1/listings", json=_live_payload(), headers=auth_headers)
        lid = r.json()["id"]
        # Simulate the same listing going live twice (e.g. retry): notify again
        # with the same ref must be deduped by the notify layer.
        from app.wantlist import notify_matches
        from app import listings as listings_mod
        row = client.app.dependency_overrides[listings_mod.get_listing_repo]().get(lid)
        notify_matches(row, wrepo, nrepo)

    bobs = [e for e in nrepo._log if e["user_uid"] == "bob" and e["category"] == "match"
            and e["outcome"] in ("sent", "would_send")]
    # 2 listings x 1 delivered each (the repeated notify_matches calls dedupe)
    assert len(bobs) == 2
    dupes = [e for e in nrepo._log if e["outcome"] == "skipped_duplicate"]
    assert len(dupes) == 2  # the repeats were caught by the 24h dedupe guard


def test_feed_boosts_want_match(mem_all, mock_verify, auth_headers):
    client, urepo, lrepo, wrepo, _ = mem_all
    urepo.upsert("alice", display_name="Alice")
    wrepo.create({"id": "w1", "user_uid": "alice", "variety": "tomato", "types": []})
    now = datetime.now(timezone.utc)
    exp = (now + timedelta(days=3)).isoformat()
    # Same expiry/age: only the boost should separate them.
    lrepo.create({"id": "plain", "owner_uid": "alice", "type": "seedling",
                  "photos": ["https://x/p.jpg"], "variety": "basil", "credit_cost": 1,
                  "spray_disclosure": "none", "status": "live", "expires_at": exp,
                  "created_at": now.isoformat()})
    lrepo.create({"id": "match", "owner_uid": "alice", "type": "seedling",
                  "photos": ["https://x/m.jpg"], "variety": "tomato", "credit_cost": 1,
                  "spray_disclosure": "none", "status": "live", "expires_at": exp,
                  "created_at": now.isoformat()})
    r = client.get("/v1/feed", headers=auth_headers)
    assert [i["id"] for i in r.json()["items"]] == ["match", "plain"]
