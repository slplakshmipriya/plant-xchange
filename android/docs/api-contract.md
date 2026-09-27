# GardenSwap Android API contract (Waves 1–3)

`api/GardenSwapApi.java` is the **frozen client-side contract**. Every screen
builds against it; `api/MockGardenSwapApi.java` implements it with canned
responses where the backend does not exist yet.

## Wave 1 — REAL (integration checkpoint, AND-090)

`api/HttpGardenSwapApi.java` (HttpURLConnection, no new deps) implements the
Wave 1 methods against the real backend. `ApiProvider` selects it when the
git-ignored `local.properties` sets `USE_REAL_API=true` **and**
`API_BASE_URL` (e.g. the Cloud Run URL); otherwise the mock is used.
Callers never change.

| Interface method | Real backend endpoint |
|---|---|
| `getMe` | `GET /v1/users/me` → `OwnerProfile` |
| `upsertProfile` | `POST /v1/users` (body: `ProfileIn`) → `OwnerProfile` |
| `createIdvSession` | `POST /v1/idv/session` → `{session_url, expires_in}` |
| `getIdvStatus` | `GET /v1/users/me` → `idv_status` field (no dedicated endpoint) |
| `registerFcmToken` | `FirebaseMessaging.subscribeToTopic("user_" + uid)` — the backend fans out to the per-user FCM topic; there is intentionally **no** `/v1/devices` endpoint (see `api/app/notify.py`) |

All calls carry a fresh Firebase ID token (bearer), verified server-side per
API-002. Async, main-thread delivery, single-shot callbacks.

### Reconciled drift (client-proposed → real)

Per `DEPENDENCY_TREE.md`, drift is a P0 bug against the drifting side — these
were reconciled client-side in AND-090 and documented in
`api/JsonParsers.java`:

- **`phoneVerified`**: the backend exposes no such field (PII minimization,
  SEC-010). The client derives it from `FirebaseUser.getPhoneNumber() != null`.
  If the backend adds the field later, `JsonParsers` should prefer it.
- **`IdvSession.sessionId`**: `POST /v1/idv/session` returns `session_url`
  only. The client uses the trailing URL token as the provisional session id.
- **`upsertProfile`**: real endpoint is `POST /v1/users` (not the proposed
  `PUT /me`); `PATCH /v1/users/me` also exists.

### Shapes (match `api/openapi.yaml` on `develop`)

- `UserProfile`: `uid`, `display_name`, `avatar_url`, `home_zip`,
  `idv_status` enum string; `phoneVerified` derived client-side (see above)
- `ProfileIn`: nullable `display_name` / `avatar_url` / `home_zip`
  (null = leave unchanged)
- `IdvSession`: `sessionId` (provisional, from URL), `providerUrl`
- `IdvStatus.fromString` maps unknown wire values → `UNVERIFIED`
  (forward-compatible with new backend states)

## Wave 3 — PROPOSED (mock phase)

The backend Wave 3 endpoints do not exist yet; the mock implements these
with canned responses shaped like the proposed contract. At the Wave 3
integration checkpoint, reconcile against `api/openapi.yaml`.

| Interface method | Proposed backend |
|---|---|
| `getTreeDetail` | `GET /v1/trees/{id}` → tree detail (API-050) |
| `setRipeAlert` | `POST /v1/trees/{id}/alerts` → subscribed bool (API-050) |
| `getWallet` | `GET /v1/ledger` → balance + entries (API-060/061) |
| `confirmExchange` | `POST /v1/exchanges/{id}/confirm` → both-sides state (API-060) |
| `getSitters` / `getSitter` | `GET /v1/sitters?zip=` / `GET /v1/sitters/{id}` (API-070) |
| `requestBooking` | `POST /v1/bookings` (API-070) |
| `submitReview` | `POST /v1/bookings/{id}/reviews` (API-072) |
| `getThreads` / `getMessages` / `sendMessage` | `GET/POST /v1/threads…` (API-080) |

## Known gaps (mock phase)

- Avatar: local URI/cache path is passed through as `avatarUrl`; byte upload
  lands with the photo pipeline (API-022, backend Wave 1 — client upload UI
  still pending).
- Phone sign-in itself is real (Firebase Auth); only the profile/IDV
  persistence was mocked before AND-090.
