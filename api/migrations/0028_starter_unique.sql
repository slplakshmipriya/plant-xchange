-- 0028_starter_unique: one starter grant per user, ever (H12).
--
-- ensure_starter_credits() grants the 3-credit bootstrap with the
-- deterministic idempotency key "starter:<uid>", and add_entry() already
-- does INSERT ... ON CONFLICT (idempotency_key) DO NOTHING. This unique
-- partial index is defense-in-depth behind that key: any direct insert of a
-- second reason='starter' row for the same uid (e.g. a raced 0009-style
-- backfill) is rejected at the DB level instead of double-granting.
CREATE UNIQUE INDEX IF NOT EXISTS credit_ledger_starter_once
    ON credit_ledger (uid) WHERE reason = 'starter';
