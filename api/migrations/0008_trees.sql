-- 0008_trees: visit rules on tree listings (API-050).
-- Ripe windows reuse expires_at / pickup_window; per-picker limits and rules
-- live here as free text (structured slots are a later wave).
ALTER TABLE listings ADD COLUMN IF NOT EXISTS visit_rules TEXT;
