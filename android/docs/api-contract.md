# GardenSwap Android API contract (Wave 1)

`api/GardenSwapApi.java` is the **frozen client-side contract**. Every screen
builds against it; `api/MockGardenSwapApi.java` implements it with canned
responses until the backend Wave 1 endpoints exist.

## Interface → backend endpoint map (for the Wave 1 integration checkpoint)

| Interface method | Planned backend (API-010/011/012) |
|---|---|
| `getMe` | `GET /me` → `UserProfile` |
| `upsertProfile` | `PUT /me` (body: `ProfileUpdate`) → `UserProfile` |
| `createIdvSession` | `POST /idv/sessions` → `IdvSession { sessionId, providerUrl }` |
| `getIdvStatus` | `GET /idv/status` → `IdvStatus` enum `{unverified, pending, verified, failed}` |
| `registerFcmToken` | `POST /devices/fcm-token` → 204 |

All calls require a Firebase ID token (bearer), verified server-side per
API-002. Async, main-thread delivery, single-shot callbacks.

## Shapes (must match `api/openapi.yaml` when backend Wave 1 lands)

- `UserProfile`: `userId`, `displayName`, `avatarUrl`, `homeZip`,
  `phoneVerified: bool`, `idvStatus: enum`
- `ProfileUpdate`: nullable `displayName` / `avatarUrl` / `homeZip`
  (null = leave unchanged)
- `IdvSession`: `sessionId`, `providerUrl`
- `IdvStatus.fromString` maps unknown wire values → `UNVERIFIED`
  (forward-compatible with new backend states)

## Swap-in plan

At the integration checkpoint, a real HTTP implementation of
`GardenSwapApi` replaces the mock via `ApiProvider.set(...)`. The interface
**must not change** — any contract drift is a P0 bug against the drifting
side (see `DEPENDENCY_TREE.md`).

## Known gaps (mock phase)

- Avatar: local URI/cache path is passed through as `avatarUrl`; byte upload
  lands with the photo pipeline (API-022, Wave 3).
- `registerFcmToken` is a mock no-op; real backend persists the token.
- Phone sign-in itself is real (Firebase Auth); only the profile/IDV
  persistence is mocked.
