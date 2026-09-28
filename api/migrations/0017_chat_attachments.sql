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
ALTER TABLE messages
    DROP CONSTRAINT IF EXISTS messages_kind_check;
ALTER TABLE messages
    ADD CONSTRAINT messages_kind_check CHECK (kind IN ('text', 'photo'));
