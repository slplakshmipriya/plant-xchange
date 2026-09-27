"""API-020: listing CRUD, lifecycle state machine, geo fuzzing, expiry sweep."""
from __future__ import annotations

import math
import random
from datetime import datetime, timedelta, timezone

import pytest

from app.listings import TRANSITIONS, can_transition, fuzz_location


def _listing_payload(**kw):
    base = {
        "type": "seedling",
        "photos": ["https://example.com/a.jpg"],
        "variety": "Cherokee Purple tomato",
        "quantity": 6,
        "unit": "starts",
        "credit_cost": 2,
        "spray_disclosure": "Neem oil only, last applied 3 weeks ago.",
        "status": "live",
        "geo_lat": 33.4152,
        "geo_lon": -111.8315,
    }
    base.update(kw)
    return base


@pytest.fixture()
def alice_profile(mem_listings, mock_verify, auth_headers):
    client, urepo, lrepo = mem_listings
    urepo.upsert("alice", display_name="Alice")
    return client, urepo, lrepo


# ------------------------------------------------ state machine table test

VALID = [
    ("draft", "live"), ("draft", "cancelled"),
    ("live", "claimed"), ("live", "expired"), ("live", "cancelled"),
    ("claimed", "completed"), ("claimed", "cancelled"), ("claimed", "live"),
]
INVALID = [
    ("draft", "claimed"), ("draft", "completed"), ("draft", "expired"),
    ("live", "draft"), ("live", "completed"),
    ("claimed", "draft"), ("claimed", "expired"),
    ("completed", "live"), ("completed", "cancelled"),
    ("expired", "live"), ("cancelled", "live"),
]


@pytest.mark.parametrize("frm,to", VALID)
def test_valid_transitions(frm, to):
    assert can_transition(frm, to), f"{frm}->{to} should be legal"


@pytest.mark.parametrize("frm,to", INVALID)
def test_invalid_transitions(frm, to):
    assert not can_transition(frm, to), f"{frm}->{to} should be illegal"


def test_transition_table_is_exhaustive():
    states = {"draft", "live", "claimed", "completed", "expired", "cancelled"}
    assert set(TRANSITIONS) == states
    for targets in TRANSITIONS.values():
        assert targets <= states


# ------------------------------------------------ CRUD

def test_create_requires_photo(alice_profile, mock_verify, auth_headers):
    client, _, _ = alice_profile
    r = client.post("/v1/listings", json=_listing_payload(photos=[]), headers=auth_headers)
    assert r.status_code == 422  # pydantic min_length


def test_create_requires_spray_disclosure(alice_profile, mock_verify, auth_headers):
    client, _, _ = alice_profile
    payload = _listing_payload()
    del payload["spray_disclosure"]
    r = client.post("/v1/listings", json=payload, headers=auth_headers)
    assert r.status_code == 422


def test_create_rejects_bad_credit_cost(alice_profile, mock_verify, auth_headers):
    client, _, _ = alice_profile
    r = client.post("/v1/listings", json=_listing_payload(credit_cost=5), headers=auth_headers)
    assert r.status_code == 422


def test_create_requires_profile(mem_listings, mock_verify, auth_headers):
    client, _, _ = mem_listings  # no profile for alice
    r = client.post("/v1/listings", json=_listing_payload(), headers=auth_headers)
    assert r.status_code == 400
    assert r.json()["code"] == "profile_required"


def test_create_and_get_roundtrip(alice_profile, mock_verify, auth_headers):
    client, _, _ = alice_profile
    r = client.post("/v1/listings", json=_listing_payload(), headers=auth_headers)
    assert r.status_code == 201
    created = r.json()
    assert created["status"] == "live"
    assert created["owner_uid"] == "alice"

    r = client.get(f"/v1/listings/{created['id']}", headers=auth_headers)
    assert r.status_code == 200
    assert r.json()["id"] == created["id"]


def test_detail_fuzzes_geo(alice_profile, mock_verify, auth_headers):
    client, _, lrepo = alice_profile
    r = client.post("/v1/listings", json=_listing_payload(), headers=auth_headers)
    body = r.json()
    # fuzzed point must differ from the true stored coordinates
    assert (body["geo_lat"], body["geo_lon"]) != (33.4152, -111.8315)
    # ...but stay within ~0.6 mi (haversine)
    def haversine_mi(a, b, c, d):
        p = math.pi / 180
        h = math.sin((c - a) * p / 2) ** 2 + math.cos(a * p) * math.cos(c * p) * math.sin((d - b) * p / 2) ** 2
        return 2 * 3959 * math.asin(math.sqrt(h))
    dist = haversine_mi(33.4152, -111.8315, body["geo_lat"], body["geo_lon"])
    assert 0 < dist <= 0.6


def test_fuzz_location_deterministic_with_seeded_rng():
    a = fuzz_location(33.4, -111.8, random.Random(42))
    b = fuzz_location(33.4, -111.8, random.Random(42))
    assert a == b
    c = fuzz_location(33.4, -111.8, random.Random(43))
    assert a != c


def test_patch_illegal_transition_rejected(alice_profile, mock_verify, auth_headers):
    client, _, _ = alice_profile
    created = client.post("/v1/listings", json=_listing_payload(), headers=auth_headers).json()
    r = client.patch(f"/v1/listings/{created['id']}", json={"status": "draft"}, headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "invalid_transition"


def test_patch_legal_transition(alice_profile, mock_verify, auth_headers):
    client, _, _ = alice_profile
    created = client.post("/v1/listings", json=_listing_payload(), headers=auth_headers).json()
    r = client.patch(f"/v1/listings/{created['id']}", json={"status": "claimed"}, headers=auth_headers)
    assert r.status_code == 200
    assert r.json()["status"] == "claimed"


def test_patch_locked_in_terminal_state(alice_profile, mock_verify, auth_headers):
    client, _, _ = alice_profile
    created = client.post("/v1/listings", json=_listing_payload(), headers=auth_headers).json()
    client.post(f"/v1/listings/{created['id']}/cancel", headers=auth_headers)
    r = client.patch(f"/v1/listings/{created['id']}", json={"variety": "x"}, headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "listing_locked"


def test_non_owner_cannot_patch(alice_profile, mock_verify):
    client, _, _ = alice_profile
    created = client.post("/v1/listings", json=_listing_payload(),
                          headers={"Authorization": "Bearer good-token"}).json()
    # nophone-token is a different uid
    r = client.patch(f"/v1/listings/{created['id']}", json={"variety": "x"},
                     headers={"Authorization": "Bearer nophone-token"})
    assert r.status_code == 403


def test_cancel_flow(alice_profile, mock_verify, auth_headers):
    client, _, _ = alice_profile
    created = client.post("/v1/listings", json=_listing_payload(), headers=auth_headers).json()
    r = client.post(f"/v1/listings/{created['id']}/cancel", headers=auth_headers)
    assert r.status_code == 200
    assert r.json()["status"] == "cancelled"
    # second cancel is an illegal transition
    r = client.post(f"/v1/listings/{created['id']}/cancel", headers=auth_headers)
    assert r.status_code == 422


def test_get_unknown_listing_404(alice_profile, mock_verify, auth_headers):
    client, _, _ = alice_profile
    r = client.get("/v1/listings/00000000-0000-0000-0000-000000000000", headers=auth_headers)
    assert r.status_code == 404


# ------------------------------------------------ sweep

def _sweep(client, secret="s3cret"):
    return client.post("/v1/internal/sweep", headers={"X-Sweep-Secret": secret})


def test_sweep_requires_secret_config(monkeypatch, alice_profile):
    client, _, _ = alice_profile
    monkeypatch.delenv("SWEEP_SECRET", raising=False)
    r = client.post("/v1/internal/sweep", headers={"X-Sweep-Secret": "x"})
    assert r.status_code == 503
    assert r.json()["code"] == "sweep_not_configured"


def test_sweep_rejects_bad_secret(monkeypatch, alice_profile):
    client, _, _ = alice_profile
    monkeypatch.setenv("SWEEP_SECRET", "s3cret")
    r = _sweep(client, secret="wrong")
    assert r.status_code == 401


def test_sweep_is_auth_exempt_but_secret_gated(monkeypatch, alice_profile):
    client, _, _ = alice_profile  # no Authorization header sent
    monkeypatch.setenv("SWEEP_SECRET", "s3cret")
    r = client.post("/v1/internal/sweep", headers={"X-Sweep-Secret": "s3cret"})
    assert r.status_code == 200  # no 401 despite no bearer token


def test_sweep_expires_past_due_and_is_idempotent(monkeypatch, alice_profile, mock_verify, auth_headers):
    client, _, lrepo = alice_profile
    monkeypatch.setenv("SWEEP_SECRET", "s3cret")
    past = (datetime.now(timezone.utc) - timedelta(hours=1)).isoformat()
    future = (datetime.now(timezone.utc) + timedelta(days=1)).isoformat()
    old = lrepo.create({"id": "a1", "owner_uid": "alice", "type": "seedling",
                        "photos": ["https://x/y.jpg"], "credit_cost": 1,
                        "spray_disclosure": "none", "status": "live", "expires_at": past})
    new = lrepo.create({"id": "a2", "owner_uid": "alice", "type": "seedling",
                        "photos": ["https://x/y.jpg"], "credit_cost": 1,
                        "spray_disclosure": "none", "status": "live", "expires_at": future})
    assert _sweep(client).json() == {"expired": 1}
    assert lrepo.get("a1")["status"] == "expired"
    assert lrepo.get("a2")["status"] == "live"
    assert _sweep(client).json() == {"expired": 0}  # idempotent


def test_naive_datetimes_are_treated_as_utc(alice_profile, mock_verify, auth_headers):
    """Review: naive ISO datetimes must not 500 the naive/aware comparison."""
    client, _, _ = alice_profile
    naive_future = (datetime.now(timezone.utc) + timedelta(days=1)).replace(tzinfo=None).isoformat()
    r = client.post("/v1/listings", json=_listing_payload(expires_at=naive_future),
                    headers=auth_headers)
    assert r.status_code == 201, r.text
    assert r.json()["expires_at"].endswith("+00:00")

    naive_past = (datetime.now(timezone.utc) - timedelta(hours=1)).replace(tzinfo=None).isoformat()
    r = client.post("/v1/listings", json=_listing_payload(expires_at=naive_past),
                    headers=auth_headers)
    assert r.status_code == 400  # invalid_expiry, not a 500
    assert r.json()["code"] == "invalid_expiry"
