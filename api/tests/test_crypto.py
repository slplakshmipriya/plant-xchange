"""H4: key versioning on Fernet tokens; M24: decrypt error envelope + cipher cache."""
from __future__ import annotations

import pytest
from cryptography.fernet import Fernet

from app.crypto import (
    GEO_KEY_ENV,
    MESSAGE_KEY_ENV,
    KEY_VERSION,
    decrypt_float,
    decrypt_text,
    encrypt_float,
    encrypt_text,
    generate_key,
    get_cipher,
)


def test_encrypt_emits_version_prefix():
    token = encrypt_text("hello", MESSAGE_KEY_ENV)
    assert token.startswith(f"v{KEY_VERSION}:")
    assert decrypt_text(token, MESSAGE_KEY_ENV) == "hello"


def test_legacy_unprefixed_token_still_decrypts():
    # Tokens minted before versioning carry no prefix; they are v1.
    import os

    key = os.environ[MESSAGE_KEY_ENV]
    legacy = Fernet(key.encode()).encrypt(b"legacy-body").decode()
    assert not legacy.startswith("v1:")
    assert decrypt_text(legacy, MESSAGE_KEY_ENV) == "legacy-body"


def test_tampered_token_fails_closed():
    token = encrypt_text("hello", MESSAGE_KEY_ENV)
    bad = token[:-4] + ("AAAA" if not token.endswith("AAAA") else "BBBB")
    with pytest.raises(RuntimeError):
        decrypt_text(bad, MESSAGE_KEY_ENV)


def test_future_version_fails_closed():
    token = encrypt_text("hello", MESSAGE_KEY_ENV)
    future = token.replace(f"v{KEY_VERSION}:", "v99:", 1)
    with pytest.raises(RuntimeError):
        decrypt_text(future, MESSAGE_KEY_ENV)


def test_missing_key_fails_closed(monkeypatch):
    monkeypatch.delenv(MESSAGE_KEY_ENV)
    with pytest.raises(RuntimeError):
        encrypt_text("x", MESSAGE_KEY_ENV)
    with pytest.raises(RuntimeError):
        decrypt_text("v1:whatever", MESSAGE_KEY_ENV)


def test_invalid_key_fails_closed(monkeypatch):
    monkeypatch.setenv(MESSAGE_KEY_ENV, "not-a-fernet-key")
    with pytest.raises(RuntimeError):
        encrypt_text("x", MESSAGE_KEY_ENV)


def test_dual_key_decrypt_across_rotation(monkeypatch):
    """Simulate a v1 -> v2 rotation: old key kept as _V1 decrypts old tokens,
    new writes carry v2."""
    import app.crypto as crypto_mod

    old_key = generate_key()
    new_key = generate_key()
    monkeypatch.setenv(MESSAGE_KEY_ENV, new_key)
    monkeypatch.setenv(f"{MESSAGE_KEY_ENV}_V1", old_key)
    monkeypatch.setattr(crypto_mod, "KEY_VERSION", 2)

    old_token = "v1:" + Fernet(old_key.encode()).encrypt(b"before").decode()
    assert decrypt_text(old_token, MESSAGE_KEY_ENV) == "before"

    new_token = encrypt_text("after", MESSAGE_KEY_ENV)
    assert new_token.startswith("v2:")
    assert decrypt_text(new_token, MESSAGE_KEY_ENV) == "after"

    # dropping the old key version fails closed for old tokens
    monkeypatch.delenv(f"{MESSAGE_KEY_ENV}_V1")
    with pytest.raises(RuntimeError):
        decrypt_text(old_token, MESSAGE_KEY_ENV)


def test_decrypt_float_round_trip():
    token = encrypt_float(33.4152, GEO_KEY_ENV)
    assert token.startswith("v1:")
    assert decrypt_float(token, GEO_KEY_ENV) == pytest.approx(33.4152)
    assert encrypt_float(None, GEO_KEY_ENV) is None
    assert decrypt_float(None, GEO_KEY_ENV) is None


def test_decrypt_float_non_float_plaintext_wrapped(monkeypatch):
    """M24: decryptable-but-non-float plaintext -> RuntimeError envelope,
    not a bare ValueError (which would 500 outside the error envelope)."""
    token = encrypt_text("not-a-float", GEO_KEY_ENV)
    with pytest.raises(RuntimeError):
        decrypt_float(token, GEO_KEY_ENV)


def test_decrypt_float_rejects_non_string():
    with pytest.raises(RuntimeError):
        decrypt_float(12345, GEO_KEY_ENV)


def test_cipher_cache_reused_and_invalidated(monkeypatch):
    c1 = get_cipher(MESSAGE_KEY_ENV)
    c2 = get_cipher(MESSAGE_KEY_ENV)
    assert c1 is c2  # cached per key value
    other = generate_key()
    monkeypatch.setenv(MESSAGE_KEY_ENV, other)
    c3 = get_cipher(MESSAGE_KEY_ENV)
    assert c3 is not c1  # env change rebuilds — rotation/tests take effect
    assert decrypt_text(encrypt_text("x", MESSAGE_KEY_ENV), MESSAGE_KEY_ENV) == "x"


def test_generate_key_produces_valid_fernet_key():
    key = generate_key()
    assert isinstance(key, str)
    Fernet(key.encode())  # raises if malformed
    assert key != generate_key()  # fresh randomness
