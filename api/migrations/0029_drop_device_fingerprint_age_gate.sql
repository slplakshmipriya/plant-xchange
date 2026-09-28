-- 0029_drop_device_fingerprint_age_gate: stop collecting device fingerprints (M17)
-- and record age attestation at onboarding (M20c).
--
-- M17: device_fingerprint was stored at signup but nothing ever read it —
-- no anomaly detection, no enforcement — and the Android client never sent
-- the X-Device-Fingerprint header. Decision (documented in
-- api/app/verify.py): stop collecting it. Dropping the column is safe
-- pre-launch (no production data); no API ever returned it (SEC-010).
-- NOTE: PRD.md still lists "Device + phone verification at signup" as a
-- trust pillar — that line needs reconciling with this decision.
--
-- M20c: age gate. GardenSwap requires users to be 13+; the attestation
-- moment is recorded here so the gate is enforced exactly once per user.
ALTER TABLE users DROP COLUMN IF EXISTS device_fingerprint;
ALTER TABLE users ADD COLUMN IF NOT EXISTS age_attested_at TIMESTAMPTZ;
