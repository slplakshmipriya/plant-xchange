-- 0015_pyo_slots: pick-your-own slot windows on tree listings.
-- The tree owner opens slots; pickers claim spots, paying credit_cost
-- credits each (claimed_count tracks filled spots). See app/slots.py.
CREATE TABLE IF NOT EXISTS slots (
    id UUID PRIMARY KEY,
    tree_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    owner_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    day_ms BIGINT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    max_pickers INT NOT NULL CHECK (max_pickers >= 1),
    claimed_count INT NOT NULL DEFAULT 0 CHECK (claimed_count >= 0),
    credit_cost INT NOT NULL DEFAULT 1 CHECK (credit_cost >= 0),
    cash_cents INT CHECK (cash_cents IS NULL OR cash_cents >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (start_ms < end_ms)
);
CREATE INDEX IF NOT EXISTS slots_tree_idx ON slots (tree_id, day_ms);
