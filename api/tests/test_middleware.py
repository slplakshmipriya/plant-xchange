"""API-003: error envelope, request-id, rate limiting, JSON logging."""
from __future__ import annotations

import json
import logging

import pytest
from fastapi.testclient import TestClient

from app.middleware import (
    ENDPOINT_RATE_LIMITS,
    JsonFormatter,
    RateLimitMiddleware,
    TokenBucketLimiter,
)


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


def test_bucket_key_uses_client_host_not_xff():
    from starlette.requests import Request

    def make_request(xff: str | None):
        headers = []
        if xff is not None:
            headers.append((b"x-forwarded-for", xff.encode()))
        scope = {
            "type": "http",
            "method": "GET",
            "path": "/me",
            "headers": headers,
            "client": ("10.0.0.1", 1234),  # LB-appended real IP (uvicorn --proxy-headers)
        }
        return Request(scope)

    # M3: a spoofed leftmost XFF entry must NOT move the bucket — the key
    # comes from request.client.host, which the LB set.
    assert TokenBucketLimiter.bucket_key(make_request("203.0.113.7, 10.0.0.1")).startswith(
        "10.0.0.1:"
    )
    assert TokenBucketLimiter.bucket_key(make_request("198.51.100.9")).startswith(
        "10.0.0.1:"
    )
    # No peer address at all: falls back to "unknown".
    scope_req = make_request(None)
    scope_req.scope["client"] = None
    assert TokenBucketLimiter.bucket_key(scope_req).startswith("unknown:")
    # Credential fingerprint still partitions authed vs anonymous buckets.
    authed = make_request(None)
    authed.scope["headers"] = [(b"authorization", b"Bearer <redacted>")]
    assert TokenBucketLimiter.bucket_key(authed).startswith("10.0.0.1:")
    assert TokenBucketLimiter.bucket_key(authed) != TokenBucketLimiter.bucket_key(
        make_request(None)
    )


def test_endpoint_specific_limits_apply_in_addition_to_global():
    # M3: abuse-prone endpoints get tighter buckets that trip before the
    # global one; other paths are unaffected.
    from fastapi import FastAPI
    from fastapi.testclient import TestClient

    app = FastAPI()
    app.add_middleware(
        RateLimitMiddleware, per_minute=1000, endpoint_limits=[(r"^/v1/reports", 2)]
    )

    @app.post("/v1/reports")
    def reports():
        return {"ok": True}

    @app.get("/v1/other")
    def other():
        return {"ok": True}

    c = TestClient(app)
    assert c.post("/v1/reports").status_code == 200
    assert c.post("/v1/reports").status_code == 200
    r = c.post("/v1/reports")
    assert r.status_code == 429
    assert r.headers["Retry-After"]
    assert c.get("/v1/other").status_code == 200


def test_default_endpoint_limits_cover_review_flagged_paths():
    # The three paths the review flagged must match some default pattern.
    import re

    paths = ["/v1/reports", "/v1/threads/abc-123/messages", "/v1/auth/verify"]
    for path in paths:
        assert any(
            re.compile(pat).match(path) for pat, _ in ENDPOINT_RATE_LIMITS
        ), f"no default limit pattern matches {path}"
    # Non-flagged paths match nothing.
    assert not any(
        re.compile(pat).match("/v1/listings") for pat, _ in ENDPOINT_RATE_LIMITS
    )


def test_healthz_trailing_slash_is_exempt(client):
    r = client.get("/healthz/")
    assert r.status_code in (200, 307)  # 307 = FastAPI slash redirect, also exempt
    if r.status_code == 307:
        assert r.is_redirect
    else:
        assert r.json()["status"] == "ok"
