"""User profiles (API-010).

- ``uid`` is the Firebase Auth uid from the verified ID token — never
  client-chosen. All writes are owner-scoped (``ensure_owner``).
- Repository pattern: ``UserRepo`` protocol with a Postgres implementation
  and an in-memory one for tests. Route handlers depend on the
  ``get_user_repo`` factory; tests override it via ``dependency_overrides``.
- SEC-010 (PII): ``public_profile()`` and ``owner_profile()`` are the ONLY
  serializers. Neither emits ``phone_hash`` or ``device_fingerprint``.
  ``home_zip`` is emitted only to the owner.
"""

from __future__ import annotations

import re
from typing import Any, Protocol

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from .auth import ensure_owner, get_current_uid
from .db import get_db_conn

router = APIRouter(prefix="/v1/users", tags=["users"])

ZIP_RE = re.compile(r"^\d{5}$")

# Fields that must never leave the server (SEC-010). Asserted by test_pii.py.
_NEVER_EXPOSE = ("phone_hash", "device_fingerprint")


class ProfileIn(BaseModel):
    display_name: str | None = Field(default=None, max_length=80)
    avatar_url: str | None = Field(default=None, max_length=2048)
    home_zip: str | None = Field(default=None, max_length=10)


class PublicProfile(BaseModel):
    uid: str
    display_name: str | None
    avatar_url: str | None
    idv_status: str = "unverified"


class OwnerProfile(PublicProfile):
    home_zip: str | None
    created_at: str | None = None


def public_profile(row: dict[str, Any]) -> dict[str, Any]:
    """Serializer for profiles seen by other users. No PII, ever."""
    return {
        "uid": row["uid"],
        "display_name": row.get("display_name"),
        "avatar_url": row.get("avatar_url"),
        "idv_status": row.get("idv_status", "unverified"),
    }


def owner_profile(row: dict[str, Any]) -> dict[str, Any]:
    """Serializer for the owner's own profile. Still no phone_hash/fingerprint."""
    out = public_profile(row)
    out["home_zip"] = row.get("home_zip")
    created = row.get("created_at")
    out["created_at"] = created.isoformat() if hasattr(created, "isoformat") else created
    return out


def _check_pii_leak(payload: dict[str, Any]) -> None:
    for field in _NEVER_EXPOSE:
        assert field not in payload, f"PII leak: {field} in response payload"


class PhoneInUseError(Exception):
    """phone_hash already claimed by a different uid (safe 409, no uid leaked)."""


class UserRepo(Protocol):
    def upsert(self, uid: str, **fields: Any) -> dict[str, Any]:
        """Insert or update. Only non-None fields are written. Returns the row."""
        ...

    def get(self, uid: str) -> dict[str, Any] | None: ...
    def get_by_phone_hash(self, phone_hash: str) -> dict[str, Any] | None: ...
    def set_idv_status(self, uid: str, status: str) -> None: ...


class PostgresUserRepo:
    def __init__(self, conn):
        self._conn = conn

    def upsert(self, uid: str, **fields: Any) -> dict[str, Any]:
        import psycopg

        clean = {k: v for k, v in fields.items() if v is not None}
        try:
            if clean:
                cols = ["uid", *clean.keys()]
                placeholders = ", ".join(["%s"] * len(cols))
                updates = ", ".join(f"{c} = EXCLUDED.{c}" for c in clean.keys())
                row = self._conn.execute(
                    f"INSERT INTO users ({', '.join(cols)}) VALUES ({placeholders}) "
                    f"ON CONFLICT (uid) DO UPDATE SET {updates} RETURNING *",
                    [uid, *clean.values()],
                ).fetchone()
            else:
                # No fields to write: ensure the row exists, then read it back.
                self._conn.execute(
                    "INSERT INTO users (uid) VALUES (%s) ON CONFLICT (uid) DO NOTHING",
                    (uid,),
                )
                row = self._conn.execute(
                    "SELECT * FROM users WHERE uid = %s", (uid,)
                ).fetchone()
        except psycopg.errors.UniqueViolation as exc:
            raise PhoneInUseError() from exc
        self._conn.commit()
        return dict(row)

    def get(self, uid: str) -> dict[str, Any] | None:
        row = self._conn.execute("SELECT * FROM users WHERE uid = %s", (uid,)).fetchone()
        return dict(row) if row else None

    def get_by_phone_hash(self, phone_hash: str) -> dict[str, Any] | None:
        row = self._conn.execute(
            "SELECT * FROM users WHERE phone_hash = %s", (phone_hash,)
        ).fetchone()
        return dict(row) if row else None

    def set_idv_status(self, uid: str, status: str) -> None:
        self._conn.execute("UPDATE users SET idv_status = %s WHERE uid = %s", (status, uid))
        self._conn.commit()


class MemoryUserRepo:
    """In-memory repo for tests. Enforces the same uniqueness invariants."""

    def __init__(self):
        self._rows: dict[str, dict[str, Any]] = {}

    def upsert(self, uid: str, **fields: Any) -> dict[str, Any]:
        clean = {k: v for k, v in fields.items() if v is not None}
        phash = clean.get("phone_hash")
        if phash:
            for other_uid, other in self._rows.items():
                if other_uid != uid and other.get("phone_hash") == phash:
                    raise PhoneInUseError()
        row = self._rows.setdefault(uid, {"uid": uid, "idv_status": "unverified"})
        row.update(clean)
        return dict(row)

    def get(self, uid: str) -> dict[str, Any] | None:
        row = self._rows.get(uid)
        return dict(row) if row else None

    def get_by_phone_hash(self, phone_hash: str) -> dict[str, Any] | None:
        for row in self._rows.values():
            if row.get("phone_hash") == phone_hash:
                return dict(row)
        return None

    def set_idv_status(self, uid: str, status: str) -> None:
        if uid in self._rows:
            self._rows[uid]["idv_status"] = status


def get_user_repo(conn=Depends(get_db_conn)) -> UserRepo:
    return PostgresUserRepo(conn)


def _validate_profile(data: ProfileIn) -> None:
    if data.home_zip is not None and not ZIP_RE.match(data.home_zip):
        raise HTTPException(
            status_code=422,
            detail={"code": "invalid_zip", "message": "home_zip must be a 5-digit ZIP code"},
        )
    if data.avatar_url is not None and not data.avatar_url.startswith(("https://", "http://")):
        raise HTTPException(
            status_code=422,
            detail={"code": "invalid_avatar_url", "message": "avatar_url must be an http(s) URL"},
        )


@router.post("", response_model=OwnerProfile, status_code=200)
def upsert_profile(
    data: ProfileIn,
    uid: str = Depends(get_current_uid),
    repo: UserRepo = Depends(get_user_repo),
) -> dict[str, Any]:
    """Create or update the caller's own profile. uid comes from the ID token."""
    _validate_profile(data)
    row = repo.upsert(
        uid,
        display_name=data.display_name,
        avatar_url=data.avatar_url,
        home_zip=data.home_zip,
    )
    out = owner_profile(row)
    _check_pii_leak(out)
    return out


@router.get("/me", response_model=OwnerProfile)
def get_me(
    uid: str = Depends(get_current_uid),
    repo: UserRepo = Depends(get_user_repo),
) -> dict[str, Any]:
    row = repo.get(uid)
    if row is None:
        raise HTTPException(
            status_code=404,
            detail={"code": "profile_not_found", "message": "No profile yet — POST /v1/users to create one"},
        )
    out = owner_profile(row)
    _check_pii_leak(out)
    return out


@router.patch("/me", response_model=OwnerProfile)
def patch_me(
    data: ProfileIn,
    uid: str = Depends(get_current_uid),
    repo: UserRepo = Depends(get_user_repo),
) -> dict[str, Any]:
    # /me is inherently owner-scoped: uid comes from the verified ID token.
    _validate_profile(data)
    row = repo.upsert(
        uid,
        display_name=data.display_name,
        avatar_url=data.avatar_url,
        home_zip=data.home_zip,
    )
    out = owner_profile(row)
    _check_pii_leak(out)
    return out


@router.get("/{uid}", response_model=PublicProfile)
def get_public_profile(
    uid: str,
    repo: UserRepo = Depends(get_user_repo),
) -> dict[str, Any]:
    """Anyone authenticated can see the public profile — no PII, no home_zip."""
    row = repo.get(uid)
    if row is None:
        raise HTTPException(
            status_code=404,
            detail={"code": "profile_not_found", "message": "No such user"},
        )
    out = public_profile(row)
    _check_pii_leak(out)
    return out
