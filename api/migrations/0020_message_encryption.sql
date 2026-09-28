-- 0020_message_encryption: messages.body now holds Fernet ciphertext.
--
-- Cutover note: no production database exists (migrations have never run
-- against real Postgres), so there is NO backfill — fresh deploys store
-- ciphertext from day one. Dev/test databases should be recreated.
--
-- The old plaintext length CHECK (1..2000 chars) cannot hold ciphertext:
-- Fernet adds ~57 bytes of overhead plus base64 expansion, so a 2000-char
-- message encrypts to ~2.8k chars. The bound is widened to 4096.
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_body_check;
ALTER TABLE messages
    ADD CONSTRAINT messages_body_check CHECK (char_length(body) BETWEEN 1 AND 4096);
