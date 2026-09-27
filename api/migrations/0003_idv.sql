-- 0003_idv: ID verification status on users (API-012).
-- ID images/documents are NEVER stored on our infra — only the provider's
-- verdict (this enum). See api/app/idv.py.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS idv_status TEXT NOT NULL DEFAULT 'unverified'
    CHECK (idv_status IN ('unverified', 'pending', 'verified', 'failed'));
