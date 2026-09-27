-- 0004_listings: listings for seedling/harvest/tree exchange (API-020).
-- SEC-010: geo_lat/geo_lon are the TRUE coordinates — API responses always
-- return fuzzed values (see app/listings.py::fuzz_location). No address column
-- exists; exact meetup details are exchanged in chat only (Wave 3+).
CREATE TABLE IF NOT EXISTS listings (
    id UUID PRIMARY KEY,
    owner_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('seedling', 'harvest', 'tree')),
    photos TEXT[] NOT NULL DEFAULT '{}',
    variety TEXT,
    quantity NUMERIC,
    unit TEXT,
    credit_cost INT NOT NULL CHECK (credit_cost BETWEEN 1 AND 3),
    pickup_window TSTZRANGE,
    expires_at TIMESTAMPTZ,
    geo_lat DOUBLE PRECISION,
    geo_lon DOUBLE PRECISION,
    spray_disclosure TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft'
        CHECK (status IN ('draft', 'live', 'claimed', 'completed', 'expired', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS listings_owner_idx ON listings (owner_uid);
CREATE INDEX IF NOT EXISTS listings_status_expires_idx ON listings (status, expires_at);
