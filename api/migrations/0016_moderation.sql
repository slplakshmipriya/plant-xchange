-- 0016_moderation: trust & safety — reports, strikes, enforcement, disputes (R2 track).
-- One user is pinned to users(uid); the enum checks mirror the API-layer enums
-- in app/moderation.py. `enforcement.pillar` is '*' for bans (all pillars).
CREATE TABLE IF NOT EXISTS reports (
    id UUID PRIMARY KEY,
    reporter_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    target_type TEXT NOT NULL CHECK (target_type IN ('LISTING','USER','BOOKING')),
    target_id TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('spam','safety','fraud','inappropriate','other')),
    details TEXT NOT NULL CHECK (char_length(details) BETWEEN 1 AND 2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS reports_target_idx ON reports (target_type, target_id);
CREATE INDEX IF NOT EXISTS reports_reporter_idx ON reports (reporter_uid, created_at);

CREATE TABLE IF NOT EXISTS strikes (
    id UUID PRIMARY KEY,
    user_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    pillar TEXT NOT NULL,
    reason TEXT NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS strikes_user_pillar_idx ON strikes (user_uid, pillar, verified);

CREATE TABLE IF NOT EXISTS enforcement (
    id UUID PRIMARY KEY,
    user_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    pillar TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('suspension','ban')),
    expires_at TIMESTAMPTZ,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS enforcement_user_idx ON enforcement (user_uid, pillar);

CREATE TABLE IF NOT EXISTS disputes (
    id UUID PRIMARY KEY,
    exchange_id TEXT NOT NULL,
    reporter_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    details TEXT NOT NULL CHECK (char_length(details) BETWEEN 1 AND 2000),
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','resolved')),
    outcome TEXT CHECK (outcome IN ('upheld','rejected')),
    reversal_credits INTEGER NOT NULL DEFAULT 0 CHECK (reversal_credits >= 0),
    resolved_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS disputes_exchange_idx ON disputes (exchange_id);
CREATE INDEX IF NOT EXISTS disputes_reporter_idx ON disputes (reporter_uid, created_at);
