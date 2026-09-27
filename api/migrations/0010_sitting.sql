-- 0010_sitting: sitter profiles, sitting requests, reviews (API-070).
-- Reviews: exactly one per sitting, owner-authored, only after completion.
CREATE TABLE IF NOT EXISTS sitter_profiles (
    uid TEXT PRIMARY KEY REFERENCES users(uid) ON DELETE CASCADE,
    bio TEXT NOT NULL DEFAULT '',
    experience_years INT NOT NULL DEFAULT 0 CHECK (experience_years >= 0),
    service_radius_miles REAL NOT NULL DEFAULT 5 CHECK (service_radius_miles > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS sitting_requests (
    id UUID PRIMARY KEY,
    owner_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    sitter_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    plant_count INT NOT NULL CHECK (plant_count > 0),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'requested'
        CHECK (status IN ('requested', 'accepted', 'declined', 'completed', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date),
    CHECK (owner_uid != sitter_uid)
);
CREATE INDEX IF NOT EXISTS sitting_requests_sitter_idx ON sitting_requests (sitter_uid);
CREATE INDEX IF NOT EXISTS sitting_requests_owner_idx ON sitting_requests (owner_uid);
CREATE TABLE IF NOT EXISTS sitting_reviews (
    id UUID PRIMARY KEY,
    sitting_id UUID NOT NULL UNIQUE REFERENCES sitting_requests(id) ON DELETE CASCADE,
    reviewer_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS sitting_reviews_sitter_idx ON sitting_reviews (reviewer_uid);
