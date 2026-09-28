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
