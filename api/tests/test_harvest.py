"""API-040: harvest pick events, remaining-qty tracking, auto-complete."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone


def _harvest_payload(**kw):
    base = {
        "type": "harvest",
        "photos": ["https://example.com/z.jpg"],
        "variety": "zucchini",
        "quantity": 20,
        "unit": "kg",
        "credit_cost": 1,
        "spray_disclosure": "unsprayed",
        "status": "live",
        "expires_at": (datetime.now(timezone.utc) + timedelta(days=2)).isoformat(),
    }
    base.update(kw)
    return base


def _make_harvest(mem_listings, mock_verify, auth_headers, **kw):
    client, urepo, lrepo, _, _ = mem_listings
    urepo.upsert("alice", display_name="Alice")
    r = client.post("/v1/listings", json=_harvest_payload(**kw), headers=auth_headers)
    assert r.status_code == 201, r.text
    assert r.json()["remaining_qty"] == kw.get("quantity", 20)
    return client, r.json()["id"]


def test_harvest_event_decrements_and_logs(mem_listings, mock_verify, auth_headers):
    client, lid = _make_harvest(mem_listings, mock_verify, auth_headers)
    r = client.post("/v1/harvest-events",
                    json={"listing_id": lid, "delta_kg": 5}, headers=auth_headers)
    assert r.status_code == 201, r.text
    body = r.json()
    assert body["remaining_kg"] == 15
    assert body["listing"]["remaining_qty"] == 15
    assert body["listing"]["status"] == "live"

    r = client.get(f"/v1/listings/{lid}/harvest-events", headers=auth_headers)
    events = r.json()["events"]
    assert len(events) == 1
    assert events[0]["delta_kg"] == 5
    assert events[0]["remaining_after"] == 15
    assert events[0]["recorder_uid"] == "alice"


def test_harvest_depleted_to_zero_completes(mem_listings, mock_verify, auth_headers):
    client, lid = _make_harvest(mem_listings, mock_verify, auth_headers, quantity=5)
    r = client.post("/v1/harvest-events",
                    json={"listing_id": lid, "delta_kg": 5}, headers=auth_headers)
    assert r.status_code == 201, r.text
    assert r.json()["remaining_kg"] == 0
    assert r.json()["listing"]["status"] == "completed"


def test_harvest_event_rejects_overpick(mem_listings, mock_verify, auth_headers):
    client, lid = _make_harvest(mem_listings, mock_verify, auth_headers, quantity=5)
    r = client.post("/v1/harvest-events",
                    json={"listing_id": lid, "delta_kg": 6}, headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "insufficient_quantity"


def test_harvest_event_owner_only_and_live_only(mem_listings, mock_verify, auth_headers):
    import app.auth as auth_mod

    client, lid = _make_harvest(mem_listings, mock_verify, auth_headers)

    # A different user cannot record picks on alice's harvest.
    orig = auth_mod.verify_id_token
    auth_mod.verify_id_token = lambda t: {"uid": "mallory"} if t == "good-token" else orig(t)
    try:
        r = client.post("/v1/harvest-events",
                        json={"listing_id": lid, "delta_kg": 1}, headers=auth_headers)
        assert r.status_code == 403
    finally:
        auth_mod.verify_id_token = orig

    # Owner cancels -> no longer live -> 422.
    r = client.post(f"/v1/listings/{lid}/cancel", headers=auth_headers)
    assert r.status_code == 200
    r = client.post("/v1/harvest-events",
                    json={"listing_id": lid, "delta_kg": 1}, headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "listing_not_live"


def test_harvest_event_rejects_non_harvest(mem_listings, mock_verify, auth_headers):
    client, urepo, _, _, _ = mem_listings
    urepo.upsert("alice", display_name="Alice")
    payload = _harvest_payload(type="seedling")
    r = client.post("/v1/listings", json=payload, headers=auth_headers)
    lid = r.json()["id"]
    r = client.post("/v1/harvest-events",
                    json={"listing_id": lid, "delta_kg": 1}, headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "not_harvest_listing"


def test_harvest_event_unknown_listing_is_404(mem_listings, mock_verify, auth_headers):
    client, *_ = mem_listings
    r = client.post("/v1/harvest-events",
                    json={"listing_id": "nope", "delta_kg": 1}, headers=auth_headers)
    assert r.status_code == 404


def test_harvest_without_quantity_rejected_clearly(mem_listings, mock_verify, auth_headers):
    client, urepo, _, _, _ = mem_listings
    urepo.upsert("alice", display_name="Alice")
    payload = _harvest_payload()
    del payload["quantity"]
    r = client.post("/v1/listings", json=payload, headers=auth_headers)
    lid = r.json()["id"]
    r = client.post("/v1/harvest-events",
                    json={"listing_id": lid, "delta_kg": 1}, headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "quantity_not_tracked"
