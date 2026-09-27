"""API-012: ID verification — stub provider, HMAC webhook verification."""
from __future__ import annotations

import time

import pytest


@pytest.fixture()
def idv_env(monkeypatch):
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
