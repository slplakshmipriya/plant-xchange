-- 0001_init: baseline migration.
-- Proves the versioned runner works end-to-end. Domain tables arrive with
-- their own tickets (API-010 users, API-020 listings, API-060 ledger, ...).
CREATE TABLE IF NOT EXISTS app_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO app_meta (key, value) VALUES ('schema_baseline', '0001')
ON CONFLICT (key) DO NOTHING;
