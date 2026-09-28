-- 0018_notify_prefs: per-user quiet-hours window + FCM device tokens (R2 notifications).
-- Extends notification_prefs (0005) with a configurable quiet-hours window
-- (HH:MM, 24h, evaluated in USER_TZ); the legacy quiet_hours boolean stays
-- as the enable toggle. Adds device_tokens for token-targeted FCM sends,
-- registered via POST /v1/users/me/fcm-token.
ALTER TABLE notification_prefs
    ADD COLUMN IF NOT EXISTS quiet_hours_start TEXT NOT NULL DEFAULT '21:00'
        CHECK (quiet_hours_start ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    ADD COLUMN IF NOT EXISTS quiet_hours_end TEXT NOT NULL DEFAULT '08:00'
        CHECK (quiet_hours_end ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$');
CREATE TABLE IF NOT EXISTS device_tokens (
    user_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    token TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'android',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_uid, token)
);
CREATE INDEX IF NOT EXISTS device_tokens_user_idx ON device_tokens (user_uid, last_seen_at DESC);
