-- 0013_claims: partial-quantity claims lifecycle (R2 claims track).
-- Distinct from the whole-listing claim in API-060: these are per-quantity
-- claim records (e.g. 2kg of a 10kg harvest) with a pending/accepted/declined
-- lifecycle. Claims decrement the listing's available quantity atomically;
-- the listing stays live until the quantity hits 0 or the listing expires.
CREATE TABLE IF NOT EXISTS claims (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    claimer_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    quantity NUMERIC NOT NULL CHECK (quantity > 0),
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'accepted', 'declined', 'cancelled')),
    pickup_start_ms BIGINT,
    pickup_end_ms BIGINT,
    notes TEXT CHECK (notes IS NULL OR char_length(notes) <= 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS claims_listing_idx ON claims (listing_id);
CREATE INDEX IF NOT EXISTS claims_claimer_idx ON claims (claimer_uid, created_at);

-- No-show strikes for the claims pillar. 2 no-shows -> 30-day claim
-- suspension; the shared moderation module (separate track) will absorb this
-- table after merge, so keep the shape boring and conventional.
CREATE TABLE IF NOT EXISTS claim_no_show_strikes (
    uid TEXT PRIMARY KEY REFERENCES users(uid) ON DELETE CASCADE,
    no_shows INTEGER NOT NULL DEFAULT 0,
    suspended_until TIMESTAMPTZ,
    banned_until TIMESTAMPTZ
);
