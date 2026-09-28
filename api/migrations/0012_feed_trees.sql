-- 0012_listing_fields: extra create-listing fields (PAR-2 create track) and
-- feed-by-way / trees list support (API-123, API-135).
-- potSize / plantAgeYears: optional AND-125 create-form fields (seedlings).
-- pickupWindowDays: AND-126 create-form field, defaults to 4 days.
-- No new tables: trees are listings with type='tree' (see 0004_listings);
-- /v1/trees filters live tree listings server-side.
ALTER TABLE listings ADD COLUMN IF NOT EXISTS pot_size TEXT;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS plant_age_years TEXT;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS pickup_window_days INT NOT NULL DEFAULT 4;
