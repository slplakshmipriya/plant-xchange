"""ID verification (API-012).

Provider abstraction so the real vendor (Stripe Identity / Didit / ...) can
replace the stub without touching routes:

- ``IDVProvider.create_session(uid)`` -> ``{"session_url": str}`` — the URL
  the client opens for document capture. Provider-hosted; ID images never
  touch our infra.
- ``IDVProvider.verify_webhook(body, signature)`` -> ``(uid, status)`` —
  HMAC-SHA256 verification with timestamp tolerance. Raises
  ``WebhookVerificationError`` on any failure (fail closed).

``StubIDVProvider`` simulates the vendor, but it is **never live by default**:
it is instantiated only when the explicit ``ENABLE_IDV_STUB=1`` opt-in is set
(see ``app/config.py::validate_idv_config``). The test-only
``POST /v1/idv/stub/decide`` endpoint is gated on the same opt-in — never on
the provider name — and returns 404 when the opt-in is off.

``/v1/idv/webhook`` is exempt from Firebase auth (it cannot carry a user
token) and relies SOLELY on HMAC signature verification. The webhook secret
fails closed: ``IDV_WEBHOOK_SECRET`` must be set, or every verification
raises.
"""

from __future__ import annotations

import hashlib
import hmac
import json
import logging
import time
from typing import Protocol

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from .auth import get_current_uid
from .config import get_settings, validate_idv_config
from .users import UserRepo, get_user_repo

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/v1/idv", tags=["idv"])

IDV_STATUSES = ("unverified", "pending", "verified", "failed")
WEBHOOK_MAX_AGE_S = 300


class WebhookVerificationError(Exception):
    pass


def _webhook_secret() -> str:
    """Return the IDV webhook HMAC secret, or raise (fail closed).

    There is no dev fallback: an unset ``IDV_WEBHOOK_SECRET`` means the
    webhook cannot be verified, so it must not verify.
    """
    secret = get_settings().idv_webhook_secret
    if not secret:
        raise WebhookVerificationError("IDV webhook secret not configured")
    return secret


def sign_payload(body: bytes, secret: str) -> str:
    return hmac.new(secret.encode("utf-8"), body, hashlib.sha256).hexdigest()


def build_verdict_payload(uid: str, decision: str, ts: int | None = None) -> bytes:
    return json.dumps(
        {"uid": uid, "decision": decision, "ts": ts if ts is not None else int(time.time())},
        separators=(",", ":"),
    ).encode("utf-8")


def verify_webhook_signature(body: bytes, signature: str | None) -> tuple[str, str]:
    """Verify HMAC + freshness. Returns (uid, idv_status). Fail closed."""
    if not signature:
        raise WebhookVerificationError("missing signature")
    secret = _webhook_secret()
    expected = sign_payload(body, secret)
    if not hmac.compare_digest(expected, signature):
        raise WebhookVerificationError("bad signature")
    try:
        payload = json.loads(body.decode("utf-8"))
        uid, decision, ts = payload["uid"], payload["decision"], payload["ts"]
    except (ValueError, KeyError, TypeError) as exc:
        raise WebhookVerificationError("malformed payload") from exc
    if not isinstance(ts, (int, float)) or isinstance(ts, bool):
        raise WebhookVerificationError("malformed payload")
    if abs(time.time() - ts) > WEBHOOK_MAX_AGE_S:
        raise WebhookVerificationError("stale payload")
    if decision == "approved":
        status = "verified"
    elif decision == "rejected":
        status = "failed"
    else:
        raise WebhookVerificationError("unknown decision")
    if not isinstance(uid, str) or not uid:
        raise WebhookVerificationError("malformed payload")
    return uid, status


class IDVProvider(Protocol):
    def create_session(self, uid: str) -> dict:
        """Start a verification session. Returns {"session_url": ...}."""
        ...

    def verify_webhook(self, body: bytes, signature: str | None) -> tuple[str, str]:
        """Verify a provider webhook. Returns (uid, idv_status)."""
        ...


class StubIDVProvider:
    """Dev stub: no network, no PII storage. Simulates approve/reject."""

    def create_session(self, uid: str) -> dict:
        token = hashlib.sha256(f"{uid}:{time.time()}".encode()).hexdigest()[:16]
        return {
            "session_url": f"https://idv-stub.local/sessions/{uid}-{token}",
            "expires_in": 900,
        }

    def verify_webhook(self, body: bytes, signature: str | None) -> tuple[str, str]:
        return verify_webhook_signature(body, signature)


class RealIDVProvider:
    """Skeleton for the production vendor. Webhook verification is
    provider-agnostic HMAC; session creation is vendor-specific."""

    def __init__(self, vendor: str):
        self.vendor = vendor

    def create_session(self, uid: str) -> dict:
        raise NotImplementedError(
            f"IDV vendor '{self.vendor}' session creation is not wired yet"
        )

    def verify_webhook(self, body: bytes, signature: str | None) -> tuple[str, str]:
        return verify_webhook_signature(body, signature)


def get_idv_provider() -> IDVProvider:
    """Resolve the configured IDV provider — fail closed on the stub.

    ``IDV_PROVIDER=stub`` (the default when unset) is refused unless the
    explicit ``ENABLE_IDV_STUB=1`` opt-in is set; see
    ``app/config.py::validate_idv_config``. A production deploy that forgets
    ``IDV_PROVIDER`` therefore cannot instantiate the self-verification stub.
    """
    settings = get_settings()
    try:
        validate_idv_config(settings)
    except RuntimeError as exc:
        raise HTTPException(
            status_code=503,
            detail={"code": "idv_not_configured", "message": str(exc)},
        )
    if settings.idv_provider == "stub":
        return StubIDVProvider()
    return RealIDVProvider(settings.idv_provider)


class StubDecision(BaseModel):
    decision: str = Field(pattern="^(approved|rejected)$")


@router.post("/session")
def create_session(
    uid: str = Depends(get_current_uid),
    repo: UserRepo = Depends(get_user_repo),
    provider: IDVProvider = Depends(get_idv_provider),
) -> dict:
    try:
        session = provider.create_session(uid)
    except NotImplementedError as exc:
        raise HTTPException(
            status_code=501, detail={"code": "idv_not_configured", "message": str(exc)}
        )
    repo.set_idv_status(uid, "pending")
    return {"session_url": session["session_url"], "expires_in": session.get("expires_in", 900)}


@router.post("/webhook")
async def idv_webhook(
    request: Request,
    repo: UserRepo = Depends(get_user_repo),
    provider: IDVProvider = Depends(get_idv_provider),
) -> dict:
    """Auth-exempt (no user token possible); secured by HMAC signature only."""
    body = await request.body()
    signature = request.headers.get("x-idv-signature")
    try:
        uid, status = provider.verify_webhook(body, signature)
    except WebhookVerificationError as exc:
        logger.info("IDV webhook rejected: %s", exc)
        raise HTTPException(
            status_code=401,
            detail={"code": "invalid_webhook_signature", "message": "Webhook verification failed"},
        )
    repo.set_idv_status(uid, status)
    return {"ok": True, "uid": uid, "idv_status": status}


@router.post("/stub/decide")
def stub_decide(
    data: StubDecision,
    uid: str = Depends(get_current_uid),
    repo: UserRepo = Depends(get_user_repo),
) -> dict:
    """TEST-ONLY: acts as the stub vendor delivering a signed verdict webhook.

    Gated on the explicit ``ENABLE_IDV_STUB=1`` opt-in — never on the
    provider name. Without the opt-in this route is dead (404), so a deploy
    that forgot ``IDV_PROVIDER`` cannot be self-verified.
    """
    if not get_settings().idv_stub_enabled:
        raise HTTPException(
            status_code=404,
            detail={"code": "not_found", "message": "No route for POST /v1/idv/stub/decide"},
        )
    provider = get_idv_provider()
    body = build_verdict_payload(uid, data.decision)
    signature = sign_payload(body, _webhook_secret())
    target_uid, status = provider.verify_webhook(body, signature)
    repo.set_idv_status(target_uid, status)
    return {"uid": target_uid, "idv_status": status}
