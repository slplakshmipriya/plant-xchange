-- 0027_message_body_check: widen the ciphertext bound on messages.body.
--
-- H10: the 2000-char pydantic bound counts *code points*, but Fernet
-- ciphertext size scales with UTF-8 *bytes*. A 2000-emoji message (8000
-- bytes) encrypts to ~10.7k chars — past the 4096-char CHECK from 0020 —
-- and the CheckViolation surfaced as an unhandled 500.
--
-- Remediation is two-layered:
--
--   1. This migration widens the CHECK to 16384 chars of ciphertext
--      (defense in depth; a violation is now caught and mapped to 422 in
--      app/msg.py instead of 500ing).
--   2. The API layer enforces a 2800-*byte* plaintext bound
--      (app/msg.py::MAX_MESSAGE_BYTES, checked in send_message -> 422).
--      2800 bytes of plaintext encrypts to ~3.8k chars, so the widened
--      CHECK is never the first line of defense.
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_body_check;
ALTER TABLE messages
    ADD CONSTRAINT messages_body_check CHECK (char_length(body) BETWEEN 1 AND 16384);
