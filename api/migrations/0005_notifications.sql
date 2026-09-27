-- 0005_notifications: prefs + send log for the notification service (API-081).
-- categories is a map like {"harvest_alerts": true, "wantlist_matches": false};
-- a category absent from the map defaults to opted-in.
-- notification_log doubles as the dedupe + rate-cap ledger.
CREATE TABLE IF NOT EXISTS notification_prefs (
    user_uid TEXT PRIMARY KEY REFERENCES users(uid) ON DELETE CASCADE,
    categories JSONB NOT NULL DEFAULT '{}',
    quiet_hours BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS notification_log (
    id BIGSERIAL PRIMARY KEY,
    user_uid TEXT NOT NULL,
    category TEXT NOT NULL,
    ref TEXT NOT NULL DEFAULT '',
    outcome TEXT NOT NULL DEFAULT 'sent',
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS notification_log_user_idx ON notification_log (user_uid, sent_at);
