"""Shared pytest fixtures for the GardenSwap API test suite."""
from __future__ import annotations

import os

import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(monkeypatch) -> TestClient:
    """Fresh app instance per test; DATABASE_URL unset so migrations are skipped."""
    monkeypatch.delenv("DATABASE_URL", raising=False)
    monkeypatch.setenv("RATE_LIMIT_PER_MIN", "1000000")  # don't trip the limiter
    from app.main import create_app

    return TestClient(create_app())


@pytest.fixture()
def mock_verify(monkeypatch):
    """verify_id_token: 'good-token' -> uid alice (+ phone claim), else raises."""
    import app.auth as auth_mod

    def fake(token: str) -> dict:
        if token == "good-token":
            return {"uid": "alice", "phone_number": "+15551234567"}
        if token == "nophone-token":
            return {"uid": "nophone"}
        raise ValueError("bad token")

    monkeypatch.setattr(auth_mod, "verify_id_token", fake)


@pytest.fixture()
def auth_headers() -> dict:
    return {"Authorization": "Bearer good-token"}


def wire_credit_repo(client):
    """Override the credit-ledger repo with an in-memory one.

    Required by any fixture whose tests hit POST /v1/users or
    POST /v1/auth/verify (API-060 grants starter credits there).
    Returns the repo so tests can seed/inspect it.
    """
    from app import credits as credits_mod

    crepo = credits_mod.MemoryCreditRepo()
    client.app.dependency_overrides[credits_mod.get_credit_repo] = lambda: crepo
    return crepo


@pytest.fixture()
def mem_users(client, monkeypatch):
    """Override the user repo with an in-memory one. Returns (client, repo)."""
    from app import users as users_mod

    repo = users_mod.MemoryUserRepo()
    client.app.dependency_overrides[users_mod.get_user_repo] = lambda: repo
    wire_credit_repo(client)
    return client, repo


@pytest.fixture()
def mem_listings(client, monkeypatch):
    """In-memory user + listing + want + notification + sitter repos.
    Returns (client, user_repo, listing_repo, want_repo, notify_repo)."""
    from app import listings as listings_mod
    from app import notify as notify_mod
    from app import sitter as sitter_mod
    from app import users as users_mod
    from app import wantlist as wantlist_mod

    urepo = users_mod.MemoryUserRepo()
    lrepo = listings_mod.MemoryListingRepo()
    wrepo = wantlist_mod.MemoryWantRepo()
    nrepo = notify_mod.MemoryNotificationRepo()
    client.app.dependency_overrides[users_mod.get_user_repo] = lambda: urepo
    client.app.dependency_overrides[listings_mod.get_listing_repo] = lambda: lrepo
    client.app.dependency_overrides[wantlist_mod.get_want_repo] = lambda: wrepo
    client.app.dependency_overrides[notify_mod.get_notification_repo] = lambda: nrepo
    client.app.dependency_overrides[sitter_mod.get_sitter_repo] = lambda: sitter_mod.MemorySitterRepo()
    wire_credit_repo(client)
    return client, urepo, lrepo, wrepo, nrepo


@pytest.fixture()
def mem_notify(client, monkeypatch):
    """In-memory notification repo. Returns (client, repo)."""
    from app import notify as notify_mod

    repo = notify_mod.MemoryNotificationRepo()
    client.app.dependency_overrides[notify_mod.get_notification_repo] = lambda: repo
    return client, repo
