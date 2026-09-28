-- 0022_geo_encryption: listings.geo_lat/geo_lon now hold Fernet ciphertext.
--
-- Cutover note: no production database exists (migrations have never run
-- against real Postgres), so there is NO backfill — fresh deploys store
-- ciphertext from day one. Dev/test databases should be recreated.
-- DOUBLE PRECISION -> TEXT; existing dev values cast to their text form
-- (the app re-encrypts on next write; fail-closed decrypt rejects them).
ALTER TABLE listings ALTER COLUMN geo_lat TYPE TEXT USING geo_lat::text;
ALTER TABLE listings ALTER COLUMN geo_lon TYPE TEXT USING geo_lon::text;
