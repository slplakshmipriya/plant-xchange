"""API-001: healthz + migration runner unit tests (no Postgres required)."""
from __future__ import annotations

import os
from pathlib import Path

import pytest

from app import db


def test_healthz_ok(client):
    r = client.get("/healthz")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["version"]


def test_healthz_needs_no_database(client, monkeypatch):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    assert client.get("/healthz").status_code == 200


def _write(tmp: Path, name: str, sql: str = "SELECT 1;") -> None:
    (tmp / name).write_text(sql, encoding="utf-8")


def test_discover_migrations_sorted(tmp_path):
    _write(tmp_path, "0003_c.sql")
    _write(tmp_path, "0001_a.sql")
    _write(tmp_path, "0002_b.sql")
    _write(tmp_path, "README.md", "not a migration")
    _write(tmp_path, "bad-name.sql", "no version prefix")
    found = db.discover_migrations(tmp_path)
    assert [v for v, _ in found] == [1, 2, 3]


def test_discover_rejects_duplicate_versions(tmp_path):
    _write(tmp_path, "0001_a.sql")
    _write(tmp_path, "0001_b.sql")
    with pytest.raises(ValueError, match="duplicate"):
        db.discover_migrations(tmp_path)


def test_pending_migrations_filters_applied(tmp_path):
    _write(tmp_path, "0001_a.sql")
    _write(tmp_path, "0002_b.sql")
    pending = db.pending_migrations({1}, tmp_path)
    assert [v for v, _ in pending] == [2]


def test_real_migrations_dir_conforms():
    found = db.discover_migrations()
    assert found, "api/migrations must contain at least one migration"
    assert [v for v, _ in found] == sorted(v for v, _ in found)


def test_run_migrations_skips_without_database_url(monkeypatch, caplog):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    assert db.run_migrations() == []


@pytest.mark.skipif(
    not os.environ.get("DATABASE_URL"),
    reason="needs DATABASE_URL (real Postgres) for integration",
)
def test_run_migrations_applies_against_postgres(tmp_path):
    _write(tmp_path, "0001_t.sql", "CREATE TABLE mig_probe (id INTEGER PRIMARY KEY);")
    applied = db.run_migrations(tmp_path)
    assert applied == [1]
    # Second run is a no-op (idempotent).
    assert db.run_migrations(tmp_path) == []
