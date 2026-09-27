"""API-080: listing-scoped threads, messages, cursor pagination, privacy."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest


@pytest.fixture()
def mem_msg(client, monkeypatch):
    from app import listings as listings_mod
    from app import msg as msg_mod
    from app import notify as notify_mod
    from app import users as users_mod
    from app import wantlist as wantlist_mod
    from conftest import wire_credit_repo
    import app.auth as auth_mod

    urepo = users_mod.MemoryUserRepo()
    lrepo = listings_mod.MemoryListingRepo()
    mrepo = msg_mod.MemoryMessageRepo()
    wrepo = wantlist_mod.MemoryWantRepo()
    nrepo = notify_mod.MemoryNotificationRepo()
    client.app.dependency_overrides[users_mod.get_user_repo] = lambda: urepo
    client.app.dependency_overrides[listings_mod.get_listing_repo] = lambda: lrepo
    client.app.dependency_overrides[msg_mod.get_message_repo] = lambda: mrepo
    client.app.dependency_overrides[wantlist_mod.get_want_repo] = lambda: wrepo
    client.app.dependency_overrides[notify_mod.get_notification_repo] = lambda: nrepo
    wire_credit_repo(client)

    def fake(token: str) -> dict:
        if token == "good-token":
            return {"uid": "alice", "phone_number": "+15551234567"}
        if token == "bob-token":
            return {"uid": "bob"}
        if token == "mallory-token":
            return {"uid": "mallory"}
        raise ValueError("bad token")

    monkeypatch.setattr(auth_mod, "verify_id_token", fake)
    return client, urepo, lrepo, mrepo


ALICE = {"Authorization": "Bearer good-token"}
BOB = {"Authorization": "Bearer bob-token"}
MALLORY = {"Authorization": "Bearer mallory-token"}


def _setup(client):
    """Alice owns a listing; bob has a profile. Returns the listing id."""
    for headers, name in ((ALICE, "Alice"), (BOB, "Bob")):
        r = client.post("/v1/users", json={"display_name": name}, headers=headers)
        assert r.status_code == 200, r.text
    r = client.post("/v1/listings", json={
        "type": "seedling",
        "photos": ["https://example.com/t.jpg"],
        "variety": "Basil",
        "quantity": 6, "unit": "starts",
        "credit_cost": 1, "spray_disclosure": "unsprayed",
        "status": "live",
        "geo_lat": 33.4152, "geo_lon": -111.8315,
        "expires_at": (datetime.now(timezone.utc) + timedelta(days=30)).isoformat(),
    }, headers=ALICE)
    assert r.status_code == 201, r.text
    return r.json()["id"]


def test_thread_open_is_idempotent_and_scoped(mem_msg):
    client, _, _, _ = mem_msg
    lid = _setup(client)

    r = client.post("/v1/threads", json={"listing_id": lid}, headers=BOB)
    assert r.status_code == 200, r.text
    tid = r.json()["id"]
    assert r.json()["listing_id"] == lid
    assert r.json()["created_by"] == "bob"

    # Opening again returns the same thread, not a duplicate.
    r = client.post("/v1/threads", json={"listing_id": lid}, headers=BOB)
    assert r.json()["id"] == tid

    # Unknown listing -> 404.
    r = client.post("/v1/threads",
                    json={"listing_id": "00000000-0000-0000-0000-000000000000"},
                    headers=BOB)
    assert r.status_code == 404


def test_send_and_paginate_messages(mem_msg):
    client, _, _, _ = mem_msg
    lid = _setup(client)
    tid = client.post("/v1/threads", json={"listing_id": lid}, headers=BOB).json()["id"]

    for i in range(5):
        r = client.post(f"/v1/threads/{tid}/messages",
                        json={"body": f"hello {i}"}, headers=BOB)
        assert r.status_code == 201, r.text
    r = client.post(f"/v1/threads/{tid}/messages",
                    json={"body": "is it still available?"}, headers=ALICE)
    assert r.status_code == 201, r.text

    page1 = client.get(f"/v1/threads/{tid}/messages",
                       params={"limit": 4}, headers=BOB).json()
    assert [m["body"] for m in page1["messages"]] == [f"hello {i}" for i in range(4)]
    assert page1["next_cursor"]

    page2 = client.get(f"/v1/threads/{tid}/messages",
                       params={"limit": 4, "cursor": page1["next_cursor"]},
                       headers=ALICE).json()
    assert [m["body"] for m in page2["messages"]] == ["hello 4", "is it still available?"]
    assert page2["next_cursor"] is None

    # Bad cursor -> 400.
    r = client.get(f"/v1/threads/{tid}/messages",
                   params={"cursor": "!!!not-base64!!!"}, headers=BOB)
    assert r.status_code == 400

    # Empty body rejected.
    r = client.post(f"/v1/threads/{tid}/messages", json={"body": ""}, headers=BOB)
    assert r.status_code == 422


def test_messaging_is_private_and_geo_free(mem_msg):
    client, _, _, _ = mem_msg
    lid = _setup(client)
    tid = client.post("/v1/threads", json={"listing_id": lid}, headers=BOB).json()["id"]
    client.post(f"/v1/threads/{tid}/messages",
                json={"body": "interested!"}, headers=BOB)

    # Mallory is neither the opener nor the listing owner.
    r = client.get(f"/v1/threads/{tid}/messages", headers=MALLORY)
    assert r.status_code == 403
    assert r.json()["code"] == "not_a_participant"
    r = client.post(f"/v1/threads/{tid}/messages",
                    json={"body": "sneaky"}, headers=MALLORY)
    assert r.status_code == 403
    assert client.get("/v1/threads", headers=MALLORY).json()["threads"] == []

    # Owner sees the thread on their listing.
    threads = client.get("/v1/threads", headers=ALICE).json()["threads"]
    assert [t["id"] for t in threads] == [tid]
    assert threads[0]["message_count"] == 1

    # No geo anywhere in thread/message payloads (exact geo stays hidden).
    blob = str(client.get("/v1/threads", headers=BOB).json())
    blob += str(client.get(f"/v1/threads/{tid}/messages", headers=BOB).json())
    for leak in ("33.4152", "-111.8315", "geo_lat", "geo_lon"):
        assert leak not in blob
