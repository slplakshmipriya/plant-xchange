"""Postgres connection + versioned migration runner.

Conventions:
- Migrations live in ``api/migrations/`` named ``NNNN_description.sql``
  (NNNN = zero-padded sequence, e.g. ``0001_init.sql``).
- Applied versions are recorded in the ``schema_migrations`` table.
- The runner applies pending migrations in version order inside a single
  transaction per file, then records the version. No manual SQL.
- When ``DATABASE_URL`` is unset, startup skips migrations with a warning
  so /healthz and the test suite work without Postgres.
"""

from __future__ import annotations

import logging
import os
import re
from pathlib import Path

logger = logging.getLogger(__name__)

MIGRATIONS_DIR = Path(__file__).resolve().parent.parent / "migrations"
FILENAME_RE = re.compile(r"^(\d{3,})_[a-z0-9_]+\.sql$")


def database_url() -> str | None:
    return os.environ.get("DATABASE_URL")


def discover_migrations(migrations_dir: Path = MIGRATIONS_DIR) -> list[tuple[int, Path]]:
    """Return (version, path) for every valid migration file, sorted by version."""
    found: list[tuple[int, Path]] = []
    if not migrations_dir.is_dir():
        return found
    for path in sorted(migrations_dir.glob("*.sql")):
        m = FILENAME_RE.match(path.name)
        if not m:
            logger.warning("ignoring non-conforming migration file: %s", path.name)
            continue
        found.append((int(m.group(1)), path))
    versions = [v for v, _ in found]
    if len(set(versions)) != len(versions):
        raise ValueError(f"duplicate migration versions in {migrations_dir}")
    return sorted(found, key=lambda t: t[0])


def pending_migrations(
    applied: set[int], migrations_dir: Path = MIGRATIONS_DIR
) -> list[tuple[int, Path]]:
    """Migrations not yet applied, in version order."""
    return [(v, p) for v, p in discover_migrations(migrations_dir) if v not in applied]


def run_migrations(migrations_dir: Path = MIGRATIONS_DIR) -> list[int]:
    """Apply pending migrations. Returns the list of versions applied."""
    url = database_url()
    if not url:
        logger.warning("DATABASE_URL unset — skipping migrations")
        return []
    import psycopg

    applied: list[int] = []
    with psycopg.connect(url) as conn:
        with conn.cursor() as cur:
            cur.execute(
                "CREATE TABLE IF NOT EXISTS schema_migrations "
                "(version INTEGER PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())"
            )
            cur.execute("SELECT version FROM schema_migrations")
            done = {row[0] for row in cur.fetchall()}
        for version, path in pending_migrations(done, migrations_dir):
            sql = path.read_text(encoding="utf-8")
            logger.info("applying migration %s", path.name)
            with conn.cursor() as cur:
                cur.execute(sql)
                cur.execute(
                    "INSERT INTO schema_migrations (version) VALUES (%s)", (version,)
                )
            conn.commit()
            applied.append(version)
    if applied:
        logger.info("migrations applied: %s", applied)
    return applied
