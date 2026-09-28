-- 0017_chat_attachments: photo attachments on listing-scoped threads (CHAT track).
--
-- Messages gain a kind ("text" | "photo") so clients can render attachments
-- distinctly. Photo messages store the URL in both `body` (keeps the
-- existing NOT NULL / length CHECK untouched) and `photo_url` for an
-- explicit accessor. Threads are unchanged: their participants are derived
-- from (created_by, listing owner) at serialization time, so no new
-- participant table is needed.
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'text';
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS photo_url TEXT;
-- The kind CHECK is dropped and re-added so its definition is canonical even
-- on databases where an earlier variant exists. The DO block makes the
-- re-add idempotent: with two migration runners racing (H9), a bare
-- "DROP IF EXISTS + ADD CONSTRAINT" pair fails the loser with a duplicate
-- constraint; the pg_constraint guard skips the ADD when it already exists.
-- (run_migrations also serializes runners with an advisory lock; this is
-- belt-and-braces.)
DO $$
BEGIN
    ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_kind_check;
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE c.conname = 'messages_kind_check'
          AND t.relname = 'messages'
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT messages_kind_check CHECK (kind IN ('text', 'photo'));
    END IF;
END $$;
