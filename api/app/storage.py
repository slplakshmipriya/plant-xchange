"""Photo upload pipeline (API-022).

Flow: ``POST /v1/uploads/sign`` -> client PUTs bytes to ``upload_url`` ->
``POST /v1/uploads/finalize`` validates and returns the public URL that
listing ``photos[]`` entries reference.

- ``StorageBackend`` is the abstraction; ``LocalStubStorage`` (default) keeps
  files under ``./var/uploads`` for dev/test, ``GCSStorage`` is a skeleton
  selected by ``STORAGE_BACKEND=gcs`` (structure only — no creds here).
- Server-side validation on finalize: content is a real image (Pillow),
  size <= 8 MB, and **EXIF GPS is stripped** (privacy: phone photos embed
  exact coordinates — SEC-010).
- Keys are ``u/{uid}/{uuid}.{ext}``; ownership and path traversal are
  enforced on every operation.
"""

from __future__ import annotations

import logging
import mimetypes
import re
import uuid
from pathlib import Path
from typing import Protocol

from .config import get_settings

logger = logging.getLogger(__name__)

MAX_IMAGE_BYTES = 8 * 1024 * 1024
KEY_RE = re.compile(r"^u/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\.[a-z0-9]+$")
GPS_IFD_TAG = 0x8825  # EXIF GPSInfo IFD

ALLOWED_CONTENT_TYPES = {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
    "image/heic": "heic",
}


class StorageError(Exception):
    pass


class StorageNotConfigured(StorageError):
    pass


class StorageBackend(Protocol):
    def sign_upload(self, uid: str, content_type: str, size_bytes: int) -> dict:
        """Return {"upload_url", "key", "public_url"} for a client PUT."""
        ...

    def store_raw(self, key: str, data: bytes) -> None:
        """Persist raw PUT bytes (local stub only)."""
        ...

    def finalize(self, uid: str, key: str) -> dict:
        """Validate + strip GPS EXIF. Returns metadata incl. public/thumb URLs."""
        ...


def _check_key(key: str, uid: str) -> None:
    if not KEY_RE.match(key):
        raise StorageError("malformed upload key")
    owner = key.split("/")[1]
    if owner != uid:
        raise StorageError("upload key does not belong to caller")


def _check_key_format(key: str) -> None:
    if not KEY_RE.match(key):
        raise StorageError("malformed upload key")


class LocalStubStorage:
    """Dev/test backend: files under <root>/<key>, served by /v1/uploads/public."""

    def __init__(self, root: str | Path | None = None):
        self.root = Path(root or get_settings().uploads_dir).resolve()
        self.root.mkdir(parents=True, exist_ok=True)

    def _path(self, key: str) -> Path:
        # KEY_RE forbids ".." and "/", so this cannot escape root; belt-and-braces:
        p = (self.root / key).resolve()
        if p != self.root and self.root not in p.parents:
            raise StorageError("path traversal rejected")
        return p

    def public_path(self, key: str) -> Path:
        """Resolved path for serving a finalized upload."""
        _check_key_format(key)
        return self._path(key)

    def sign_upload(self, uid: str, content_type: str, size_bytes: int) -> dict:
        ext = ALLOWED_CONTENT_TYPES.get(content_type.lower())
        if not ext:
            raise StorageError(f"unsupported content type: {content_type}")
        if not (0 < size_bytes <= MAX_IMAGE_BYTES):
            raise StorageError(f"size must be within 1..{MAX_IMAGE_BYTES} bytes")
        key = f"u/{uid}/{uuid.uuid4().hex}.{ext}"
        return {
            "upload_url": f"/v1/uploads/raw/{key}",
            "key": key,
            "public_url": f"/v1/uploads/public/{key}",
        }

    def store_raw(self, key: str, data: bytes) -> None:
        _check_key_format(key)  # KEY_RE forbids ".." — traversal can't be expressed
        if len(data) > MAX_IMAGE_BYTES:
            raise StorageError("upload exceeds 8 MB")
        path = self._path(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def finalize(self, uid: str, key: str) -> dict:
        from PIL import Image, UnidentifiedImageError

        _check_key(key, uid)
        path = self._path(key)
        if not path.is_file():
            raise StorageError("upload not found — PUT bytes to upload_url first")
        if path.stat().st_size > MAX_IMAGE_BYTES:
            raise StorageError("upload exceeds 8 MB")
        try:
            with Image.open(path) as img:
                img.verify()  # not a real image -> raises
            with Image.open(path) as img:
                fmt = (img.format or "JPEG").lower()
                img.load()
                exif = img.getexif()
                gps_removed = GPS_IFD_TAG in exif
                if gps_removed:
                    del exif[GPS_IFD_TAG]
                # Re-save: applies GPS stripping and normalizes the file.
                img.save(path, format=img.format or "JPEG", exif=exif)
        except UnidentifiedImageError as exc:
            raise StorageError("not a valid image") from exc
        except StorageError:
            raise
        except Exception as exc:  # noqa: BLE001 — corrupt image, safe 422
            raise StorageError(f"image processing failed: {type(exc).__name__}") from exc
        if gps_removed:
            logger.info("stripped EXIF GPS from upload %s", key)
        public_url = f"/v1/uploads/public/{key}"
        return {
            "key": key,
            "public_url": public_url,
            "thumb_url": public_url,  # stub: no separate thumbnail rendition
            "size_bytes": path.stat().st_size,
            "content_type": f"image/{'jpeg' if fmt == 'jpeg' else fmt}",
            "gps_stripped": gps_removed,
        }


class GCSStorage:
    """Production skeleton (STORAGE_BACKEND=gcs). Structure only — no creds,
    no network calls in this environment. sign_upload would mint a GCS V4
    signed PUT URL; finalize would validate via a GCS download stream."""

    def __init__(self, bucket: str | None = None):
        import os

        self.bucket = bucket or os.environ.get("GCS_BUCKET")
        if not self.bucket:
            raise StorageNotConfigured("GCS_BUCKET is not set")

    def sign_upload(self, uid: str, content_type: str, size_bytes: int) -> dict:
        raise StorageNotConfigured("GCS signed-URL minting is not wired in this environment")

    def store_raw(self, key: str, data: bytes) -> None:
        raise StorageNotConfigured("GCS backend has no local raw-PUT path")

    def finalize(self, uid: str, key: str) -> dict:
        raise StorageNotConfigured("GCS finalize is not wired in this environment")


def get_storage() -> StorageBackend:
    backend = get_settings().storage_backend
    if backend == "gcs":
        return GCSStorage()
    if backend == "local":
        return LocalStubStorage()
    raise StorageNotConfigured(f"unknown STORAGE_BACKEND: {backend}")


def guess_media_type(key: str) -> str:
    return mimetypes.guess_type(key)[0] or "application/octet-stream"
