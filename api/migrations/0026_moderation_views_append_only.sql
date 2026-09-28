-- 0026_moderation_views_append_only: the privileged-view audit log becomes
-- write-once at the DB level, and gains hash chaining for tamper-evidence.
--
-- H3: migration 0021 claimed "append-only (no update/delete paths in the
-- API)" — convention, not enforcement. This migration adds:
--
--   1. A BEFORE UPDATE/DELETE trigger that raises on any mutation of
--      moderation_views. Rows can only be inserted, never changed.
--   2. prev_hash / row_hash columns. The app computes each row's hash as
--      sha256(prev_hash || viewer_uid || thread_id || message_id || reason)
--      (app/moderation.py::_chain_hash), so a rewritten row invalidates
--      every later row. Chain computation stays in Python because it needs
--      no DB crypto extension; the trigger only blocks mutation.
--
-- Caveat: concurrent inserts can briefly share the same prev_hash (both
-- read the tail before either commits). The chain is tamper-evidence, not
-- a total order.
ALTER TABLE moderation_views
    ADD COLUMN IF NOT EXISTS prev_hash TEXT,
    ADD COLUMN IF NOT EXISTS row_hash TEXT;

CREATE OR REPLACE FUNCTION moderation_views_block_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'moderation_views is append-only: % is not allowed', TG_OP;
    RETURN NULL;  -- unreachable; keeps plpgsql happy
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS moderation_views_no_update ON moderation_views;
DROP TRIGGER IF EXISTS moderation_views_no_delete ON moderation_views;
CREATE TRIGGER moderation_views_no_update
    BEFORE UPDATE ON moderation_views
    FOR EACH ROW EXECUTE FUNCTION moderation_views_block_mutation();
CREATE TRIGGER moderation_views_no_delete
    BEFORE DELETE ON moderation_views
    FOR EACH ROW EXECUTE FUNCTION moderation_views_block_mutation();
