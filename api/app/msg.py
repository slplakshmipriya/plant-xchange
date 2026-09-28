"""Listing-scoped messaging (API-080).

- ``POST /v1/threads``: open (or fetch the existing) thread about a listing.
  One thread per ``(listing_id, user)`` — the conversation is between the
  listing owner and the user who opened it.
- ``GET /v1/threads``: threads the caller participates in (opened by them or
  on their listings).
- ``POST /v1/threads/{id}/messages`` + ``GET .../messages``: send and read,
  participant-only, cursor-paginated (opaque base64 cursor, like the feed).
  Messages carry a ``kind`` ("text" | "photo"); photo messages also carry
  ``photo_url``.
- ``POST /v1/threads/{id}/attachments``: attach a photo by URL as a
  "photo"-kind message, participant-only, URL validated like listing photos.
- Thread payloads include ``participant_uids`` (the two uids in the
  conversation: the opener and the listing owner) so clients can identify
  the other party.
- Geo rule: thread/message payloads never carry coordinates — exact geo stays
  hidden until the exchange-confirm flow (API-060) completes, and even then
  it is exchanged out of band, not through these serializers.
"""

from __future__ import annotations

import base64
import uuid
from typing import Any, Protocol

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field

from .auth import get_current_uid
from .db import get_db_conn
from .listings import ListingRepo, get_listing_repo
from .users import UserRepo, get_user_repo

router = APIRouter(prefix="/v1", tags=["messaging"])

PAGE_SIZE = 20


def _encode_cursor(offset: int) -> str:
    return base64.urlsafe_b64encode(str(offset).encode()).decode()


def _decode_cursor(cursor: str | None) -> int:
    if not cursor:
        return 0
    try:
        offset = int(base64.urlsafe_b64decode(cursor.encode()).decode())
    except Exception:
        raise HTTPException(400, {"code": "invalid_cursor",
                                  "message": "Malformed message cursor"})
    if offset < 0:
        raise HTTPException(400, {"code": "invalid_cursor",
                                  "message": "Malformed message cursor"})
    return offset


class MessageRepo(Protocol):
    def get_or_create_thread(self, listing_id: str, uid: str) -> dict[str, Any]: ...
    def get_thread(self, thread_id: str) -> dict[str, Any] | None: ...
    def list_threads_for(self, uid: str, owner_listing_ids: list[str]) -> list[dict[str, Any]]: ...
    def add_message(self, thread_id: str, sender_uid: str, body: str,
                    kind: str = "text", photo_url: str | None = None) -> dict[str, Any]: ...
    def list_messages(self, thread_id: str, offset: int, limit: int) -> list[dict[str, Any]]: ...
    def count_messages(self, thread_id: str) -> int: ...


def _serialize_thread(row: dict[str, Any], message_count: int = 0,
                      listing: dict[str, Any] | None = None) -> dict[str, Any]:
    # No geo fields, ever (API-080).
    participants = {row["created_by"]}
    owner_uid = listing.get("owner_uid") if listing else None
    if owner_uid:
        participants.add(owner_uid)
    return {
        "id": str(row["id"]),
        "listing_id": str(row["listing_id"]),
        "created_by": row["created_by"],
        "created_at": row.get("created_at"),
        "message_count": message_count,
        "participant_uids": sorted(participants),
    }


def _serialize_message(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": str(row["id"]),
        "thread_id": str(row["thread_id"]),
        "sender_uid": row["sender_uid"],
        "kind": row.get("kind", "text"),
        "body": row["body"],
        "photo_url": row.get("photo_url"),
        "created_at": row.get("created_at"),
    }


class PostgresMessageRepo:
    def __init__(self, conn):
        self._conn = conn

    @staticmethod
    def _row(row) -> dict:
        d = dict(row)
        c = d.get("created_at")
        d["created_at"] = c.isoformat() if hasattr(c, "isoformat") else c
        return d

    def get_or_create_thread(self, listing_id, uid):
        row = self._conn.execute(
            "SELECT * FROM threads WHERE listing_id = %s AND created_by = %s",
            (listing_id, uid)).fetchone()
        if row:
            return self._row(row)
        tid = str(uuid.uuid4())
        self._conn.execute(
            "INSERT INTO threads (id, listing_id, created_by) VALUES (%s,%s,%s) "
            "ON CONFLICT (listing_id, created_by) DO NOTHING",
            (tid, listing_id, uid))
        self._conn.commit()
        row = self._conn.execute(
            "SELECT * FROM threads WHERE listing_id = %s AND created_by = %s",
            (listing_id, uid)).fetchone()
        return self._row(row)

    def get_thread(self, thread_id):
        row = self._conn.execute(
            "SELECT * FROM threads WHERE id = %s", (thread_id,)).fetchone()
        return self._row(row) if row else None

    def list_threads_for(self, uid, owner_listing_ids):
        if owner_listing_ids:
            rows = self._conn.execute(
                "SELECT * FROM threads WHERE created_by = %s OR listing_id = ANY(%s) "
                "ORDER BY created_at DESC",
                (uid, owner_listing_ids)).fetchall()
        else:
            rows = self._conn.execute(
                "SELECT * FROM threads WHERE created_by = %s ORDER BY created_at DESC",
                (uid,)).fetchall()
        return [self._row(r) for r in rows]

    def add_message(self, thread_id, sender_uid, body, kind="text", photo_url=None):
        mid = str(uuid.uuid4())
        row = self._conn.execute(
            "INSERT INTO messages (id, thread_id, sender_uid, body, kind, photo_url) "
            "VALUES (%s,%s,%s,%s,%s,%s) RETURNING *",
            (mid, thread_id, sender_uid, body, kind, photo_url)).fetchone()
        self._conn.commit()
        return self._row(row)

    def list_messages(self, thread_id, offset, limit):
        rows = self._conn.execute(
            "SELECT * FROM messages WHERE thread_id = %s "
            "ORDER BY created_at, id LIMIT %s OFFSET %s",
            (thread_id, limit + 1, offset)).fetchall()
        return [self._row(r) for r in rows]

    def count_messages(self, thread_id):
        row = self._conn.execute(
            "SELECT COUNT(*) AS n FROM messages WHERE thread_id = %s",
            (thread_id,)).fetchone()
        return int(row["n"])


class MemoryMessageRepo:
    def __init__(self):
        self._threads: dict[str, dict[str, Any]] = {}
        self._by_listing_user: dict[tuple[str, str], str] = {}
        self._messages: dict[str, list[dict[str, Any]]] = {}

    def get_or_create_thread(self, listing_id, uid):
        from .listings import utcnow
        key = (listing_id, uid)
        if key in self._by_listing_user:
            return dict(self._threads[self._by_listing_user[key]])
        tid = str(uuid.uuid4())
        row = {"id": tid, "listing_id": listing_id, "created_by": uid,
               "created_at": utcnow().isoformat()}
        self._threads[tid] = row
        self._by_listing_user[key] = tid
        self._messages[tid] = []
        return dict(row)

    def get_thread(self, thread_id):
        row = self._threads.get(thread_id)
        return dict(row) if row else None

    def list_threads_for(self, uid, owner_listing_ids):
        owned = set(owner_listing_ids)
        rows = [t for t in self._threads.values()
                if t["created_by"] == uid or t["listing_id"] in owned]
        rows.sort(key=lambda t: t["created_at"], reverse=True)
        return [dict(t) for t in rows]

    def add_message(self, thread_id, sender_uid, body, kind="text", photo_url=None):
        from .listings import utcnow
        row = {"id": str(uuid.uuid4()), "thread_id": thread_id,
               "sender_uid": sender_uid, "body": body, "kind": kind,
               "photo_url": photo_url, "created_at": utcnow().isoformat()}
        self._messages[thread_id].append(row)
        return dict(row)

    def list_messages(self, thread_id, offset, limit):
        msgs = sorted(self._messages.get(thread_id, []),
                      key=lambda m: (m["created_at"], m["id"]))
        return [dict(m) for m in msgs[offset:offset + limit + 1]]

    def count_messages(self, thread_id):
        return len(self._messages.get(thread_id, []))


def get_message_repo(conn=Depends(get_db_conn)) -> MessageRepo:
    return PostgresMessageRepo(conn)


class ThreadIn(BaseModel):
    listing_id: str = Field(min_length=1)


class MessageIn(BaseModel):
    body: str = Field(min_length=1, max_length=2000)


class AttachmentIn(BaseModel):
    photoUrl: str = Field(min_length=1, max_length=2000)


def _validate_photo_url(url: str) -> str:
    """Mirror the listing photo rule (listings._validate_common): http(s) only."""
    url = url.strip()
    if not url.startswith(("https://", "http://")):
        raise HTTPException(422, {"code": "invalid_photo_url",
                                  "message": "photo URLs must be http(s)"})
    return url


def _participant_or_403(thread: dict[str, Any] | None,
                        listing: dict[str, Any] | None, uid: str) -> dict[str, Any]:
    if thread is None:
        raise HTTPException(404, {"code": "thread_not_found",
                                  "message": "No such thread"})
    parties = {thread["created_by"]}
    if listing is not None:
        parties.add(listing["owner_uid"])
    if uid not in parties:
        raise HTTPException(403, {"code": "not_a_participant",
                                  "message": "This conversation is private"})
    return thread


@router.post("/threads", status_code=200, tags=["messaging"])
def open_thread(
    data: ThreadIn,
    uid: str = Depends(get_current_uid),
    repo: MessageRepo = Depends(get_message_repo),
    listing_repo: ListingRepo = Depends(get_listing_repo),
    user_repo: UserRepo = Depends(get_user_repo),
) -> dict[str, Any]:
    """Open (or fetch) your thread about a listing. Idempotent per listing."""
    listing = listing_repo.get(data.listing_id)
    if listing is None:
        raise HTTPException(404, {"code": "listing_not_found",
                                  "message": "No such listing"})
    if user_repo.get(uid) is None:
        raise HTTPException(400, {"code": "profile_required",
                                  "message": "Create a profile (POST /v1/users) before messaging"})
    thread = repo.get_or_create_thread(data.listing_id, uid)
    return _serialize_thread(thread, repo.count_messages(thread["id"]), listing)


@router.get("/threads", tags=["messaging"])
def list_threads(
    uid: str = Depends(get_current_uid),
    repo: MessageRepo = Depends(get_message_repo),
    listing_repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    """Threads you opened plus threads on your listings."""
    owned_ids = [l["id"] for l in listing_repo.list_by_owner(uid)]
    threads = repo.list_threads_for(uid, owned_ids)
    out = []
    for t in threads:
        listing = listing_repo.get(str(t["listing_id"]))
        out.append(_serialize_thread(t, repo.count_messages(t["id"]), listing))
    return {"threads": out}


@router.post("/threads/{thread_id}/messages", status_code=201, tags=["messaging"])
def send_message(
    thread_id: str,
    data: MessageIn,
    uid: str = Depends(get_current_uid),
    repo: MessageRepo = Depends(get_message_repo),
    listing_repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    """Send a message. Participants only."""
    thread = repo.get_thread(thread_id)
    listing = listing_repo.get(thread["listing_id"]) if thread else None
    _participant_or_403(thread, listing, uid)
    body = data.body.strip()
    if not body:
        raise HTTPException(422, {"code": "empty_message",
                                  "message": "Message body cannot be blank"})
    return _serialize_message(repo.add_message(thread_id, uid, body))


@router.post("/threads/{thread_id}/attachments", status_code=201, tags=["messaging"])
def attach_photo(
    thread_id: str,
    data: AttachmentIn,
    uid: str = Depends(get_current_uid),
    repo: MessageRepo = Depends(get_message_repo),
    listing_repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    """Attach a photo to a thread as a "photo"-kind message. Participants only."""
    thread = repo.get_thread(thread_id)
    listing = listing_repo.get(thread["listing_id"]) if thread else None
    _participant_or_403(thread, listing, uid)
    url = _validate_photo_url(data.photoUrl)
    return _serialize_message(
        repo.add_message(thread_id, uid, url, kind="photo", photo_url=url))


@router.get("/threads/{thread_id}/messages", tags=["messaging"])
def read_messages(
    thread_id: str,
    cursor: str | None = Query(default=None),
    limit: int = Query(default=PAGE_SIZE, ge=1, le=100),
    uid: str = Depends(get_current_uid),
    repo: MessageRepo = Depends(get_message_repo),
    listing_repo: ListingRepo = Depends(get_listing_repo),
) -> dict[str, Any]:
    """Read a thread's messages, oldest first. Participants only."""
    thread = repo.get_thread(thread_id)
    listing = listing_repo.get(thread["listing_id"]) if thread else None
    _participant_or_403(thread, listing, uid)
    offset = _decode_cursor(cursor)
    rows = repo.list_messages(thread_id, offset, limit)
    has_more = len(rows) > limit
    return {
        "messages": [_serialize_message(m) for m in rows[:limit]],
        "next_cursor": _encode_cursor(offset + limit) if has_more else None,
    }
