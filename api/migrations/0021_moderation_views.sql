-- 0021_moderation_views: audit log of privileged plaintext views.
--
-- The support dashboard (GET /v1/support/threads/{id}/messages) decrypts
-- message bodies server-side for moderation. Every message returned writes
-- one row here: who looked, at which thread/message, why, and when.
-- Rows are append-only (no update/delete paths in the API).
CREATE TABLE IF NOT EXISTS moderation_views (
    id UUID PRIMARY KEY,
    viewer_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    thread_id UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
    message_id UUID REFERENCES messages(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS moderation_views_viewer_idx
    ON moderation_views (viewer_uid, viewed_at);
CREATE INDEX IF NOT EXISTS moderation_views_thread_idx
    ON moderation_views (thread_id);
