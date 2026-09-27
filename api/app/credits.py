"""Credit ledger (API-060).

- The ledger is append-only and is the source of truth; balances are derived
  as ``SUM(delta)``. Reasons: ``starter`` / ``exchange_spend`` / ``exchange_earn``.
- New users get 3 starter credits (PRD bootstrap) via ``ensure_starter_credits``
  — idempotent, safe to call from every user-creation path; the 0009 migration
  backfills pre-existing users.
- This module imports nothing from the users domain (users.py imports from
  here) so the dependency direction stays one-way.
"""

from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Any, Protocol

from fastapi import Depends

from .db import get_db_conn

STARTER_CREDITS = 3


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class CreditRepo(Protocol):
    def add_entry(self, uid: str, delta: int, reason: str,
                  ref_id: str | None = None,
                  idempotency_key: str | None = None) -> dict[str, Any]:
        """Append a ledger entry. Idempotent on idempotency_key: repeats
        return the existing entry instead of double-posting."""
        ...
    def find_by_idempotency_key(self, key: str) -> dict[str, Any] | None: ...
    def balance(self, uid: str) -> int: ...
    def entries(self, uid: str) -> list[dict[str, Any]]: ...
    def add_confirmation(self, listing_id: str, uid: str) -> bool:
        """Record an exchange confirmation. Returns True when newly added."""
        ...
    def confirmations(self, listing_id: str) -> list[str]: ...


class PostgresCreditRepo:
    def __init__(self, conn):
        self._conn = conn

    @staticmethod
    def _row(row) -> dict:
        d = dict(row)
        c = d.get("created_at")
        d["created_at"] = c.isoformat() if hasattr(c, "isoformat") else c
        return d

    def add_entry(self, uid, delta, reason, ref_id=None, idempotency_key=None):
        # Concurrent same-key inserts: exactly one wins; the loser re-reads.
        row = self._conn.execute(
            "INSERT INTO credit_ledger (id, uid, delta, reason, ref_id, idempotency_key) "
            "VALUES (%s,%s,%s,%s,%s,%s) "
            "ON CONFLICT (idempotency_key) DO NOTHING RETURNING *",
            (str(uuid.uuid4()), uid, delta, reason, ref_id, idempotency_key),
        ).fetchone()
        self._conn.commit()
        if row is None and idempotency_key:
            row = self._conn.execute(
                "SELECT * FROM credit_ledger WHERE idempotency_key = %s", (idempotency_key,)
            ).fetchone()
        return self._row(row) if row else None

    def find_by_idempotency_key(self, key):
        row = self._conn.execute(
            "SELECT * FROM credit_ledger WHERE idempotency_key = %s", (key,)
        ).fetchone()
        return self._row(row) if row else None

    def balance(self, uid):
        row = self._conn.execute(
            "SELECT COALESCE(SUM(delta), 0) AS b FROM credit_ledger WHERE uid = %s",
            (uid,),
        ).fetchone()
        return int(row["b"])

    def entries(self, uid):
        rows = self._conn.execute(
            "SELECT * FROM credit_ledger WHERE uid = %s ORDER BY created_at", (uid,)
        ).fetchall()
        return [self._row(r) for r in rows]

    def add_confirmation(self, listing_id, uid):
        cur = self._conn.execute(
            "INSERT INTO exchange_confirmations (listing_id, uid) VALUES (%s,%s) "
            "ON CONFLICT DO NOTHING",
            (listing_id, uid),
        )
        self._conn.commit()
        return (cur.rowcount or 0) > 0

    def confirmations(self, listing_id):
        rows = self._conn.execute(
            "SELECT uid FROM exchange_confirmations WHERE listing_id = %s", (listing_id,)
        ).fetchall()
        return [r["uid"] for r in rows]


class MemoryCreditRepo:
    def __init__(self):
        self._entries: list[dict[str, Any]] = []
        self._by_key: dict[str, dict[str, Any]] = {}
        self._confirmations: dict[str, set[str]] = {}

    def add_entry(self, uid, delta, reason, ref_id=None, idempotency_key=None):
        if idempotency_key and idempotency_key in self._by_key:
            return dict(self._by_key[idempotency_key])
        row = {
            "id": str(uuid.uuid4()), "uid": uid, "delta": delta, "reason": reason,
            "ref_id": ref_id, "idempotency_key": idempotency_key,
            "created_at": _utcnow().isoformat(),
        }
        self._entries.append(row)
        if idempotency_key:
            self._by_key[idempotency_key] = row
        return dict(row)

    def find_by_idempotency_key(self, key):
        row = self._by_key.get(key)
        return dict(row) if row else None

    def balance(self, uid):
        return sum(e["delta"] for e in self._entries if e["uid"] == uid)

    def entries(self, uid):
        return [dict(e) for e in self._entries if e["uid"] == uid]

    def add_confirmation(self, listing_id, uid):
        s = self._confirmations.setdefault(listing_id, set())
        if uid in s:
            return False
        s.add(uid)
        return True

    def confirmations(self, listing_id):
        return sorted(self._confirmations.get(listing_id, set()))


def get_credit_repo(conn=Depends(get_db_conn)) -> CreditRepo:
    return PostgresCreditRepo(conn)


def ensure_starter_credits(uid: str, credit_repo: CreditRepo) -> None:
    """Grant the 3-credit bootstrap once per user. Idempotent — safe to call
    from every user-creation path (profile upsert, phone verify)."""
    if not any(e["reason"] == "starter" for e in credit_repo.entries(uid)):
        credit_repo.add_entry(uid, STARTER_CREDITS, "starter", ref_id=uid)
