"""Upload routes (API-022): sign -> PUT bytes -> finalize."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from .auth import get_current_uid
from .db import get_db_conn
from .storage import (
    MAX_IMAGE_BYTES,
    PostgresUploadsRegistry,
    StorageError,
    StorageNotConfigured,
    UploadsRegistry,
    get_storage,
    guess_media_type,
)

router = APIRouter(prefix="/v1/uploads", tags=["uploads"])


def get_uploads_registry(conn=Depends(get_db_conn)) -> UploadsRegistry:
    """Uploads finalize-state registry. Tests override with MemoryUploadsRegistry."""
    return PostgresUploadsRegistry(conn)


class SignIn(BaseModel):
    content_type: str
    size_bytes: int = Field(gt=0)


class FinalizeIn(BaseModel):
    key: str


def _storage_error(exc: Exception) -> HTTPException:
    if isinstance(exc, StorageNotConfigured):
        return HTTPException(501, {"code": "storage_not_configured", "message": str(exc)})
    return HTTPException(422, {"code": "invalid_upload", "message": str(exc)})


@router.post("/sign")
def sign_upload(
    data: SignIn,
    uid: str = Depends(get_current_uid),
) -> dict:
    try:
        return get_storage().sign_upload(uid, data.content_type, data.size_bytes)
    except StorageError as exc:
        raise _storage_error(exc)


@router.put("/raw/{key:path}")
async def put_raw(
    key: str,
    request: Request,
    uid: str = Depends(get_current_uid),
    registry: UploadsRegistry = Depends(get_uploads_registry),
) -> dict:
    """Local-stub raw PUT target. (GCS backend: 501 — clients PUT to the signed URL.)"""
    data = await request.body()
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(413, {"code": "upload_too_large", "message": "Upload exceeds 8 MB"})
    try:
        from .storage import _check_key_format, LocalStubStorage

        _check_key_format(key)
        owner = key.split("/")[1]
        if owner != uid:
            raise HTTPException(403, {"code": "forbidden",
                                      "message": "Upload key does not belong to caller"})
        backend = get_storage()
        if not isinstance(backend, LocalStubStorage):
            raise StorageNotConfigured("raw PUT is only available on the local stub backend")
        backend.store_raw(key, data)
        registry.record_raw(key, uid)
    except StorageError as exc:
        raise _storage_error(exc)
    return {"ok": True, "bytes": len(data)}


@router.post("/finalize")
def finalize_upload(
    data: FinalizeIn,
    uid: str = Depends(get_current_uid),
    registry: UploadsRegistry = Depends(get_uploads_registry),
) -> dict:
    try:
        meta = get_storage().finalize(uid, data.key)
    except StorageError as exc:
        raise _storage_error(exc)
    # Only after EXIF stripping succeeded: the file is safe to serve publicly.
    registry.mark_finalized(data.key)
    return meta


@router.get("/public/{key:path}", include_in_schema=False)
def serve_public(key: str, registry: UploadsRegistry = Depends(get_uploads_registry)):
    """Local-stub file serving. Auth-exempt: listing photos are public.

    C7: serves only uploads that ran through finalize (EXIF GPS stripped).
    Raw PUT bytes without a finalized registry record 404 — their EXIF may
    still be intact.
    """
    from .storage import LocalStubStorage

    backend = get_storage()
    if not isinstance(backend, LocalStubStorage):
        raise HTTPException(501, {"code": "storage_not_configured",
                                  "message": "public serving is local-stub only"})
    try:
        path = backend.public_path(key)
    except StorageError:
        raise HTTPException(404, {"code": "not_found", "message": "No such upload"})
    if not registry.is_finalized(key):
        raise HTTPException(404, {"code": "not_found", "message": "No such upload"})
    if not path.is_file():
        raise HTTPException(404, {"code": "not_found", "message": "No such upload"})
    return FileResponse(path, media_type=guess_media_type(key))
