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


@pytest.fixture()
def mem_users(client, monkeypatch):
    """Override the user repo with an in-memory one. Returns (client, repo)."""
    from app import users as users_mod

    repo = users_mod.MemoryUserRepo()
    client.app.dependency_overrides[users_mod.get_user_repo] = lambda: repo
    return client, repo
