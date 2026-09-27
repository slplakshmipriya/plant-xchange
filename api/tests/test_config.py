"""SEC-001: centralized env config; no secrets in code or defaults."""
from __future__ import annotations

from app.config import get_settings


def test_defaults_are_safe(monkeypatch):
    for var in ("DATABASE_URL", "FIREBASE_PROJECT_ID", "RATE_LIMIT_PER_MIN",
                "LOG_LEVEL", "PORT", "STRIPE_SECRET_KEY"):
        monkeypatch.delenv(var, raising=False)
    s = get_settings()
    assert s.database_url is None
    assert s.firebase_project_id is None
    assert s.rate_limit_per_min == 120
    assert s.log_level == "INFO"
    assert s.port == 8080


def test_reads_environment(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", "postgresql://u:p@host/db?sslmode=require")
    monkeypatch.setenv("FIREBASE_PROJECT_ID", "gardenswap-dev")
    monkeypatch.setenv("RATE_LIMIT_PER_MIN", "42")
    monkeypatch.setenv("LOG_LEVEL", "debug")
    s = get_settings()
    assert s.database_url.startswith("postgresql://")
    assert s.firebase_project_id == "gardenswap-dev"
    assert s.rate_limit_per_min == 42
    assert s.log_level == "DEBUG"


def test_invalid_rate_limit_falls_back(monkeypatch):
    monkeypatch.setenv("RATE_LIMIT_PER_MIN", "not-a-number")
    assert get_settings().rate_limit_per_min == 120


def test_env_example_has_no_secret_values():
    """Secret-like keys must ship empty; non-secret tunables may have defaults."""
    from pathlib import Path

    example = Path(__file__).resolve().parent.parent / ".env.example"
    assert example.is_file()
    secret_markers = ("SECRET", "PASSWORD", "TOKEN", "PRIVATE", "URL", "KEY")
    for line in example.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip().upper()
        if any(m in key for m in secret_markers):
            assert value.strip() == "", f"{key} ships with a value in .env.example"
