"""API-002: Firebase auth accept/reject paths (firebase-admin fully mocked)."""
from __future__ import annotations

import pytest
from fastapi import Depends
from fastapi.testclient import TestClient

from app.auth import ensure_owner, get_current_uid


@pytest.fixture()
def mock_verify(monkeypatch):
    """verify_id_token: 'good-token' -> uid alice, anything else raises."""
    import app.auth as auth_mod

    def fake(token: str) -> dict:
        if token == "good-token":
            return {"uid": "alice"}
        raise ValueError("bad token")

    monkeypatch.setattr(auth_mod, "verify_id_token", fake)


def test_healthz_exempt_from_auth(client):
    assert client.get("/healthz").status_code == 200


def test_me_rejects_missing_header(client):
    r = client.get("/me")
    assert r.status_code == 401
    assert r.json()["code"] == "unauthorized"


def test_me_rejects_malformed_header(client):
    r = client.get("/me", headers={"Authorization": "Token abc"})
    assert r.status_code == 401


def test_me_rejects_invalid_token(client, mock_verify):
    r = client.get("/me", headers={"Authorization": "Bearer bogus"})
    assert r.status_code == 401
    assert r.json()["code"] == "unauthorized"


def test_me_accepts_valid_token(client, mock_verify):
    r = client.get("/me", headers={"Authorization": "Bearer good-token"})
    assert r.status_code == 200
    assert r.json() == {"uid": "alice"}


def _owner_client(monkeypatch) -> TestClient:
    from app.main import create_app

    app = create_app()

    @app.get("/things/{owner_uid}")
    def get_thing(owner_uid: str, uid: str = Depends(get_current_uid)):
        ensure_owner(owner_uid, uid)
        return {"owner": owner_uid}

    return TestClient(app)


def test_owner_allowed(client, mock_verify, monkeypatch):
    tc = _owner_client(monkeypatch)
    r = tc.get("/things/alice", headers={"Authorization": "Bearer good-token"})
    assert r.status_code == 200
    assert r.json() == {"owner": "alice"}


def test_non_owner_forbidden(client, mock_verify, monkeypatch):
    tc = _owner_client(monkeypatch)
    r = tc.get("/things/bob", headers={"Authorization": "Bearer good-token"})
    assert r.status_code == 403


def test_ensure_owner_unit():
    ensure_owner("alice", "alice")  # no raise
    with pytest.raises(Exception) as ei:
        ensure_owner("bob", "alice")
    assert ei.value.status_code == 403
