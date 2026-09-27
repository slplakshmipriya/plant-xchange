-- 0002_users: user profiles (API-010).
-- uid is the Firebase Auth uid (verified by middleware, never client-chosen).
-- phone_hash is a salted-domain SHA-256 of the E.164 phone number, set only
-- via the /v1/auth/verify flow (API-011) — never accepted from clients.
-- SEC-010: phone_hash / device_fingerprint are NEVER returned by any API.
CREATE TABLE IF NOT EXISTS users (
    uid TEXT PRIMARY KEY,
    phone_hash TEXT UNIQUE,
    display_name TEXT,
    avatar_url TEXT,
    home_zip TEXT,
    device_fingerprint TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
