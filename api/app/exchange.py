"""Claim + two-party exchange confirmation + wallet (API-060).

- ``POST /v1/listings/{id}/claim``: any profile-holding non-owner claims a live
  listing; needs ``balance >= credit_cost`` (negative balances are not allowed).
  Sets ``claimer_uid``, transitions ``live -> claimed``.
- ``POST /v1/exchange/confirm``: giver and claimer each confirm; credits move
  exactly once when both confirmations are in (claimer ``-cost``,
  owner ``+cost``), transitioning ``claimed -> completed``.
- Idempotency: client ``idempotency_key`` on confirm; repeats return the prior
  result without moving credits twice. The ``(listing_id, uid)`` PK makes
  double confirmation a no-op, and the status gate makes double completion
  impossible.
- ``GET /v1/wallet``: derived balance + full append-only history.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from .auth import get_current_uid
from .credits import CreditRepo, get_credit_repo
from .listings import ListingRepo, can_transition, get_listing_repo, public_listing
from .users import UserRepo, get_user_repo

router = APIRouter(prefix="/v1", tags=["credits"])


def _serialize_entry(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": str(row["id"]),
        "delta": row["delta"],
        "reason": row["reason"],
        "ref_id": row.get("ref_id"),
        "created_at": row.get("created_at"),
    }


class ConfirmIn(BaseModel):
    listing_id: str = Field(min_length=1)
    idempotency_key: str | None = Field(default=None, max_length=128)


@router.post("/listings/{listing_id}/claim", status_code=200, tags=["listings"])
def claim_listing(
    listing_id: str,
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
    user_repo: UserRepo = Depends(get_user_repo),
    credit_repo: CreditRepo = Depends(get_credit_repo),
) -> dict[str, Any]:
    """Claim a live listing. Not the owner; needs balance >= credit_cost."""
    row = repo.get(listing_id)
    if row is None:
        raise HTTPException(404, {"code": "listing_not_found", "message": "No such listing"})
    if row["owner_uid"] == uid:
        raise HTTPException(422, {"code": "cannot_claim_own",
                                  "message": "You cannot claim your own listing"})
    if row["status"] != "live":
        raise HTTPException(422, {"code": "listing_not_live",
                                  "message": f"Cannot claim a '{row['status']}' listing"})
    if user_repo.get(uid) is None:
        raise HTTPException(400, {"code": "profile_required",
                                  "message": "Create a profile (POST /v1/users) before claiming"})
    if credit_repo.balance(uid) < row["credit_cost"]:
        raise HTTPException(422, {"code": "insufficient_credits",
                                  "message": "Not enough credits — give before you claim"})
    # Atomic: concurrent claimants cannot both win; the loser gets None.
    updated = repo.claim(listing_id, uid)
    if updated is None:
        raise HTTPException(422, {"code": "listing_not_live",
                                  "message": "Someone just claimed this listing"})
    return public_listing(updated)


@router.post("/exchange/confirm", tags=["credits"])
def confirm_exchange(
    data: ConfirmIn,
    uid: str = Depends(get_current_uid),
    repo: ListingRepo = Depends(get_listing_repo),
    credit_repo: CreditRepo = Depends(get_credit_repo),
) -> dict[str, Any]:
    """Both parties confirm; credits move exactly once when both are in."""
    if data.idempotency_key:
        # Entries are stored under "<key>:spend" / "<key>:earn".
        prior = credit_repo.find_by_idempotency_key(f"{data.idempotency_key}:spend")
        if prior:
            row = repo.get(data.listing_id)
            return {"status": "already_confirmed",
                    "listing": public_listing(row) if row else None}
    row = repo.get(data.listing_id)
    if row is None:
        raise HTTPException(404, {"code": "listing_not_found", "message": "No such listing"})
    if row["status"] == "completed" and row.get("claimer_uid"):
        # Repeat confirm after completion: safe no-op, not an error.
        return {"status": "completed",
                "confirmed_by": sorted(credit_repo.confirmations(data.listing_id)),
                "listing": public_listing(row)}
    if row["status"] != "claimed" or not row.get("claimer_uid"):
        raise HTTPException(422, {"code": "not_claimed",
                                  "message": "Nothing to confirm — listing is not claimed"})
    if uid not in (row["owner_uid"], row["claimer_uid"]):
        raise HTTPException(403, {"code": "not_a_party",
                                  "message": "Only the giver and claimer can confirm"})
    credit_repo.add_confirmation(data.listing_id, uid)
    confirmed = set(credit_repo.confirmations(data.listing_id))
    if {row["owner_uid"], row["claimer_uid"]} <= confirmed and can_transition("claimed", "completed"):
        cost = row["credit_cost"]
        # Balance can change between claim and confirm — recheck so a confirm
        # can never drive a balance negative.
        if credit_repo.balance(row["claimer_uid"]) < cost:
            raise HTTPException(422, {"code": "insufficient_credits",
                                      "message": "Claimer no longer has enough credits"})
        # Deterministic server-side keys: exactly-once credit moves even when
        # the client sends no idempotency key and two confirms race. Entries
        # land before the status flip so a crash mid-flight is recoverable by
        # retry (re-adds are no-ops, then the flip completes).
        base_key = data.idempotency_key or f"exchange:{data.listing_id}"
        credit_repo.add_entry(row["claimer_uid"], -cost, "exchange_spend",
                              ref_id=data.listing_id,
                              idempotency_key=f"{base_key}:spend")
        credit_repo.add_entry(row["owner_uid"], cost, "exchange_earn",
                              ref_id=data.listing_id,
                              idempotency_key=f"{base_key}:earn")
        completed = repo.complete_if_claimed(data.listing_id)
        if completed is not None:
            row = completed
    return {
        "status": row["status"],
        "confirmed_by": sorted(confirmed),
        "listing": public_listing(row),
    }


@router.get("/wallet", tags=["credits"])
def get_wallet(
    uid: str = Depends(get_current_uid),
    credit_repo: CreditRepo = Depends(get_credit_repo),
) -> dict[str, Any]:
    """Derived balance + full append-only history. No PII in entries."""
    return {
        "uid": uid,
        "balance": credit_repo.balance(uid),
        "entries": [_serialize_entry(e) for e in credit_repo.entries(uid)],
    }
