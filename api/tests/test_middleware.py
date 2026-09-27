"""API-003: error envelope, request-id, rate limiting, JSON logging."""
from __future__ import annotations

import json
import logging

import pytest
from fastapi.testclient import TestClient

from app.middleware import JsonFormatter, TokenBucketLimiter


@pytest.fixture()
def auth_headers(monkeypatch):
    """Valid Firebase token mock; returns headers dict for authenticated calls."""
    import app.auth as auth_mod

    def fake(token: str) -> dict:
        if token == "good-token":
            return {"uid": "alice"}
        raise ValueError("bad token")

    monkeypatch.setattr(auth_mod, "verify_id_token", fake)
    return {"Authorization": "Bearer good-token"}


def test_404_uses_error_envelope(client, auth_headers):
    r = client.get("/no-such-route", headers=auth_headers)
    assert r.status_code == 404
    body = r.json()
    assert set(body) == {"code", "message", "request_id"}
    assert body["code"] == "not_found"
    assert body["request_id"]
    # Envelope request_id matches the echoed header.
    assert r.headers["X-Request-ID"] == body["request_id"]


def test_request_id_propagates_from_header(client, auth_headers):
    headers = {**auth_headers, "X-Request-ID": "trace-123"}
    r = client.get("/no-such-route", headers=headers)
    assert r.status_code == 404
    assert r.json()["request_id"] == "trace-123"
    assert r.headers["X-Request-ID"] == "trace-123"


def test_unauthenticated_unknown_path_is_401_not_404(client):
    """Secure default: auth runs before routing; no route-existence leak."""
    r = client.get("/no-such-route")
    assert r.status_code == 401


def test_500_hides_internals(client, monkeypatch, auth_headers):
    from app.main import create_app

    app = create_app()

    @app.get("/boom-test")
    def boom_test():
        raise RuntimeError("super secret internals")

    tc = TestClient(app, raise_server_exceptions=False)
    r = tc.get("/boom-test", headers=auth_headers)
    assert r.status_code == 500
    body = r.json()
    assert body["code"] == "internal_error"
    assert body["message"] == "Internal server error."
    assert "super secret" not in r.text
    assert "Traceback" not in r.text


def test_rate_limit_429(monkeypatch):
    from app.main import create_app

    monkeypatch.setenv("RATE_LIMIT_PER_MIN", "3")
    tc = TestClient(create_app())
    statuses = [tc.get("/healthz").status_code for _ in range(5)]
    assert statuses[:3] == [200, 200, 200]
    assert statuses[3] == 429
    r = tc.get("/healthz")
    assert r.status_code == 429
    assert r.json()["code"] == "rate_limited"
    assert "Retry-After" in r.headers


def test_rate_limit_bucketed_per_credential(monkeypatch):
    """Different Authorization headers get independent buckets (same IP)."""
    from app.main import create_app

    monkeypatch.setenv("RATE_LIMIT_PER_MIN", "2")
    tc = TestClient(create_app())
    h1 = {"Authorization": "Bearer cred-one"}
    h2 = {"Authorization": "Bearer cred-two"}
    assert tc.get("/healthz", headers=h1).status_code == 200
    assert tc.get("/healthz", headers=h1).status_code == 200
    assert tc.get("/healthz", headers=h1).status_code == 429
    # Second credential still has budget.
    assert tc.get("/healthz", headers=h2).status_code == 200


def test_token_bucket_refills():
    limiter = TokenBucketLimiter(per_minute=6000)  # 100/sec
    assert limiter.allow("k")[0]
    # Exhaust a tiny bucket deterministically.
    tiny = TokenBucketLimiter(per_minute=60)
    results = [tiny.allow("k")[0] for _ in range(61)]
    assert results.count(True) == 60
    assert results[-1] is False


def test_json_formatter_emits_structured_logs():
    record = logging.LogRecord(
        name="test", level=logging.INFO, pathname=__file__, lineno=1,
        msg="hello", args=(), exc_info=None,
    )
    payload = json.loads(JsonFormatter().format(record))
    assert payload["level"] == "INFO"
    assert payload["msg"] == "hello"
    assert "ts" in payload and "logger" in payload and "request_id" in payload


def test_unauthorized_uses_envelope_with_request_id(client):
    r = client.get("/me")  # no auth header
    assert r.status_code == 401
    body = r.json()
    assert set(body) == {"code", "message", "request_id"}
    assert r.headers["X-Request-ID"] == body["request_id"]
