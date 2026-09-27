"""API-021: feed & discovery ranking + cursor pagination."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from app.feed import score_listing


def _row(**kw):
    base = {
        "id": "x", "owner_uid": "alice", "type": "seedling", "photos": [],
        "variety": "tomato", "quantity": 1, "unit": "starts",
        "credit_cost": 1, "pickup_window": None,
        "expires_at": None, "geo_lat": None, "geo_lon": None,
        "spray_disclosure": "none", "status": "live", "created_at": None,
    }
    base.update(kw)
    return base


def _iso(dt):
    return dt.isoformat()


def test_urgency_outranks_recency():
    """Expires-soon-first: a listing with 1 day left outranks one listed an
    hour ago with 6 days left."""
    now = datetime.now(timezone.utc)
    soon = _row(id="soon", created_at=_iso(now - timedelta(hours=1)),
                expires_at=_iso(now + timedelta(days=1)))
    later = _row(id="later", created_at=_iso(now - timedelta(minutes=30)),
                 expires_at=_iso(now + timedelta(days=6)))
    assert score_listing(soon, now) > score_listing(later, now)


def test_freshness_breaks_urgency_ties():
    now = datetime.now(timezone.utc)
    exp = _iso(now + timedelta(days=3))
    old = _row(id="old", created_at=_iso(now - timedelta(days=2)), expires_at=exp)
    new = _row(id="new", created_at=_iso(now - timedelta(minutes=5)), expires_at=exp)
    assert score_listing(new, now) > score_listing(old, now)


def test_want_boost_lifts_matching_seedling():
    now = datetime.now(timezone.utc)
    exp = _iso(now + timedelta(days=3))
    plain = _row(id="plain", variety="basil", created_at=_iso(now), expires_at=exp)
    match = _row(id="match", variety="Cherokee Purple tomato",
                 created_at=_iso(now), expires_at=exp)
    assert score_listing(match, now, boost=1.0) > score_listing(plain, now)


def test_feed_returns_live_only_with_pagination(mem_listings, mock_verify, auth_headers):
    client, urepo, lrepo = mem_listings
    urepo.upsert("alice", display_name="Alice")
    now = datetime.now(timezone.utc)
    lrepo.create({"id": "live1", "owner_uid": "alice", "type": "seedling",
                  "photos": ["https://x/1.jpg"], "credit_cost": 1,
                  "spray_disclosure": "none", "status": "live",
                  "expires_at": _iso(now + timedelta(days=1))})
    lrepo.create({"id": "live2", "owner_uid": "alice", "type": "harvest",
                  "photos": ["https://x/2.jpg"], "credit_cost": 1,
                  "spray_disclosure": "none", "status": "live",
                  "expires_at": _iso(now + timedelta(days=5))})
    lrepo.create({"id": "draft1", "owner_uid": "alice", "type": "seedling",
                  "photos": ["https://x/3.jpg"], "credit_cost": 1,
                  "spray_disclosure": "none", "status": "draft",
                  "expires_at": _iso(now + timedelta(days=1))})

    r = client.get("/v1/feed?limit=1", headers=auth_headers)
    assert r.status_code == 200, r.text
    body = r.json()
    assert [i["id"] for i in body["items"]] == ["live1"]  # soonest expiry first
    assert body["next_cursor"]

    r2 = client.get(f"/v1/feed?limit=1&cursor={body['next_cursor']}", headers=auth_headers)
    body2 = r2.json()
    assert [i["id"] for i in body2["items"]] == ["live2"]
    assert body2["next_cursor"] is None


def test_feed_bad_cursor_is_400(mem_listings, mock_verify, auth_headers):
    client, _, _ = mem_listings
    r = client.get("/v1/feed?cursor=!!!not-base64!!!", headers=auth_headers)
    assert r.status_code == 400
    assert r.json()["code"] == "invalid_cursor"


def test_feed_geo_is_fuzzed_not_exact(mem_listings, mock_verify, auth_headers):
    client, urepo, lrepo = mem_listings
    urepo.upsert("alice", display_name="Alice")
    lrepo.create({"id": "g1", "owner_uid": "alice", "type": "seedling",
                  "photos": ["https://x/g.jpg"], "credit_cost": 1,
                  "spray_disclosure": "none", "status": "live",
                  "geo_lat": 33.4152, "geo_lon": -111.8315})
    r = client.get("/v1/feed", headers=auth_headers)
    item = r.json()["items"][0]
    assert (item["geo_lat"], item["geo_lon"]) != (33.4152, -111.8315)
