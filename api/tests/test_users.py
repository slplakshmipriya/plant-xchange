"""API-010: user profiles — upsert/get/patch, owner scoping, PII rules."""
from __future__ import annotations

import pytest


def test_unauthenticated_profile_write_rejected(mem_users):
    client, _ = mem_users
    r = client.post("/v1/users", json={"display_name": "Alice"})
    assert r.status_code == 401


def test_upsert_creates_profile(mem_users, mock_verify, auth_headers):
    client, repo = mem_users
    r = client.post(
        "/v1/users",
        json={"display_name": "Alice", "home_zip": "85281"},
        headers=auth_headers,
    )
    assert r.status_code == 200
    body = r.json()
    assert body["uid"] == "alice"
    assert body["display_name"] == "Alice"
    assert body["home_zip"] == "85281"
    assert repo.get("alice")["display_name"] == "Alice"


def test_patch_updates_only_sent_fields(mem_users, mock_verify, auth_headers):
    client, _ = mem_users
    client.post("/v1/users", json={"display_name": "Alice", "home_zip": "85281"},
                headers=auth_headers)
    r = client.patch("/v1/users/me", json={"display_name": "Alicia"}, headers=auth_headers)
    assert r.status_code == 200
    body = r.json()
    assert body["display_name"] == "Alicia"
    assert body["home_zip"] == "85281"  # untouched


def test_get_me_404_before_profile_exists(mem_users, mock_verify, auth_headers):
    client, _ = mem_users
    r = client.get("/v1/users/me", headers=auth_headers)
    assert r.status_code == 404
    assert r.json()["code"] == "profile_not_found"


def test_invalid_zip_rejected(mem_users, mock_verify, auth_headers):
    client, _ = mem_users
    r = client.post("/v1/users", json={"home_zip": "not-a-zip"}, headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "invalid_zip"


def test_public_profile_hides_pii_and_zip(mem_users, mock_verify, auth_headers):
    client, repo = mem_users
    repo.upsert("alice", display_name="Alice", home_zip="85281",
                phone_hash="ph", device_fingerprint="fp")
    # another user views alice's public profile
    r = client.get("/v1/users/alice", headers=auth_headers)
    assert r.status_code == 200
    body = r.json()
    assert "home_zip" not in body
    assert "phone_hash" not in body
    assert "device_fingerprint" not in body


def test_owner_profile_hides_phone_and_fingerprint(mem_users, mock_verify, auth_headers):
    client, repo = mem_users
    repo.upsert("alice", display_name="Alice", home_zip="85281",
                phone_hash="ph", device_fingerprint="fp")
    r = client.get("/v1/users/me", headers=auth_headers)
    body = r.json()
    assert body["home_zip"] == "85281"  # owner sees own zip
    assert "phone_hash" not in body
    assert "device_fingerprint" not in body


def test_duplicate_phone_hash_rejected_in_repo():
    from app.users import MemoryUserRepo, PhoneInUseError

    repo = MemoryUserRepo()
    repo.upsert("alice", phone_hash="ph1")
    with pytest.raises(PhoneInUseError):
        repo.upsert("bob", phone_hash="ph1")
    # same uid re-upserting its own hash is fine
    repo.upsert("alice", phone_hash="ph1", display_name="Alice")
    assert repo.get("alice")["display_name"] == "Alice"


def test_public_profile_404_for_unknown_user(mem_users, mock_verify, auth_headers):
    client, _ = mem_users
    r = client.get("/v1/users/nobody", headers=auth_headers)
    assert r.status_code == 404
