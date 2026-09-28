"""Phone verification (API-011).

Firebase Auth is the verifier: the middleware has already validated the ID
token, so the ``phone_number`` claim is trustworthy. This endpoint binds that
verified phone number to the caller's uid:

- phone numbers are stored only as a domain-separated SHA-256 hash.
- one account per phone_hash: a hash claimed by another uid is a safe 409
  (the response never reveals the other uid).
- ``X-Device-Fingerprint`` header is stored when present.
"""

from __future__ import annotations

import hashlib

from fastapi import APIRouter, Depends, HTTPException, Request

from .auth import get_current_uid
from .credits import CreditRepo, ensure_starter_credits, get_credit_repo
from .users import PhoneInUseError, UserRepo, get_user_repo

router = APIRouter(tags=["auth"])

PHONE_HASH_DOMAIN = "gs-phone-v1:"


def phone_hash(phone_number: str) -> str:
    """Domain-separated SHA-256 of the E.164 phone number. Not reversible."""
    return hashlib.sha256((PHONE_HASH_DOMAIN + phone_number).encode("utf-8")).hexdigest()


@router.post("/v1/auth/verify")
def verify_phone(
    request: Request,
    uid: str = Depends(get_current_uid),
    repo: UserRepo = Depends(get_user_repo),
    credit_repo: CreditRepo = Depends(get_credit_repo),
) -> dict:
    claims = getattr(request.state, "claims", None) or {}
    phone = claims.get("phone_number")
    if not phone:
        raise HTTPException(
            status_code=400,
            detail={
                "code": "phone_verification_required",
                "message": "This account has no verified phone number. Sign in with phone auth first.",
            },
        )
    fingerprint = request.headers.get("x-device-fingerprint")
    try:
        repo.upsert(uid, phone_hash=phone_hash(phone), device_fingerprint=fingerprint)
    except PhoneInUseError:
        # Safe error: do not reveal which uid holds the number.
        raise HTTPException(
            status_code=409,
            detail={
                "code": "phone_in_use",
                "message": "This phone number is already registered to another account.",
            },
        )
    # Idempotent starter-credit bootstrap (a user row may first be created here).
    ensure_starter_credits(uid, credit_repo)
    return {"uid": uid, "verified": True}
