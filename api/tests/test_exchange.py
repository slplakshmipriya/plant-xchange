"""API-060: credit ledger, claim, two-party exchange confirmation, wallet."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest


@pytest.fixture()
def mem_exchange(client, monkeypatch):
    from app import exchange as exchange_mod
    from app import listings as listings_mod
    from app import notify as notify_mod
    from app import users as users_mod
    from app import wantlist as wantlist_mod
    from conftest import wire_credit_repo
    import app.auth as auth_mod

    urepo = users_mod.MemoryUserRepo()
    lrepo = listings_mod.MemoryListingRepo()
    crepo = wire_credit_repo(client)
    wrepo = wantlist_mod.MemoryWantRepo()
    nrepo = notify_mod.MemoryNotificationRepo()
    client.app.dependency_overrides[users_mod.get_user_repo] = lambda: urepo
    client.app.dependency_overrides[listings_mod.get_listing_repo] = lambda: lrepo
    client.app.dependency_overrides[wantlist_mod.get_want_repo] = lambda: wrepo
    client.app.dependency_overrides[notify_mod.get_notification_repo] = lambda: nrepo

    def fake(token: str) -> dict:
        if token == "good-token":
            return {"uid": "alice", "phone_number": "+15551234567"}
        if token == "bob-token":
            return {"uid": "bob"}
        if token == "mallory-token":
            return {"uid": "mallory"}
        raise ValueError("bad token")

    monkeypatch.setattr(auth_mod, "verify_id_token", fake)
    assert exchange_mod is not None  # router registered on the app
    return client, urepo, lrepo, crepo


ALICE = {"Authorization": "Bearer good-token"}
BOB = {"Authorization": "Bearer bob-token"}
MALLORY = {"Authorization": "Bearer mallory-token"}


def _profile(client, headers, name):
    r = client.post("/v1/users", json={"display_name": name}, headers=headers)
    assert r.status_code == 200, r.text


def _listing_payload(cost=2, **kw):
    base = {
        "type": "seedling",
        "photos": ["https://example.com/t.jpg"],
        "variety": "Cherokee Purple tomato",
        "quantity": 4,
        "unit": "starts",
        "credit_cost": cost,
        "spray_disclosure": "unsprayed",
        "status": "live",
        "expires_at": (datetime.now(timezone.utc) + timedelta(days=30)).isoformat(),
    }
    base.update(kw)
    return base


def _make_listing(client, cost=2):
    _profile(client, ALICE, "Alice")
    r = client.post("/v1/listings", json=_listing_payload(cost), headers=ALICE)
    assert r.status_code == 201, r.text
    return r.json()["id"]


def test_starter_credits_on_profile_creation(mem_exchange):
    client, _, _, crepo = mem_exchange
    _profile(client, ALICE, "Alice")
    body = client.get("/v1/wallet", headers=ALICE).json()
    assert body["balance"] == 3
    assert [e["reason"] for e in body["entries"]] == ["starter"]
    assert body["entries"][0]["delta"] == 3
    # Idempotent: updating the profile does not re-grant.
    _profile(client, ALICE, "Alice")
    assert client.get("/v1/wallet", headers=ALICE).json()["balance"] == 3


def test_starter_credits_on_phone_verify_path(mem_exchange):
    client, _, _, _ = mem_exchange
    r = client.post("/v1/auth/verify", headers=ALICE)
    assert r.status_code == 200, r.text
    # Verify-first user still gets exactly one bootstrap grant.
    r = client.post("/v1/auth/verify", headers=ALICE)
    assert client.get("/v1/wallet", headers=ALICE).json()["balance"] == 3


def test_wallet_has_no_pii(mem_exchange):
    client, _, _, _ = mem_exchange
    _profile(client, ALICE, "Alice")
    body = client.get("/v1/wallet", headers=ALICE).json()
    assert set(body) == {"uid", "balance", "entries"}
    for e in body["entries"]:
        assert set(e) == {"id", "delta", "reason", "ref_id", "created_at"}


def test_claim_and_two_party_confirm_moves_credits(mem_exchange):
    client, _, _, _ = mem_exchange
    lid = _make_listing(client, cost=2)
    _profile(client, BOB, "Bob")

    r = client.post(f"/v1/listings/{lid}/claim", headers=BOB)
    assert r.status_code == 200, r.text
    assert r.json()["status"] == "claimed"
    assert r.json()["claimer_uid"] == "bob"

    # First confirmation: no movement yet.
    r = client.post("/v1/exchange/confirm",
                    json={"listing_id": lid, "idempotency_key": "k1"}, headers=BOB)
    assert r.status_code == 200, r.text
    assert r.json()["status"] == "claimed"
    assert r.json()["confirmed_by"] == ["bob"]
    assert client.get("/v1/wallet", headers=BOB).json()["balance"] == 3

    # Second confirmation: both in -> credits move, listing completed.
    r = client.post("/v1/exchange/confirm",
                    json={"listing_id": lid, "idempotency_key": "k1"}, headers=ALICE)
    assert r.json()["status"] == "completed"
    assert sorted(r.json()["confirmed_by"]) == ["alice", "bob"]

    assert client.get("/v1/wallet", headers=BOB).json()["balance"] == 1
    assert client.get("/v1/wallet", headers=ALICE).json()["balance"] == 5
    bob_reasons = [e["reason"] for e in client.get("/v1/wallet", headers=BOB).json()["entries"]]
    alice_reasons = [e["reason"] for e in client.get("/v1/wallet", headers=ALICE).json()["entries"]]
    assert bob_reasons == ["starter", "exchange_spend"]
    assert alice_reasons == ["starter", "exchange_earn"]


def test_confirm_idempotent_on_key_and_after_completion(mem_exchange):
    client, _, _, _ = mem_exchange
    lid = _make_listing(client, cost=1)
    _profile(client, BOB, "Bob")
    client.post(f"/v1/listings/{lid}/claim", headers=BOB)
    client.post("/v1/exchange/confirm",
                json={"listing_id": lid, "idempotency_key": "k9"}, headers=BOB)
    client.post("/v1/exchange/confirm",
                json={"listing_id": lid, "idempotency_key": "k9"}, headers=ALICE)

    # Same key again: already_confirmed, balances untouched.
    r = client.post("/v1/exchange/confirm",
                    json={"listing_id": lid, "idempotency_key": "k9"}, headers=ALICE)
    assert r.json()["status"] == "already_confirmed"
    assert client.get("/v1/wallet", headers=BOB).json()["balance"] == 2
    assert client.get("/v1/wallet", headers=ALICE).json()["balance"] == 4

    # Confirm after completion without a key: safe no-op.
    r = client.post("/v1/exchange/confirm", json={"listing_id": lid}, headers=BOB)
    assert r.json()["status"] == "completed"
    assert client.get("/v1/wallet", headers=BOB).json()["balance"] == 2


def test_claim_rules(mem_exchange):
    client, _, _, _ = mem_exchange
    lid = _make_listing(client, cost=2)

    # Owner cannot claim their own listing.
    r = client.post(f"/v1/listings/{lid}/claim", headers=ALICE)
    assert r.status_code == 422
    assert r.json()["code"] == "cannot_claim_own"

    # Claimer needs a profile.
    r = client.post(f"/v1/listings/{lid}/claim", headers=BOB)
    assert r.status_code == 400
    assert r.json()["code"] == "profile_required"
    _profile(client, BOB, "Bob")

    # Successful claim by a funded non-owner.
    r = client.post(f"/v1/listings/{lid}/claim", headers=BOB)
    assert r.status_code == 200
    assert r.json()["claimer_uid"] == "bob"

    # Cannot claim a non-live listing (already claimed).
    _profile(client, MALLORY, "Mallory")
    r = client.post(f"/v1/listings/{lid}/claim", headers=MALLORY)
    assert r.status_code == 422
    assert r.json()["code"] == "listing_not_live"

    # Insufficient credits: spend bob's 3 starter credits on a cost-3
    # exchange, then he cannot afford another claim.
    lid3 = _make_listing(client, cost=3)
    client.post(f"/v1/listings/{lid3}/claim", headers=BOB)
    client.post("/v1/exchange/confirm", json={"listing_id": lid3}, headers=BOB)
    client.post("/v1/exchange/confirm", json={"listing_id": lid3}, headers=ALICE)
    assert client.get("/v1/wallet", headers=BOB).json()["balance"] == 0
    lid4 = _make_listing(client, cost=1)
    r = client.post(f"/v1/listings/{lid4}/claim", headers=BOB)
    assert r.status_code == 422
    assert r.json()["code"] == "insufficient_credits"


def test_confirm_rules(mem_exchange):
    client, _, _, _ = mem_exchange
    lid = _make_listing(client, cost=2)
    _profile(client, BOB, "Bob")

    # Nothing to confirm before a claim exists.
    r = client.post("/v1/exchange/confirm", json={"listing_id": lid}, headers=ALICE)
    assert r.status_code == 422
    assert r.json()["code"] == "not_claimed"

    client.post(f"/v1/listings/{lid}/claim", headers=BOB)

    # A stranger cannot confirm.
    _profile(client, MALLORY, "Mallory")
    r = client.post("/v1/exchange/confirm", json={"listing_id": lid}, headers=MALLORY)
    assert r.status_code == 403
    assert r.json()["code"] == "not_a_party"

    # Unknown listing.
    r = client.post("/v1/exchange/confirm",
                    json={"listing_id": "00000000-0000-0000-0000-000000000000"},
                    headers=ALICE)
    assert r.status_code == 404
