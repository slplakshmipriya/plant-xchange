"""CHAT track: thread attachments (photo messages) + participant_uids."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest


@pytest.fixture()
def chat_client(client, monkeypatch):
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
    return client


ALICE = {"Authorization": "Bearer good-token"}
BOB = {"Authorization": "Bearer bob-token"}
MALLORY = {"Authorization": "Bearer mallory-token"}


def _listing_id(client):
    """Alice owns a listing; bob has a profile."""
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


def _thread_id(client, lid):
    r = client.post("/v1/threads", json={"listing_id": lid}, headers=BOB)
    assert r.status_code == 200, r.text
    return r.json()["id"]


PHOTO = "https://cdn.example.com/swaps/tomato-1.jpg"


def test_attachment_creates_photo_message_visible_in_thread(chat_client):
    tid = _thread_id(chat_client, _listing_id(chat_client))

    r = chat_client.post(f"/v1/threads/{tid}/attachments",
                         json={"photoUrl": PHOTO}, headers=BOB)
    assert r.status_code == 201, r.text
    msg = r.json()
    assert msg["kind"] == "photo"
    assert msg["photo_url"] == PHOTO
    assert msg["sender_uid"] == "bob"
    assert msg["thread_id"] == tid

    # The photo message shows up in the thread's message list.
    msgs = chat_client.get(f"/v1/threads/{tid}/messages", headers=BOB).json()["messages"]
    assert [m["id"] for m in msgs] == [msg["id"]]
    assert msgs[0]["kind"] == "photo"
    assert msgs[0]["photo_url"] == PHOTO


def test_attachment_forbidden_for_non_participant(chat_client):
    tid = _thread_id(chat_client, _listing_id(chat_client))
    r = chat_client.post(f"/v1/threads/{tid}/attachments",
                         json={"photoUrl": PHOTO}, headers=MALLORY)
    assert r.status_code == 403
    assert r.json()["code"] == "not_a_participant"


def test_attachment_thread_not_found(chat_client):
    _listing_id(chat_client)
    r = chat_client.post("/v1/threads/00000000-0000-0000-0000-000000000000/attachments",
                         json={"photoUrl": PHOTO}, headers=BOB)
    assert r.status_code == 404
    assert r.json()["code"] == "thread_not_found"


def test_attachment_rejects_bad_photo_url(chat_client):
    tid = _thread_id(chat_client, _listing_id(chat_client))
    for bad in ("ftp://example.com/p.jpg", "not-a-url", "javascript:alert(1)"):
        r = chat_client.post(f"/v1/threads/{tid}/attachments",
                             json={"photoUrl": bad}, headers=BOB)
        assert r.status_code == 422, (bad, r.text)
        assert r.json()["code"] == "invalid_photo_url"
    # Empty string fails pydantic validation.
    r = chat_client.post(f"/v1/threads/{tid}/attachments",
                         json={"photoUrl": ""}, headers=BOB)
    assert r.status_code == 422


def test_thread_responses_include_participant_uids(chat_client):
    lid = _listing_id(chat_client)
    tid = _thread_id(chat_client, lid)

    # Detail (open is idempotent) carries both participants.
    opened = chat_client.post("/v1/threads", json={"listing_id": lid}, headers=BOB).json()
    assert opened["id"] == tid
    assert opened["participant_uids"] == ["alice", "bob"]

    # List view carries them too, for both sides of the conversation.
    for headers in (BOB, ALICE):
        threads = chat_client.get("/v1/threads", headers=headers).json()["threads"]
        mine = next(t for t in threads if t["id"] == tid)
        assert mine["participant_uids"] == ["alice", "bob"]

    # Existing fields are untouched.
    assert set(opened) == {"id", "listing_id", "created_by", "created_at",
                           "message_count", "participant_uids"}
    assert opened["created_by"] == "bob"


def test_text_message_kind_defaults_to_text(chat_client):
    tid = _thread_id(chat_client, _listing_id(chat_client))
    r = chat_client.post(f"/v1/threads/{tid}/messages",
                         json={"body": "still available?"}, headers=BOB)
    assert r.status_code == 201, r.text
    assert r.json()["kind"] == "text"
    assert r.json()["photo_url"] is None
