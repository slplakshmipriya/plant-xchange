# Privacy / PII minimization (SEC-010)

The rule, enforced by `api/tests/test_pii.py` (fails closed — extend the
audit when adding endpoints):

**No API response may contain `phone_hash`, `device_fingerprint`, or exact
geo coordinates. Ever.** Serializers are the enforcement point
(`app/users.py::public_profile` / `owner_profile`,
`app/listings.py::public_listing`); each asserts the forbidden fields are
absent, and `test_pii.py` recursively scans every response schema for leaks.

## Per-endpoint exposure

| Endpoint | Exposed | Explicitly withheld |
|---|---|---|
| `GET /v1/users/{uid}` (public) | uid, display_name, avatar_url, idv_status | home_zip, phone_hash, device_fingerprint |
| `GET /v1/users/me`, `POST/PATCH /v1/users` | above + home_zip, created_at (owner only) | phone_hash, device_fingerprint |
| `POST /v1/auth/verify` | uid, verified | phone_hash (stored hashed only) |
| `GET /v1/listings/{id}` | listing fields, **fuzzed** geo_lat/geo_lon (~0.5 mi jitter), owner_uid | true coordinates, owner contact details |
| `POST /v1/idv/*` | session_url, idv_status | ID documents (never touch our infra — provider-hosted) |
| `PUT /v1/users/me/notification-prefs` | categories, quiet_hours | — |
| `POST /v1/uploads/*` | key, public_url, thumb_url, size, content_type | EXIF GPS (stripped server-side on finalize) |

Notes:

- `phone_hash` is a domain-separated SHA-256 (`gs-phone-v1:`), set only via
  the verified Firebase `phone_number` claim — never accepted from clients.
- True listing coordinates live in Postgres but are **never serialized**;
  `fuzz_location()` jitters every response. Exact meetup details stay in
  chat until the exchange-confirm flow (later wave) gates them.
- Uploaded photos are re-saved with the EXIF GPS IFD removed; other EXIF is
  preserved.
- Firebase uids may appear in structured logs (needed for debugging); they
  are authentication identifiers, not PII under this rule. `phone_hash` and
  `device_fingerprint` are never logged.
- Deletion: `DELETE /v1/users/me` (Wave 2, EPIC-P2-4) must cascade to
  listings/uploads/prefs and anonymize ledger references. Until then,
  deletion requests go through support with DB access.
