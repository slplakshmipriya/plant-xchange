"""API-011: phone verification binds the Firebase-verified phone claim to uid."""
from __future__ import annotations


def test_verify_requires_auth(client):
    r = client.post("/v1/auth/verify")
    assert r.status_code == 401


def test_verify_rejects_token_without_phone_claim(mem_users, mock_verify):
    client, _ = mem_users
    r = client.post(
        "/v1/auth/verify", headers={"Authorization": "Bearer nophone-token"}
    )
    assert r.status_code == 400
    assert r.json()["code"] == "phone_verification_required"


def test_verify_upserts_phone_hash_and_fingerprint(mem_users, mock_verify, auth_headers):
    from app.verify import phone_hash

    client, repo = mem_users
    r = client.post(
        "/v1/auth/verify",
        headers={**auth_headers, "X-Device-Fingerprint": "fp-123"},
    )
    assert r.status_code == 200
    assert r.json() == {"uid": "alice", "verified": True}
    row = repo.get("alice")
    assert row["phone_hash"] == phone_hash("+15551234567")
    assert row["device_fingerprint"] == "fp-123"


def test_verify_without_fingerprint_header_ok(mem_users, mock_verify, auth_headers):
    client, repo = mem_users
    r = client.post("/v1/auth/verify", headers=auth_headers)
    assert r.status_code == 200
    assert repo.get("alice").get("device_fingerprint") is None


def test_verify_rejects_duplicate_phone_safely(mem_users, mock_verify, auth_headers):
    from app.verify import phone_hash

    client, repo = mem_users
    # bob already claimed this phone number through the verify flow
    repo.upsert("bob", phone_hash=phone_hash("+15551234567"))
    r = client.post("/v1/auth/verify", headers=auth_headers)
    assert r.status_code == 409
    body = r.json()
    assert body["code"] == "phone_in_use"
    assert "bob" not in body["message"]  # safe error: no uid leaked


def test_verify_idempotent_for_same_user(mem_users, mock_verify, auth_headers):
    client, repo = mem_users
    assert client.post("/v1/auth/verify", headers=auth_headers).status_code == 200
    assert client.post("/v1/auth/verify", headers=auth_headers).status_code == 200
    assert repo.get("alice")["phone_hash"] is not None


def test_phone_hash_is_stable_and_domain_separated():
    from app.verify import phone_hash

    assert phone_hash("+15551234567") == phone_hash("+15551234567")
    assert phone_hash("+15551234567") != phone_hash("+15551234568")
    assert len(phone_hash("+15551234567")) == 64
