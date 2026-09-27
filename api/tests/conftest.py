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
