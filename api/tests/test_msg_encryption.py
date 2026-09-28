"""At-rest encryption for chat message bodies + the support dashboard.

- bodies are Fernet ciphertext at rest (both repos), plaintext on the wire
- GET /v1/support/threads/{id}/messages is support-gated (403 otherwise)
  and writes one moderation_views audit row per message viewed
"""
from __future__ import annotations

import pytest


@pytest.fixture()
def mem_all(client, monkeypatch):
    from app import listings as listings_mod
    from app import moderation as moderation_mod
    from app import msg as msg_mod
    from app import notify as notify_mod
    from app import users as users_mod
    from app import wantlist as wantlist_mod
    from conftest import wire_credit_repo
    import app.auth as auth_mod

    urepo = users_mod.MemoryUserRepo()
    lrepo = listings_mod.MemoryListingRepo()
    mrepo = msg_mod.MemoryMessageRepo()
    vrepo = moderation_mod.MemoryModerationViewRepo()
    wrepo = wantlist_mod.MemoryWantRepo()
    nrepo = notify_mod.MemoryNotificationRepo()
    client.app.dependency_overrides[users_mod.get_user_repo] = lambda: urepo
    client.app.dependency_overrides[listings_mod.get_listing_repo] = lambda: lrepo
    client.app.dependency_overrides[msg_mod.get_message_repo] = lambda: mrepo
    client.app.dependency_overrides[moderation_mod.get_moderation_view_repo] = lambda: vrepo
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
        if token == "support-token":
            return {"uid": "support1"}
        raise ValueError("bad token")

    monkeypatch.setattr(auth_mod, "verify_id_token", fake)
    return client, urepo, lrepo, mrepo, vrepo


ALICE = {"Authorization": "Bearer good-token"}
BOB = {"Authorization": "Bearer bob-token"}
MALLORY = {"Authorization": "Bearer mallory-token"}
SUPPORT = {"Authorization": "Bearer support-token"}


def _setup_thread(client):
    """Alice owns a listing; bob opens a thread and sends two messages."""
    for headers, name in ((ALICE, "Alice"), (BOB, "Bob")):
        r = client.post("/v1/users", json={"display_name": name}, headers=headers)
        assert r.status_code == 200, r.text
    r = client.post("/v1/listings", json={
        "type": "seedling", "photos": ["https://example.com/t.jpg"],
        "variety": "Basil", "quantity": 6, "unit": "starts",
        "credit_cost": 1, "spray_disclosure": "unsprayed", "status": "live",
    }, headers=ALICE)
    assert r.status_code == 201, r.text
    lid = r.json()["id"]  # create returns public_listing(row) directly
    r = client.post("/v1/threads", json={"listing_id": lid}, headers=BOB)
    assert r.status_code == 200, r.text
    tid = r.json()["id"]
    for body in ("secret hello", "still available?"):
        r = client.post(f"/v1/threads/{tid}/messages",
                        json={"body": body}, headers=BOB)
        assert r.status_code == 201, r.text
    return tid


def test_body_round_trips_plaintext_on_wire(mem_all):
    client, *_ = mem_all
    tid = _setup_thread(client)
    r = client.get(f"/v1/threads/{tid}/messages", headers=BOB)
    assert r.status_code == 200, r.text
    assert [m["body"] for m in r.json()["messages"]] == ["secret hello", "still available?"]


def test_body_is_ciphertext_at_rest(mem_all):
    from app.crypto import MESSAGE_KEY_ENV, decrypt_text

    client, _, _, mrepo, _ = mem_all
    tid = _setup_thread(client)
    stored = mrepo._messages[tid]
    assert len(stored) == 2
    for row in stored:
        assert row["body"] != "secret hello"
        assert "secret" not in row["body"]
    assert decrypt_text(stored[0]["body"], MESSAGE_KEY_ENV) == "secret hello"


def test_tampered_token_raises():
    from app.crypto import MESSAGE_KEY_ENV, decrypt_text

    with pytest.raises(RuntimeError):
        decrypt_text("not-a-valid-token", MESSAGE_KEY_ENV)


def test_missing_key_fail_closed(monkeypatch):
    from app.crypto import MESSAGE_KEY_ENV, decrypt_text, encrypt_text

    monkeypatch.delenv(MESSAGE_KEY_ENV, raising=False)
    with pytest.raises(RuntimeError):
        encrypt_text("hello", MESSAGE_KEY_ENV)
    with pytest.raises(RuntimeError):
        decrypt_text("whatever", MESSAGE_KEY_ENV)


def test_support_endpoint_403_for_non_support(mem_all, monkeypatch):
    client, *_ = mem_all
    tid = _setup_thread(client)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(f"/v1/support/threads/{tid}/messages?reason=review", headers=MALLORY)
    assert r.status_code == 403, r.text


def test_support_endpoint_403_when_no_support_configured(mem_all, monkeypatch):
    client, *_ = mem_all
    tid = _setup_thread(client)
    monkeypatch.delenv("SUPPORT_UIDS", raising=False)
    r = client.get(f"/v1/support/threads/{tid}/messages?reason=review", headers=SUPPORT)
    assert r.status_code == 403, r.text


def test_support_endpoint_200_decrypts_and_audits(mem_all, monkeypatch):
    client, _, _, _, vrepo = mem_all
    tid = _setup_thread(client)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(
        f"/v1/support/threads/{tid}/messages?reason=abuse+report+%2342",
        headers=SUPPORT,
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["thread_id"] == tid
    assert [m["body"] for m in body["messages"]] == ["secret hello", "still available?"]
    # one audit row per message viewed
    views = vrepo.list_views_for_thread(tid)
    assert len(views) == 2
    for v in views:
        assert v["viewer_uid"] == "support1"
        assert v["thread_id"] == tid
        assert v["reason"] == "abuse report #42"
        assert v["message_id"]
        assert v["viewed_at"]


def test_support_endpoint_404_unknown_thread(mem_all, monkeypatch):
    client, *_ = mem_all
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(
        "/v1/support/threads/00000000-0000-0000-0000-000000000000/messages?reason=review",
        headers=SUPPORT,
    )
    assert r.status_code == 404, r.text


def test_support_endpoint_requires_reason(mem_all, monkeypatch):
    client, *_ = mem_all
    tid = _setup_thread(client)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(f"/v1/support/threads/{tid}/messages", headers=SUPPORT)
    assert r.status_code == 422, r.text

