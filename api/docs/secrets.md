# Secrets management — GardenSwap API (SEC-001)

**Rule: no secret ever lands in this repo.** Config comes from the environment
(`api/app/config.py`); production values live in Google Secret Manager and are
injected at deploy time by `api/cloudbuild.yaml --set-secrets`.

## Secret → env mapping

| Env var | Secret Manager name | Used by | Notes |
|---|---|---|---|
| `DATABASE_URL` | `gardenswap-database-url` | `app/db.py` | Neon Postgres connection string (`?sslmode=require`) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | `gardenswap-firebase-sa` | Cloud Run env (future: custom claims / FCM) | Service-account JSON, injected as env |
| `FIREBASE_PROJECT_ID` | plain env var (not secret) | `app/auth.py` | Project ID is not sensitive |
| `STRIPE_SECRET_KEY` | `gardenswap-stripe-secret` (Wave 4, API-071) | payments | Test + live keys as separate versions |
| `STRIPE_WEBHOOK_SECRET` | `gardenswap-stripe-webhook` (Wave 4) | webhook verification | Rotated per endpoint |
| `MESSAGE_ENCRYPTION_KEY` | `gardenswap-message-encryption-key` | `app/crypto.py` (chat message bodies) | Fernet key — mint with `generate_key()`; see rotation below |
| `GEO_ENCRYPTION_KEY` | `gardenswap-geo-encryption-key` | `app/crypto.py` (listing true coordinates) | Fernet key — mint with `generate_key()`; see rotation below |
| `MESSAGE_ENCRYPTION_KEY_V<n>` / `GEO_ENCRYPTION_KEY_V<n>` | `gardenswap-*-encryption-key` prior versions | `app/crypto.py` (dual-key decrypt) | Previous key versions, kept only during rotation |
| `SWEEP_SECRET` | `gardenswap-sweep-secret` | `app/main.py` (`/v1/internal/sweep`) | Shared secret, `hmac.compare_digest`; 503 when unset |
| `IDV_WEBHOOK_SECRET` | `gardenswap-idv-webhook` | `app/idv.py` | HMAC for the IDV provider webhook; fail-closed when unset |
| `SUPPORT_UIDS` | plain env var (not secret) | `app/moderation.py` | Comma-separated uid allowlist for support routes; fail-closed when empty |

Non-secret tunables (`RATE_LIMIT_PER_MIN`, `LOG_LEVEL`, `PORT`) are plain env vars.

## Local development

1. `cp api/.env.example api/.env` and fill in values. `.env` is git-ignored.
2. Never print secrets to the terminal in chat/PRs. If a secret is pasted
   anywhere shared, treat it as leaked and rotate immediately (below).

## Rotation runbook

1. Create the new secret version:
   `gcloud secrets versions add <name> --data-file=- <<<"$NEW_VALUE"`
2. Redeploy (Cloud Build picks up `:latest`): `gcloud builds submit` or merge
   to `develop` and let the trigger run.
3. Verify: `GET /healthz` 200; exercise one authenticated call (`GET /me`).
4. Disable (don't destroy) the old version:
   `gcloud secrets versions disable <N> --secret=<name>`.
   Destroy after 7 days of clean operation.

Target cadence: database password every 90 days; Stripe keys on team change;
Firebase service-account key yearly.

## Minting encryption keys

`MESSAGE_ENCRYPTION_KEY` and `GEO_ENCRYPTION_KEY` MUST be minted with
`app/crypto.py::generate_key()` — never hand-roll, derive, or reuse a key:

```bash
cd api && python -c "from app.crypto import generate_key; print(generate_key())"
```

Store the output directly in Secret Manager (`gardenswap-message-encryption-key`
/ `gardenswap-geo-encryption-key`). Never paste a key into chat, a PR, or a
log; a pasted key is burned — rotate immediately (leak procedure below).

## Encryption-key rotation (versioned, dual-key)

Tokens carry a version prefix (`v1:<fernet-token>`); `app/crypto.py` decrypts
by version — the current version from the plain env var, older versions from
`<VAR>_V<n>` (e.g. `MESSAGE_ENCRYPTION_KEY_V1`). Tokens minted before
versioning (no prefix) are treated as v1. Rotation therefore never requires
offline re-encryption of all rows:

1. Mint the new key with `generate_key()` (above).
2. Add a new Secret Manager version; configure the deployment with the new
   key as `MESSAGE_ENCRYPTION_KEY` (resp. `GEO_ENCRYPTION_KEY`) and the old
   key as `MESSAGE_ENCRYPTION_KEY_V1` (resp. `GEO_ENCRYPTION_KEY_V1`).
   Bump `KEY_VERSION` in `app/crypto.py` (1 → 2) so new writes carry `v2:`.
3. Deploy. Reads of old tokens use the `_V1` key (dual-key decrypt); all new
   writes carry the new prefix. Writes re-encrypt-on-write automatically —
   every updated row migrates itself.
4. Verify migration: sample ciphertext prefixes in the DB until no `v1:`
   (or unprefixed) tokens remain for that field.
5. Remove the `_V1` env var and disable its Secret Manager version. Reads of
   a version with no configured key fail closed (`RuntimeError`), so do not
   remove it early.

## Key loss = permanent data loss — backup procedure

Losing **all** versions of `MESSAGE_ENCRYPTION_KEY` makes every chat message
body permanently undecryptable; losing `GEO_ENCRYPTION_KEY` does the same to
all listing coordinates. There is no recovery: `decrypt_text`/`decrypt_float`
raise, and every read path that touches the field (listing serializers, chat
serializers, the support dashboard, the data export) fails closed with HTTP
500. The only remedy is restoring the exact key bytes.

Backup requirements:
1. Secret Manager versioning is ON for both key secrets — never destroy old
   versions until step 5 of the rotation above is complete.
2. At mint time, store an offline copy of the key in the team's
   HSM-backed/offline vault (sealed envelope equivalent). The offline copy
   is the last resort for total Secret Manager loss.
3. Test-restore annually: decrypt a canary row with the offline copy.

## If a secret leaks (committed, pasted in chat, logged)

1. **Revoke/rotate first**, investigate second — the old value is burned.
2. `git filter-repo` / BFG the repo history if it was committed; force-push
   all affected branches (coordinate — this rewrites history).
3. Check access logs for misuse during the exposure window
   (Cloud Logging: filter by the affected service/DB user).
4. File a post-mortem note in this doc's history; add a gitleaks rule if the
   leak class isn't covered.

## CI enforcement

`.github/workflows/api.yml` runs gitleaks on every PR/push (SEC-002). The
build fails on any finding. The only allowlisted path is `api/.env.example`
(empty values by construction).

The same workflow runs a `pip-audit` job against `api/requirements.txt` —
it fails on ANY known vulnerability in the pinned set (triage accepted-risk
items in the `IGNORED` map with reason + owner + date).
