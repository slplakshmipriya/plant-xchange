-- 0030_want_variety_trgm: trigram index for DB-level want-list matching (M9).
--
-- notify_matches / find_matches_for_listing match on variety substring
-- (case-insensitive, either direction). Scanning the whole want_list table
-- in Python on every listing go-live does not scale; the pg_trgm GIN index
-- lets Postgres resolve the `lower(variety) LIKE '%…%'` arm of the match
-- from the index instead of a sequential scan. (The reverse-substring arm
-- uses position(), which is literal but not indexable — rare in practice.)
--
-- Runs inside the migration transaction (plain CREATE INDEX; CONCURRENTLY
-- cannot run in a transaction). Standalone: no dependency on 0026–0029.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS want_list_variety_trgm_idx
    ON want_list USING gin (lower(variety) gin_trgm_ops);
