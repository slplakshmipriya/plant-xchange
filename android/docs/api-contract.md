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

# GardenSwap Android API contract (Wave 2)

`api/GardenSwapApi.java` is the **frozen client-side contract**. Every screen
builds against it; `api/MockGardenSwapApi.java` implements it with canned
responses until the real HTTP client replaces it at the integration checkpoint.

## Interface → backend endpoint map

| Interface method | Backend (API-010/011/012/020) |
|---|---|
| `getMe` | `GET /v1/users/me` → `UserProfile` |
| `upsertProfile` | `POST /v1/users` + `PATCH /v1/users/me` → `UserProfile` |
| `createIdvSession` | `POST /v1/idv/session` → `IdvSession { sessionId, providerUrl }` |
| `getIdvStatus` | `GET /v1/users/me`.idv_status (no dedicated endpoint) |
| `registerFcmToken` | No backend endpoint — client subscribes to FCM topic `user_{uid}` on login (see notify.py) |
| `createListing` | `POST /v1/listings` (201) → `Listing` |
| `getListing` | `GET /v1/listings/{listing_id}` → `Listing` |
| `patchListing` | `PATCH /v1/listings/{listing_id}` → `Listing` (illegal state moves are 422) |
| `listMyListings` | Client-proposed — backend Wave 2 should add `GET /v1/listings?owner=me` |
| `getWantList` / `addWant` / `removeWant` | Client-proposed (API-030 builds these) |
| `getMatches` | Client-proposed — backend match engine (API-030) is authoritative; mock delegates to `util/WantMatcher` |
| `getHarvestEvents` / `logHarvestEvent` | Client-proposed (AND-040); backend harvest rules (API-040) define the wire form |

## Shapes

- `Listing`: `id`, `ownerUid`, `type` (seedling/harvest/tree), `photos[]`,
  `variety`, `quantity`, `unit`, `creditCost` (1–3), `pickupStartMs/EndMs`,
  `expiresAtMs`, `geoLat/Lon` (**fuzzed** by the backend, ~0.5 mi),
  `sprayDisclosure`, `status` (draft/live/claimed/completed/expired/cancelled)
- `ListingInput`: create payload; photo (≥1) + spray disclosure required
- `ListingPatch`: nullable fields; `status` drives the state machine
- `Listing.free` / `ListingInput.free`: client-proposed (AND-040) — the
  backend decides the wire semantics (0-credit listing vs 1-credit floor);
  the real HTTP client must map accordingly, never silently drop the flag
- `HarvestEvent`: `id`, `listingId`, signed `delta` (+ picked / − taken),
  `note`, `createdAtMs`
- `ListingStatus.fromString` maps unknown wire values → `null`
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
