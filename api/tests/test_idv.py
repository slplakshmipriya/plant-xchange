"""API-012: ID verification — stub provider, HMAC webhook verification."""
from __future__ import annotations

import time

import pytest


@pytest.fixture()
def idv_env(monkeypatch):
    # Explicit opt-in: the stub route/provider are dead without it (C1).
    monkeypatch.setenv("ENABLE_IDV_STUB", "1")
    monkeypatch.setenv("IDV_PROVIDER", "stub")
    monkeypatch.setenv("IDV_WEBHOOK_SECRET", "test-secret")


def _signed(uid: str, decision: str, secret: str = "test-secret", ts=None) -> tuple[bytes, str]:
    from app.idv import build_verdict_payload, sign_payload

    body = build_verdict_payload(uid, decision, ts=ts)
    return body, sign_payload(body, secret)


def test_session_requires_auth(client):
    r = client.post("/v1/idv/session")
    assert r.status_code == 401


def test_session_marks_pending(mem_users, mock_verify, auth_headers, idv_env):
    client, repo = mem_users
    repo.upsert("alice")
    r = client.post("/v1/idv/session", headers=auth_headers)
    assert r.status_code == 200
    body = r.json()
    assert body["session_url"].startswith("https://idv-stub.local/sessions/alice-")
    assert repo.get("alice")["idv_status"] == "pending"


def test_webhook_is_auth_exempt_but_signature_verified(mem_users, idv_env):
    client, repo = mem_users
    repo.upsert("alice")
    body, sig = _signed("alice", "approved")
    # no Authorization header at all
    r = client.post("/v1/idv/webhook", content=body,
                    headers={"X-IDV-Signature": sig, "Content-Type": "application/json"})
    assert r.status_code == 200
    assert r.json() == {"ok": True, "uid": "alice", "idv_status": "verified"}
    assert repo.get("alice")["idv_status"] == "verified"


def test_webhook_rejects_bad_signature(mem_users, idv_env):
    client, repo = mem_users
    repo.upsert("alice")
    body, _ = _signed("alice", "approved")
    r = client.post("/v1/idv/webhook", content=body,
                    headers={"X-IDV-Signature": "deadbeef"})
    assert r.status_code == 401
    assert r.json()["code"] == "invalid_webhook_signature"
    assert repo.get("alice")["idv_status"] != "verified"


def test_webhook_rejects_missing_signature(mem_users, idv_env):
    client, _ = mem_users
    body, _ = _signed("alice", "approved")
    r = client.post("/v1/idv/webhook", content=body)
    assert r.status_code == 401


def test_webhook_rejects_stale_payload(mem_users, idv_env):
    client, _ = mem_users
    body, sig = _signed("alice", "approved", ts=int(time.time()) - 3600)
    r = client.post("/v1/idv/webhook", content=body,
                    headers={"X-IDV-Signature": sig})
    assert r.status_code == 401


def test_webhook_rejects_tampered_body(mem_users, idv_env):
    client, _ = mem_users
    body, sig = _signed("alice", "approved")
    tampered = body.replace(b"approved", b"rejected")
    r = client.post("/v1/idv/webhook", content=tampered,
                    headers={"X-IDV-Signature": sig})
    assert r.status_code == 401


def test_stub_decide_approved_and_rejected(mem_users, mock_verify, auth_headers, idv_env):
    client, repo = mem_users
    repo.upsert("alice")
    r = client.post("/v1/idv/stub/decide", json={"decision": "approved"}, headers=auth_headers)
    assert r.status_code == 200
    assert r.json()["idv_status"] == "verified"
    r = client.post("/v1/idv/stub/decide", json={"decision": "rejected"}, headers=auth_headers)
    assert r.json()["idv_status"] == "failed"
    assert repo.get("alice")["idv_status"] == "failed"


def test_stub_decide_rejects_bad_decision(mem_users, mock_verify, auth_headers, idv_env):
    client, _ = mem_users
    r = client.post("/v1/idv/stub/decide", json={"decision": "maybe"}, headers=auth_headers)
    assert r.status_code == 422


def test_stub_decide_404_when_real_provider(mem_users, mock_verify, auth_headers, monkeypatch):
    client, _ = mem_users
    monkeypatch.setenv("IDV_PROVIDER", "stripe")
    r = client.post("/v1/idv/stub/decide", json={"decision": "approved"}, headers=auth_headers)
    assert r.status_code == 404


# --- C1 fail-closed tests ---------------------------------------------------

def test_stub_decide_dead_by_default(mem_users, mock_verify, auth_headers, monkeypatch):
    """No ENABLE_IDV_STUB -> the self-verification route is unusable (404),
    even when IDV_PROVIDER is the (default) stub."""
    client, repo = mem_users
    repo.upsert("alice")
    monkeypatch.delenv("ENABLE_IDV_STUB", raising=False)
    monkeypatch.setenv("IDV_PROVIDER", "stub")
    monkeypatch.setenv("IDV_WEBHOOK_SECRET", "test-secret")
    r = client.post("/v1/idv/stub/decide", json={"decision": "approved"}, headers=auth_headers)
    assert r.status_code == 404
    assert repo.get("alice")["idv_status"] != "verified"


def test_webhook_secret_fail_closed(monkeypatch):
    """_webhook_secret() raises when IDV_WEBHOOK_SECRET is unset — no dev fallback."""
    from app.idv import WebhookVerificationError, _webhook_secret

    monkeypatch.delenv("IDV_WEBHOOK_SECRET", raising=False)
    monkeypatch.setenv("IDV_PROVIDER", "stub")
    monkeypatch.setenv("ENABLE_IDV_STUB", "1")
    with pytest.raises(WebhookVerificationError):
        _webhook_secret()


def test_webhook_401_when_secret_unset(mem_users, monkeypatch):
    """The signed-webhook path is unusable without a secret (fail closed)."""
    client, repo = mem_users
    repo.upsert("alice")
    monkeypatch.setenv("ENABLE_IDV_STUB", "1")
    monkeypatch.setenv("IDV_PROVIDER", "stub")
    monkeypatch.delenv("IDV_WEBHOOK_SECRET", raising=False)
    body, sig = _signed("alice", "approved", secret="anything")
    r = client.post("/v1/idv/webhook", content=body,
                    headers={"X-IDV-Signature": sig, "Content-Type": "application/json"})
    assert r.status_code == 401
    assert repo.get("alice")["idv_status"] != "verified"


def test_stub_provider_blocked_without_opt_in(monkeypatch):
    """IDV_PROVIDER=stub without ENABLE_IDV_STUB=1 refuses to resolve."""
    from fastapi import HTTPException

    from app.idv import StubIDVProvider, get_idv_provider

    monkeypatch.setenv("IDV_PROVIDER", "stub")
    monkeypatch.delenv("ENABLE_IDV_STUB", raising=False)
    with pytest.raises(HTTPException) as exc_info:
        get_idv_provider()
    assert exc_info.value.status_code == 503

    monkeypatch.setenv("ENABLE_IDV_STUB", "1")
    assert isinstance(get_idv_provider(), StubIDVProvider)


def test_validate_idv_config_blocks_stub_deploy_by_default(monkeypatch):
    """Startup/config validation: stub without opt-in raises (fail closed)."""
    from app.config import get_settings, validate_idv_config

    monkeypatch.setenv("IDV_PROVIDER", "stub")
    monkeypatch.delenv("ENABLE_IDV_STUB", raising=False)
    with pytest.raises(RuntimeError):
        validate_idv_config(get_settings())

    monkeypatch.setenv("ENABLE_IDV_STUB", "1")
    validate_idv_config(get_settings())  # explicit opt-in: no raise

    monkeypatch.setenv("IDV_PROVIDER", "stripe")
    monkeypatch.delenv("ENABLE_IDV_STUB", raising=False)
    validate_idv_config(get_settings())  # real provider: no raise


def test_real_provider_session_not_implemented(mock_verify, auth_headers, monkeypatch):
    from app.idv import RealIDVProvider

    monkeypatch.setenv("IDV_PROVIDER", "stripe")
    with pytest.raises(NotImplementedError):
        RealIDVProvider("stripe").create_session("alice")


def test_webhook_signature_unit(monkeypatch):
    import app.idv as idv_mod
    from app.idv import WebhookVerificationError, sign_payload

    monkeypatch.setattr(idv_mod, "_webhook_secret", lambda: "s3cret")
    body = b'{"uid":"u","decision":"approved","ts":%d}' % int(time.time())
    sig = sign_payload(body, "s3cret")
    assert idv_mod.verify_webhook_signature(body, sig) == ("u", "verified")
    with pytest.raises(WebhookVerificationError):
        idv_mod.verify_webhook_signature(body, "wrong")
