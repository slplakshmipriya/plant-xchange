"""At-rest encryption for chat message bodies + the support dashboard.

- bodies are Fernet ciphertext at rest (both repos), plaintext on the wire
- GET /v1/support/threads/{id}/messages is support-gated (403 otherwise),
  H2-scoped to threads with an open report/dispute, uses a controlled
  ``reason`` enum (422 otherwise), paginates (limit <= 100), and writes
  batched moderation_views audit rows per message viewed
- GET /v1/support/moderation-views?thread_id= reviews the hash-chained,
  append-only audit log (403 otherwise)
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
    modrepo = moderation_mod.MemoryModerationRepo()
    wrepo = wantlist_mod.MemoryWantRepo()
    nrepo = notify_mod.MemoryNotificationRepo()
    client.app.dependency_overrides[users_mod.get_user_repo] = lambda: urepo
    client.app.dependency_overrides[listings_mod.get_listing_repo] = lambda: lrepo
    client.app.dependency_overrides[msg_mod.get_message_repo] = lambda: mrepo
    client.app.dependency_overrides[moderation_mod.get_moderation_view_repo] = lambda: vrepo
    client.app.dependency_overrides[moderation_mod.get_moderation_repo] = lambda: modrepo
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
    return client, urepo, lrepo, mrepo, vrepo, modrepo


ALICE = {"Authorization": "Bearer good-token"}
BOB = {"Authorization": "Bearer bob-token"}
MALLORY = {"Authorization": "Bearer mallory-token"}
SUPPORT = {"Authorization": "Bearer support-token"}


def _setup_thread(client):
    """Alice owns a listing; bob opens a thread and sends two messages.
    Returns (thread_id, listing_id)."""
    for headers, name in ((ALICE, "Alice"), (BOB, "Bob")):
        r = client.post("/v1/users", json={"display_name": name, "age_attestation": True}, headers=headers)
        assert r.status_code == 200, r.text
    r = client.post("/v1/listings", json={
        "type": "seedling", "photos": ["https://example.com/t.jpg"],
        "variety": "Basil", "quantity": 6, "unit": "starts",
        "credit_cost": 1, "spray_disclosure": "unsprayed", "status": "live",
    }, headers=ALICE)
    assert r.status_code == 201, r.text
    lid = r.json()["id"]  # create returns public_listing(row) directly
    r = client.post("/v1/threads", json={"listing_id": lid}, headers=BOB)
    assert r.status_code == 201, r.text
    tid = r.json()["id"]
    for body in ("secret hello", "still available?"):
        r = client.post(f"/v1/threads/{tid}/messages",
                        json={"body": body}, headers=BOB)
        assert r.status_code == 201, r.text
    return tid, lid


def _link_open_report(modrepo, listing_id):
    """File a report naming the thread's listing so H2 scoping allows reads."""
    return modrepo.add_report(
        reporter_uid="mallory", target_type="LISTING", target_id=listing_id,
        category="safety", details="threatening messages")


def test_body_round_trips_plaintext_on_wire(mem_all):
    client, *_ = mem_all
    tid, _ = _setup_thread(client)
    r = client.get(f"/v1/threads/{tid}/messages", headers=BOB)
    assert r.status_code == 200, r.text
    assert [m["body"] for m in r.json()["messages"]] == ["secret hello", "still available?"]


def test_body_is_ciphertext_at_rest(mem_all):
    from app.crypto import MESSAGE_KEY_ENV, decrypt_text

    client, _, _, mrepo, *_ = mem_all
    tid, _ = _setup_thread(client)
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
    tid, lid = _setup_thread(client)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(f"/v1/support/threads/{tid}/messages?reason=report_investigation",
                   headers=MALLORY)
    assert r.status_code == 403, r.text


def test_support_endpoint_403_when_no_support_configured(mem_all, monkeypatch):
    client, *_ = mem_all
    tid, lid = _setup_thread(client)
    monkeypatch.delenv("SUPPORT_UIDS", raising=False)
    r = client.get(f"/v1/support/threads/{tid}/messages?reason=report_investigation",
                   headers=SUPPORT)
    assert r.status_code == 403, r.text


def test_support_endpoint_200_decrypts_and_audits(mem_all, monkeypatch):
    client, _, _, _, vrepo, modrepo = mem_all
    tid, lid = _setup_thread(client)
    _link_open_report(modrepo, lid)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(
        f"/v1/support/threads/{tid}/messages?reason=report_investigation",
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
        assert v["reason"] == "report_investigation"
        assert v["message_id"]
        assert v["viewed_at"]
        assert v["row_hash"]
    # H3: the chain links — every row's prev_hash is the previous row_hash.
    assert views[0]["prev_hash"] is None
    assert views[1]["prev_hash"] == views[0]["row_hash"]


def test_support_endpoint_403_without_open_case(mem_all, monkeypatch):
    # H2: no report on the listing/participants and no open dispute -> 403,
    # and nothing is written to the audit log.
    client, _, _, _, vrepo, _ = mem_all
    tid, lid = _setup_thread(client)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(
        f"/v1/support/threads/{tid}/messages?reason=report_investigation",
        headers=SUPPORT,
    )
    assert r.status_code == 403, r.text
    assert r.json()["code"] == "no_open_case"
    assert vrepo.list_views_for_thread(tid) == []


def test_support_endpoint_403_when_only_resolved_dispute(mem_all, monkeypatch):
    # An open dispute links the thread, but resolving it revokes the link.
    client, _, _, _, _, modrepo = mem_all
    tid, lid = _setup_thread(client)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    dispute = modrepo.add_dispute(exchange_id=lid, reporter_uid="bob",
                                  reason="x", details="y")
    r = client.get(
        f"/v1/support/threads/{tid}/messages?reason=dispute_evidence",
        headers=SUPPORT,
    )
    assert r.status_code == 200, r.text
    from datetime import datetime, timezone
    modrepo.resolve_dispute(dispute["id"], "rejected", 0, "support1",
                            datetime.now(timezone.utc))
    r = client.get(
        f"/v1/support/threads/{tid}/messages?reason=dispute_evidence",
        headers=SUPPORT,
    )
    assert r.status_code == 403, r.text
    assert r.json()["code"] == "no_open_case"


def test_support_endpoint_404_unknown_thread(mem_all, monkeypatch):
    client, *_ = mem_all
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(
        "/v1/support/threads/00000000-0000-0000-0000-000000000000/messages"
        "?reason=report_investigation",
        headers=SUPPORT,
    )
    assert r.status_code == 404, r.text


def test_support_endpoint_requires_reason(mem_all, monkeypatch):
    client, *_ = mem_all
    tid, lid = _setup_thread(client)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(f"/v1/support/threads/{tid}/messages", headers=SUPPORT)
    assert r.status_code == 422, r.text


def test_support_endpoint_rejects_free_text_reason(mem_all, monkeypatch):
    # H2: reason is a controlled vocabulary now — anything else is a 422.
    client, _, _, _, _, modrepo = mem_all
    tid, lid = _setup_thread(client)
    _link_open_report(modrepo, lid)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(
        f"/v1/support/threads/{tid}/messages?reason=abuse+report+%2342",
        headers=SUPPORT,
    )
    assert r.status_code == 422, r.text


def test_support_endpoint_paginates_and_batches_audit(mem_all, monkeypatch):
    # M2: limit <= 100 with a cursor; audit rows batch-insert per page.
    client, _, _, _, vrepo, modrepo = mem_all
    tid, lid = _setup_thread(client)
    _link_open_report(modrepo, lid)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    for i in range(3):
        r = client.post(f"/v1/threads/{tid}/messages",
                        json={"body": f"msg {i}"}, headers=BOB)
        assert r.status_code == 201, r.text

    page1 = client.get(
        f"/v1/support/threads/{tid}/messages?reason=safety_review&limit=2",
        headers=SUPPORT).json()
    assert [m["body"] for m in page1["messages"]] == ["secret hello", "still available?"]
    assert page1["next_cursor"]

    page2 = client.get(
        f"/v1/support/threads/{tid}/messages?reason=safety_review&limit=2"
        f"&cursor={page1['next_cursor']}",
        headers=SUPPORT).json()
    assert [m["body"] for m in page2["messages"]] == ["msg 0", "msg 1"]
    assert page2["next_cursor"]

    page3 = client.get(
        f"/v1/support/threads/{tid}/messages?reason=safety_review&limit=2"
        f"&cursor={page2['next_cursor']}",
        headers=SUPPORT).json()
    assert [m["body"] for m in page3["messages"]] == ["msg 2"]
    assert page3["next_cursor"] is None

    # one audit row per message returned, batched per page (no crash, all logged)
    views = vrepo.list_views_for_thread(tid)
    assert len(views) == 5
    assert all(v["reason"] == "safety_review" for v in views)


def test_support_endpoint_limit_capped_at_100(mem_all, monkeypatch):
    client, _, _, _, _, modrepo = mem_all
    tid, lid = _setup_thread(client)
    _link_open_report(modrepo, lid)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(
        f"/v1/support/threads/{tid}/messages?reason=safety_review&limit=101",
        headers=SUPPORT)
    assert r.status_code == 422, r.text


def test_moderation_views_route_200_for_support(mem_all, monkeypatch):
    # H3: the audit log has a review surface.
    client, _, _, _, _, modrepo = mem_all
    tid, lid = _setup_thread(client)
    _link_open_report(modrepo, lid)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(f"/v1/support/threads/{tid}/messages?reason=fraud_investigation",
                   headers=SUPPORT)
    assert r.status_code == 200, r.text

    r = client.get(f"/v1/support/moderation-views?thread_id={tid}", headers=SUPPORT)
    assert r.status_code == 200, r.text
    payload = r.json()
    assert payload["thread_id"] == tid
    assert len(payload["views"]) == 2
    view = payload["views"][0]
    assert view["viewer_uid"] == "support1"
    assert view["reason"] == "fraud_investigation"
    assert view["message_id"]
    assert view["viewed_at"]
    assert view["row_hash"]
    assert payload["views"][1]["prev_hash"] == payload["views"][0]["row_hash"]


def test_moderation_views_route_403_for_non_support(mem_all, monkeypatch):
    client, *_ = mem_all
    tid, lid = _setup_thread(client)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(f"/v1/support/moderation-views?thread_id={tid}", headers=MALLORY)
    assert r.status_code == 403, r.text
    monkeypatch.delenv("SUPPORT_UIDS", raising=False)
    r = client.get(f"/v1/support/moderation-views?thread_id={tid}", headers=SUPPORT)
    assert r.status_code == 403, r.text


def test_moderation_views_route_empty_for_unviewed_thread(mem_all, monkeypatch):
    client, *_ = mem_all
    tid, lid = _setup_thread(client)
    monkeypatch.setenv("SUPPORT_UIDS", "support1")
    r = client.get(f"/v1/support/moderation-views?thread_id={tid}", headers=SUPPORT)
    assert r.status_code == 200, r.text
    assert r.json()["views"] == []


def test_chain_hash_detects_tampering():
    # H3: rewriting a row invalidates every later row's chain link.
    from app.moderation import _chain_hash

    h1 = _chain_hash(None, "support1", "t1", "m1", "safety_review")
    h2 = _chain_hash(h1, "support1", "t1", "m2", "safety_review")
    assert h1 != h2
    tampered = _chain_hash("WRONG", "support1", "t1", "m2", "safety_review")
    assert tampered != h2

