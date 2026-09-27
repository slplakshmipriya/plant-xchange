-- 0011_messaging: listing-scoped threads + messages (API-080).
-- One thread per (listing, user): a conversation between the listing owner
-- and the user who opened the thread.
CREATE TABLE IF NOT EXISTS threads (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    created_by TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (listing_id, created_by)
);
CREATE INDEX IF NOT EXISTS threads_listing_idx ON threads (listing_id);
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    thread_id UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
    sender_uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS messages_thread_idx ON messages (thread_id, created_at, id);
