-- 0023_slot_claims: per-(slot, claimer) claim records for PYO slots.
-- One row per spot claimed: a repeat claim by the same user on the same
-- slot is rejected via the UNIQUE(slot_id, claimer_uid) constraint, so
-- claimed_count can no longer be double-incremented for a single payment.
-- See app/slots.py (claim_slot does the INSERT + increment in one
-- transaction: INSERT ... ON CONFLICT DO NOTHING, 409 when it fails).
CREATE TABLE IF NOT EXISTS slot_claims (
    slot_id UUID NOT NULL REFERENCES slots(id) ON DELETE CASCADE,
    claimer_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (slot_id, claimer_uid)
);
CREATE INDEX IF NOT EXISTS slot_claims_slot_idx ON slot_claims (slot_id);
CREATE INDEX IF NOT EXISTS slot_claims_claimer_idx ON slot_claims (claimer_uid);
