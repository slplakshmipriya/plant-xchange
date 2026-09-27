-- 0009_credits: append-only credit ledger + exchange confirmations (API-060).
-- Balances are always derived (SUM(delta)); the ledger is the source of truth.
-- Starter-credit backfill for users predating this migration.
CREATE TABLE IF NOT EXISTS credit_ledger (
    id UUID PRIMARY KEY,
    uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    delta INT NOT NULL CHECK (delta != 0),
    reason TEXT NOT NULL,
    ref_id TEXT,
    idempotency_key TEXT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS credit_ledger_uid_idx ON credit_ledger (uid);
CREATE TABLE IF NOT EXISTS exchange_confirmations (
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    uid TEXT NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (listing_id, uid)
);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS claimer_uid TEXT REFERENCES users(uid);
INSERT INTO credit_ledger (id, uid, delta, reason, ref_id)
SELECT gen_random_uuid(), uid, 3, 'starter', 'backfill-0009'
FROM users
WHERE uid NOT IN (SELECT uid FROM credit_ledger WHERE reason = 'starter');
