# Plant Xchange (Garden Swap) — Android app

Android client for the Garden Swap network: hyperlocal seedling swaps,
backyard harvest sharing, and plant sitting, all settled in fungible
network credits.

- Package: `com.gardenswap.app`
- Language: Java, single-`app` module
- Backend: Python/FastAPI on Google Cloud Run + Neon Postgres, Firebase Auth
  (contract: `api/openapi.yaml` in the backend repo)

## Tech stack

| | |
|---|---|
| Android Gradle Plugin | 8.5.2 |
| compileSdk / targetSdk | 35 |
| minSdk | 23 |
| Firebase BOM | 33.7.0 (Auth, Messaging, Analytics, Crashlytics) |
| UI | AppCompat + Material Components, programmatic views |
| Tests | JUnit 4 JVM unit tests (`app/src/test`) |

## Prerequisites

- JDK 17
- Android SDK with platform 35 and build-tools installed
- A Firebase project with an Android app registered for `com.gardenswap.app`
  (Auth + Cloud Messaging enabled)

## Setup

1. Clone and open the project in Android Studio (or build with `./gradlew`).
2. Copy the local config template and fill in your values:
   ```bash
   cp local.properties.template local.properties
   ```
   `local.properties` is git-ignored — it holds your SDK path, the API base
   URL, and any release-signing secrets. Never commit it or any keystore.
3. Add your `google-services.json` (from the Firebase console) at
   `app/google-services.json`. This file is git-ignored; each developer
   uses their own Firebase project config.
4. Build:
   ```bash
   ./gradlew assembleDebug
   ```

## Tests

```bash
./gradlew testDebugUnitTest
```

`assembleDebug` depends on `testDebugUnitTest`, so every debug build runs the
JVM suite and a failing test fails the build. No emulator needed.

## Project layout

```
app/src/main/java/com/gardenswap/app/
  api/          Retrofit-free HTTP client (HttpGardenSwapApi), JSON parsers,
                and a MockGardenSwapApi for UI-only work
  listings/     listing create / detail / browse
  harvest/     harvest event logging
  trees/       tree listings and ripe alerts
  wallet/      credit balance and ledger
  sitters/     sitter directory, booking, reviews
  chat/        listing-scoped threads and messages
  profile/     profile + IDV
```

## API modes

`ApiProvider` selects the backend:

- **Mock** (`MockGardenSwapApi`) — in-memory data, no network. For UI
  iteration.
- **HTTP** (`HttpGardenSwapApi`) — talks to the Cloud Run API using the
  Firebase ID token. Set `API_BASE_URL` in `local.properties`.

The client is contract-first: request/response shapes follow the checked-in
OpenAPI spec, and `docs/api-contract.md` records every place where client
semantics were reconciled with the backend (credit pricing, claim/cancel
endpoints, harvest notes, ripe-alert semantics, etc.).

## Release signing

Generate a keystore once, keep it **outside** the repo, and reference it from
`local.properties`:

```properties
RELEASE_KEYSTORE_PATH=/absolute/path/to/release.keystore
RELEASE_KEYSTORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

See `local.properties.template` for the full list of supported keys.
