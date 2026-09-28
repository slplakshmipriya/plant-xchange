"""At-rest field encryption (AES-256-GCM via Fernet).

Values are encrypted in Python before they reach the store (Postgres or the
in-memory fakes) and decrypted at the last responsible moment before use —
e.g. right before ``fuzz_location`` runs, or inside the serializer that
serves a participant. Keys come from environment variables, read fresh on
every call (same convention as ``SUPPORT_UIDS`` in moderation.py) so tests
can monkeypatch them.

Fail closed everywhere:
- missing or invalid key -> ``RuntimeError`` (refuse to handle secrets)
- tampered token / wrong key -> ``RuntimeError`` (Fernet authentication)

Key registry:
- ``MESSAGE_ENCRYPTION_KEY``: chat message bodies (msg.py)
- ``GEO_ENCRYPTION_KEY``: listing true coordinates (listings.py)

This is application-layer encryption, not end-to-end: the server holds the
keys, so privileged flows (the support moderation dashboard) can still
decrypt. What it defeats is DB-layer exposure — dumps, backup leaks, a
read-only SQL injection, or someone browsing tables in the DB console.
A fully compromised app server defeats it by design.
"""

from __future__ import annotations

import os

from cryptography.fernet import Fernet, InvalidToken

MESSAGE_KEY_ENV = "MESSAGE_ENCRYPTION_KEY"
GEO_KEY_ENV = "GEO_ENCRYPTION_KEY"


def generate_key() -> str:
    """Mint a fresh Fernet key for ops. Store in env / Secret Manager, never in the repo."""
    return Fernet.generate_key().decode("utf-8")


def get_cipher(env_var: str) -> Fernet:
    """Build the cipher from ``env_var``. Read fresh every call so tests can
    monkeypatch the env; missing/invalid key raises (fail closed)."""
    raw = os.environ.get(env_var, "")
    if not raw:
        raise RuntimeError(
            f"{env_var} is not set — refusing to handle encrypted fields without a key"
        )
    try:
        return Fernet(raw.encode("utf-8"))
    except Exception as exc:
        raise RuntimeError(f"{env_var} is not a valid Fernet key") from exc


def encrypt_text(plaintext: str, env_var: str) -> str:
    """Encrypt ``plaintext`` -> base64 token string. Raises on missing key."""
    return get_cipher(env_var).encrypt(plaintext.encode("utf-8")).decode("utf-8")


def decrypt_text(token: str, env_var: str) -> str:
    """Decrypt a token. Raises ``RuntimeError`` on tampered token or wrong key."""
    try:
        return get_cipher(env_var).decrypt(token.encode("utf-8")).decode("utf-8")
    except InvalidToken as exc:
        raise RuntimeError(
            "ciphertext failed authentication — wrong key or tampered data"
        ) from exc


def encrypt_float(value: float | None, env_var: str) -> str | None:
    """Encrypt an optional coordinate. ``None`` stays ``None`` (no geo)."""
    if value is None:
        return None
    return encrypt_text(repr(float(value)), env_var)


def decrypt_float(token: str | None, env_var: str) -> float | None:
    """Decrypt an optional coordinate. ``None`` stays ``None``; a non-string
    or unauthentic token raises (fail closed)."""
    if token is None:
        return None
    if not isinstance(token, str):
        raise RuntimeError("expected ciphertext string for encrypted coordinate")
    return float(decrypt_text(token, env_var))
