-- 0006_want_list: seasonal want-list for match-driven discovery (API-030).
-- Matching is variety substring (case-insensitive) + optional type filter.
CREATE TABLE IF NOT EXISTS want_list (
    id UUID PRIMARY KEY,
    user_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    variety TEXT NOT NULL,
    types TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS want_list_user_idx ON want_list (user_uid);
