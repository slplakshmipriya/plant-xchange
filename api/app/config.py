"""Central configuration (SEC-001).

Every setting comes from the environment. Nothing is read from files, and no
default in this module is a real credential. ``get_settings()`` reads the
environment fresh on each call so tests can monkeypatch env vars freely.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


def _int(name: str, default: int) -> int:
    try:
        return max(1, int(os.environ.get(name, default)))
    except (ValueError, TypeError):
        return default


@dataclass(frozen=True)
class Settings:
    database_url: str | None       # Neon Postgres connection string
    firebase_project_id: str | None
    rate_limit_per_min: int        # per-instance token bucket (approximate)
    log_level: str
    port: int                      # honored from $PORT on Cloud Run


def get_settings() -> Settings:
    return Settings(
        database_url=os.environ.get("DATABASE_URL"),
        firebase_project_id=os.environ.get("FIREBASE_PROJECT_ID"),
        rate_limit_per_min=_int("RATE_LIMIT_PER_MIN", 120),
        log_level=os.environ.get("LOG_LEVEL", "INFO").upper(),
        port=_int("PORT", 8080),
    )
