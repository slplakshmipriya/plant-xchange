-- 0020_message_encryption: messages.body now holds Fernet ciphertext.
--
-- Cutover note: no production database exists (migrations have never run
-- against real Postgres), so there is NO backfill — fresh deploys store
-- ciphertext from day one. Dev/test databases should be recreated.
--
-- The old plaintext length CHECK (1..2000 chars) cannot hold ciphertext:
-- Fernet adds ~57 bytes of overhead plus base64 expansion, so a 2000-char
-- message encrypts to ~2.8k chars. The bound is widened to 4096.
--
-- The re-add is guarded by a pg_constraint check so it is idempotent: with
-- two migration runners racing (H9), a bare "DROP IF EXISTS + ADD
-- CONSTRAINT" pair fails the loser with a duplicate constraint; the guard
-- skips the ADD when it already exists. (run_migrations also serializes
-- runners with an advisory lock; this is belt-and-braces.)
DO $$
BEGIN
    ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_body_check;
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE c.conname = 'messages_body_check'
          AND t.relname = 'messages'
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT messages_body_check
            CHECK (char_length(body) BETWEEN 1 AND 4096);
    END IF;
END $$;
