"""Seasonal want-list + match engine (API-030).

Want-list is the primary discovery mechanism (PRD pillar 1): users state what
they want to grow; when a matching listing goes **live**, each matching user
gets one push (category ``"match"``, ref = listing id — the notify layer's
24h dedupe guarantees at-most-one per listing per day).

Matching rule: variety substring, case-insensitive, either direction
(``"tomato"`` matches ``"Cherokee Purple tomato"`` and vice versa), AND the
listing type must be in the entry's ``types`` list (empty = any type).
"""

from __future__ import annotations

import uuid
from typing import Any, Protocol

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from .auth import ensure_owner, get_current_uid
from .db import get_db_conn
from .notify import NotificationRepo, get_notification_repo, send_notification

router = APIRouter(prefix="/v1/want-list", tags=["want-list"])

MATCH_CATEGORY = "match"
WANT_MATCH_BOOST = 1.0  # feed ranking boost for seedling matches (API-021)


def variety_matches(want: str, listing_variety: str | None) -> bool:
    """Substring match, case-insensitive, either direction."""
    w = (want or "").strip().lower()
    lv = (listing_variety or "").strip().lower()
    return bool(w and lv) and (w in lv or lv in w)


def _matches_types(entry_types: list[str], listing_type: str) -> bool:
    return not entry_types or listing_type in entry_types


class WantIn(BaseModel):
    variety: str = Field(min_length=1, max_length=120)
    types: list[str] = Field(default_factory=list, max_length=3)


class WantPatch(BaseModel):
    variety: str | None = Field(default=None, min_length=1, max_length=120)
    types: list[str] | None = Field(default=None, max_length=3)


def _validate_types(types: list[str] | None) -> None:
    from .listings import LISTING_TYPES

    for t in types or []:
        if t not in LISTING_TYPES:
            raise HTTPException(400, {"code": "invalid_want_type",
                                      "message": f"Unknown listing type '{t}'"})


def _serialize(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": str(row["id"]),
        "user_uid": row["user_uid"],
        "variety": row["variety"],
        "types": list(row.get("types") or []),
        "created_at": row.get("created_at"),
    }


class WantRepo(Protocol):
    def create(self, data: dict[str, Any]) -> dict[str, Any]: ...
    def list_for_user(self, uid: str) -> list[dict[str, Any]]: ...
    def get(self, want_id: str) -> dict[str, Any] | None: ...
    def update(self, want_id: str, fields: dict[str, Any]) -> dict[str, Any] | None: ...
    def delete(self, want_id: str) -> bool: ...
    def list_all(self) -> list[dict[str, Any]]: ...


class PostgresWantRepo:
    def __init__(self, conn):
        self._conn = conn

    @staticmethod
    def _row(row) -> dict:
        d = dict(row)
        c = d.get("created_at")
        d["created_at"] = c.isoformat() if hasattr(c, "isoformat") else c
        return d

    def create(self, data: dict[str, Any]) -> dict[str, Any]:
        row = self._conn.execute(
            "INSERT INTO want_list (id, user_uid, variety, types) VALUES (%s,%s,%s,%s) RETURNING *",
            (data["id"], data["user_uid"], data["variety"], data.get("types") or []),
        ).fetchone()
        self._conn.commit()
        return self._row(row)

    def list_for_user(self, uid: str) -> list[dict[str, Any]]:
        rows = self._conn.execute(
            "SELECT * FROM want_list WHERE user_uid = %s ORDER BY created_at", (uid,)
        ).fetchall()
        return [self._row(r) for r in rows]

    def get(self, want_id: str) -> dict[str, Any] | None:
        row = self._conn.execute("SELECT * FROM want_list WHERE id = %s", (want_id,)).fetchone()
        return self._row(row) if row else None

    def update(self, want_id: str, fields: dict[str, Any]) -> dict[str, Any] | None:
        if not fields:
            return self.get(want_id)
        sets = ", ".join(f"{k} = %s" for k in fields)
        self._conn.execute(
            f"UPDATE want_list SET {sets} WHERE id = %s", (*fields.values(), want_id)
        )
        self._conn.commit()
        return self.get(want_id)

    def delete(self, want_id: str) -> bool:
        cur = self._conn.execute("DELETE FROM want_list WHERE id = %s", (want_id,))
        self._conn.commit()
        return (cur.rowcount or 0) > 0

    def list_all(self) -> list[dict[str, Any]]:
        rows = self._conn.execute("SELECT * FROM want_list").fetchall()
        return [self._row(r) for r in rows]


class MemoryWantRepo:
    def __init__(self):
        self._rows: dict[str, dict[str, Any]] = {}

    def create(self, data: dict[str, Any]) -> dict[str, Any]:
        row = dict(data)
        self._rows[row["id"]] = row
        return dict(row)

    def list_for_user(self, uid: str) -> list[dict[str, Any]]:
        return [dict(r) for r in self._rows.values() if r["user_uid"] == uid]

    def get(self, want_id: str) -> dict[str, Any] | None:
        row = self._rows.get(want_id)
        return dict(row) if row else None

    def update(self, want_id: str, fields: dict[str, Any]) -> dict[str, Any] | None:
        row = self._rows.get(want_id)
        if row is None:
            return None
        row.update(fields)
        return dict(row)

    def delete(self, want_id: str) -> bool:
        return self._rows.pop(want_id, None) is not None

    def list_all(self) -> list[dict[str, Any]]:
        return [dict(r) for r in self._rows.values()]


def get_want_repo(conn=Depends(get_db_conn)) -> WantRepo:
    return PostgresWantRepo(conn)


def find_matches(row: dict[str, Any], entries: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Want-list entries matching a listing row. Excludes the listing owner."""
    out = []
    for e in entries:
        if e["user_uid"] == row.get("owner_uid"):
            continue
        if not variety_matches(e["variety"], row.get("variety")):
            continue
        if not _matches_types(list(e.get("types") or []), row.get("type")):
            continue
        out.append(e)
    return out


def notify_matches(
    row: dict[str, Any],
    want_repo: WantRepo,
    notify_repo: NotificationRepo,
) -> int:
    """Push one ``match`` notification per matching user. Returns count sent
    (status sent/would_send; skips don't count)."""
    n = 0
    for entry in find_matches(row, want_repo.list_all()):
        uid = entry["user_uid"]
        result = send_notification(
            uid,
            MATCH_CATEGORY,
            "A seedling you want is nearby",
            f"{row.get('variety') or 'A plant'} you want was just listed.",
            data={"listing_id": str(row["id"])},
            ref=str(row["id"]),
            repo=notify_repo,
        )
        if result["status"] in ("sent", "would_send"):
            n += 1
    return n


@router.post("", status_code=201)
def create_want(
    data: WantIn,
    uid: str = Depends(get_current_uid),
    repo: WantRepo = Depends(get_want_repo),
) -> dict[str, Any]:
    _validate_types(data.types)
    row = repo.create({
        "id": str(uuid.uuid4()),
        "user_uid": uid,
        "variety": data.variety.strip(),
        "types": data.types,
    })
    return _serialize(row)


@router.get("")
def list_wants(
    uid: str = Depends(get_current_uid),
    repo: WantRepo = Depends(get_want_repo),
) -> dict[str, Any]:
    return {"items": [_serialize(r) for r in repo.list_for_user(uid)]}


@router.patch("/{want_id}")
def patch_want(
    want_id: str,
    data: WantPatch,
    uid: str = Depends(get_current_uid),
    repo: WantRepo = Depends(get_want_repo),
) -> dict[str, Any]:
    row = repo.get(want_id)
    if row is None:
        raise HTTPException(404, {"code": "want_not_found", "message": "No such want-list entry"})
    ensure_owner(row["user_uid"], uid)
    _validate_types(data.types)
    fields = {k: v for k, v in data.model_dump(exclude_unset=True).items() if v is not None}
    if "variety" in fields:
        fields["variety"] = fields["variety"].strip()
    return _serialize(repo.update(want_id, fields))


@router.delete("/{want_id}", status_code=204)
def delete_want(
    want_id: str,
    uid: str = Depends(get_current_uid),
    repo: WantRepo = Depends(get_want_repo),
) -> None:
    row = repo.get(want_id)
    if row is None:
        raise HTTPException(404, {"code": "want_not_found", "message": "No such want-list entry"})
    ensure_owner(row["user_uid"], uid)
    repo.delete(want_id)
    return None
