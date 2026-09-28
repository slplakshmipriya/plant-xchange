-- 0025_uploads_registry: persistent record of photo-upload finalize state.
--
-- C7: GET /v1/uploads/public/{key} must not serve raw bytes that were PUT
-- but never finalized (finalize is where EXIF GPS stripping happens).
-- PUT /v1/uploads/raw/{key} inserts finalized=FALSE; POST /v1/uploads/finalize
-- flips it to TRUE only after EXIF stripping succeeds; serve_public 404s
-- unless the registry row is finalized.
CREATE TABLE IF NOT EXISTS uploads (
    key TEXT PRIMARY KEY,
    owner_uid TEXT NOT NULL,
    finalized BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS uploads_owner_idx ON uploads (owner_uid);
