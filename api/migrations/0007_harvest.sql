-- 0007_harvest: remaining-quantity tracking + audit log for harvest listings (API-040).
-- remaining_qty NULL means "not yet tracked" and reads as quantity.
CREATE TABLE IF NOT EXISTS harvest_events (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    recorder_uid TEXT NOT NULL,
    delta_kg NUMERIC NOT NULL CHECK (delta_kg > 0),
    remaining_after NUMERIC NOT NULL CHECK (remaining_after >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS harvest_events_listing_idx ON harvest_events (listing_id);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS remaining_qty NUMERIC;
