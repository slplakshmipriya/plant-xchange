"""API-022: photo upload — sign/PUT/finalize, EXIF GPS stripping, key safety."""
from __future__ import annotations

import io

import pytest
from PIL import Image
from PIL.ExifTags import IFD


@pytest.fixture()
def uploads_dir(monkeypatch, tmp_path):
    monkeypatch.setenv("UPLOADS_DIR", str(tmp_path / "uploads"))
    monkeypatch.setenv("STORAGE_BACKEND", "local")
    return tmp_path / "uploads"


def _gps_jpeg() -> bytes:
    img = Image.new("RGB", (64, 64), "red")
    ex = Image.Exif()
    ex[IFD.GPSInfo] = {
        0: b"\x02\x03\x00\x00",
        1: "N", 2: (37.0, 46.0, 0.0),
        3: "W", 4: (122.0, 25.0, 0.0),
    }
    buf = io.BytesIO()
    img.save(buf, "JPEG", exif=ex)
    return buf.getvalue()


def _plain_png() -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (32, 32), "blue").save(buf, "PNG")
    return buf.getvalue()


def test_sign_rejects_non_image_content_type(client, mock_verify, auth_headers, uploads_dir):
    r = client.post("/v1/uploads/sign",
                    json={"content_type": "application/pdf", "size_bytes": 100},
                    headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "invalid_upload"


def test_sign_rejects_oversize(client, mock_verify, auth_headers, uploads_dir):
    r = client.post("/v1/uploads/sign",
                    json={"content_type": "image/jpeg", "size_bytes": 9 * 1024 * 1024},
                    headers=auth_headers)
    assert r.status_code == 422


def test_sign_requires_auth(client, uploads_dir):
    r = client.post("/v1/uploads/sign", json={"content_type": "image/jpeg", "size_bytes": 10})
    assert r.status_code == 401


def test_full_flow_strips_gps_exif(client, mock_verify, auth_headers, uploads_dir):
    raw = _gps_jpeg()
    # sanity: the crafted upload really contains GPS EXIF
    assert IFD.GPSInfo in Image.open(io.BytesIO(raw)).getexif()

    signed = client.post("/v1/uploads/sign",
                         json={"content_type": "image/jpeg", "size_bytes": len(raw)},
                         headers=auth_headers).json()
    key = signed["key"]
    assert key.startswith("u/alice/")

    put = client.put(signed["upload_url"], content=raw,
                     headers={**auth_headers, "Content-Type": "image/jpeg"})
    assert put.status_code == 200

    fin = client.post("/v1/uploads/finalize", json={"key": key}, headers=auth_headers)
    assert fin.status_code == 200
    meta = fin.json()
    assert meta["gps_stripped"] is True
    assert meta["thumb_url"] == meta["public_url"]
    assert meta["content_type"] == "image/jpeg"

    # the stored file itself no longer carries GPS
    stored = Image.open(uploads_dir / key)
    assert IFD.GPSInfo not in stored.getexif()

    # the public URL serves the file without auth
    pub = client.get(meta["public_url"])
    assert pub.status_code == 200
    assert pub.headers["content-type"] == "image/jpeg"


def test_finalize_rejects_non_image(client, mock_verify, auth_headers, uploads_dir):
    signed = client.post("/v1/uploads/sign",
                         json={"content_type": "image/png", "size_bytes": 100},
                         headers=auth_headers).json()
    client.put(signed["upload_url"], content=b"definitely not an image" * 10,
               headers=auth_headers)
    r = client.post("/v1/uploads/finalize", json={"key": signed["key"]}, headers=auth_headers)
    assert r.status_code == 422


def test_finalize_rejects_other_users_key(client, mock_verify, auth_headers, uploads_dir):
    r = client.post("/v1/uploads/finalize", json={"key": "u/bob/abc123.jpg"},
                    headers=auth_headers)
    assert r.status_code == 422
    assert r.json()["code"] == "invalid_upload"


def test_finalize_requires_put_first(client, mock_verify, auth_headers, uploads_dir):
    signed = client.post("/v1/uploads/sign",
                         json={"content_type": "image/png", "size_bytes": 100},
                         headers=auth_headers).json()
    r = client.post("/v1/uploads/finalize", json={"key": signed["key"]}, headers=auth_headers)
    assert r.status_code == 422


def test_put_rejects_other_users_key(client, mock_verify, auth_headers, uploads_dir):
    r = client.put("/v1/uploads/raw/u/bob/abc123.jpg", content=b"x" * 10, headers=auth_headers)
    assert r.status_code == 403


def test_png_finalize_roundtrip(client, mock_verify, auth_headers, uploads_dir):
    raw = _plain_png()
    signed = client.post("/v1/uploads/sign",
                         json={"content_type": "image/png", "size_bytes": len(raw)},
                         headers=auth_headers).json()
    client.put(signed["upload_url"], content=raw, headers=auth_headers)
    fin = client.post("/v1/uploads/finalize", json={"key": signed["key"]}, headers=auth_headers)
    assert fin.status_code == 200
    assert fin.json()["content_type"] == "image/png"
    assert fin.json()["gps_stripped"] is False


def test_put_rejects_oversize_body(client, mock_verify, auth_headers, uploads_dir):
    signed = client.post("/v1/uploads/sign",
                         json={"content_type": "image/jpeg", "size_bytes": 100},
                         headers=auth_headers).json()
    r = client.put(signed["upload_url"], content=b"x" * (9 * 1024 * 1024), headers=auth_headers)
    assert r.status_code == 413


def test_storage_rejects_path_traversal(uploads_dir):
    from app.storage import LocalStubStorage, StorageError

    store = LocalStubStorage(uploads_dir)
    # ".." cannot be expressed in a valid key at all
    with pytest.raises(StorageError):
        store.store_raw("u/alice/../../evil.jpg", b"x")
    with pytest.raises(StorageError):
        store.store_raw("u/alice/../../../tmp/evil.jpg", b"x")
    # a well-formed key still works
    store.store_raw("u/alice/abc123.jpg", b"x")


def test_gcs_skeleton_is_structure_only(monkeypatch):
    import os

    from app.storage import GCSStorage, StorageNotConfigured

    monkeypatch.setenv("GCS_BUCKET", "dummy-bucket")
    store = GCSStorage()
    with pytest.raises(StorageNotConfigured):
        store.sign_upload("alice", "image/jpeg", 100)


def test_unknown_backend_rejected(client, mock_verify, auth_headers, monkeypatch, tmp_path):
    monkeypatch.setenv("STORAGE_BACKEND", "s3")
    r = client.post("/v1/uploads/sign",
                    json={"content_type": "image/jpeg", "size_bytes": 100},
                    headers=auth_headers)
    assert r.status_code == 501
