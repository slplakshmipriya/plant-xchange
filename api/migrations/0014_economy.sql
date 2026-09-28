-- 0014_economy: seasonal credit expiry support (R2 credit economy).
--
-- Credit seasons (UTC): Mar 1–Sep 30 and Oct 1–Feb 28/29. Credits expire at
-- the end of the season they were issued in. Expiry is DERIVED from
-- credit_ledger.created_at (see season_end() below and the FIFO view in
-- app/credits.py) — the ledger stays append-only: never UPDATE/DELETE rows
-- for expiry.
--
-- season_end(ts): end of the season containing ts (last millisecond, UTC),
-- mirroring the Python season_end_ms() helper the API uses.

CREATE OR REPLACE FUNCTION season_end(ts TIMESTAMPTZ) RETURNS TIMESTAMPTZ
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE
    WHEN EXTRACT(MONTH FROM ts AT TIME ZONE 'UTC') BETWEEN 3 AND 9 THEN
      make_timestamptz(EXTRACT(YEAR FROM ts AT TIME ZONE 'UTC')::int,
                       9, 30, 23, 59, 59.999, 'UTC')
    WHEN EXTRACT(MONTH FROM ts AT TIME ZONE 'UTC') >= 10 THEN
      -- Oct–Dec: season ends on the last day of next February.
      make_timestamptz(EXTRACT(YEAR FROM ts AT TIME ZONE 'UTC')::int + 1,
                       3, 1, 0, 0, 0, 'UTC') - INTERVAL '1 millisecond'
    ELSE
      -- Jan–Feb: season ends on the last day of this February.
      make_timestamptz(EXTRACT(YEAR FROM ts AT TIME ZONE 'UTC')::int,
                       3, 1, 0, 0, 0, 'UTC') - INTERVAL '1 millisecond'
  END
$$;

-- Entries are always scanned per-uid in created_at order for the derived
-- balance/expiry view; this covers the expiry lookups too.
CREATE INDEX IF NOT EXISTS credit_ledger_uid_created_idx
    ON credit_ledger (uid, created_at);

COMMENT ON TABLE credit_ledger IS
    'Append-only. Balances are derived (SUM of unexpired FIFO lots, see season_end()); never UPDATE/DELETE for expiry.';
