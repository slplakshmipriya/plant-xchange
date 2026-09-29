# Firebase setup (AND-002)

The app needs a `google-services.json` to build. It is **git-ignored**
(see `android/.gitignore`) — never commit it.

## Where it goes

| Variant | Path |
|---|---|
| debug (default) | `android/app/google-services.json` |
| per-variant | `android/app/src/debug/google-services.json`, `android/app/src/release/google-services.json` |

The single file at `android/app/` covers all variants unless you add
per-variant overrides.

## How to get it

1. Firebase console → create project (or reuse the Garden Swap project).
2. Add an Android app with package name **`com.gardenswap.test`**.
3. Download `google-services.json` and place it per the table above.
4. For phone auth (AND-010, later): add your debug keystore SHA-1 under
   Project settings → Android app. Get it with:
   `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
5. Enable in the console: Authentication (phone), Cloud Messaging.

## What initializes on launch

`GardenSwapApp` (registered in the manifest) initializes FirebaseApp,
Analytics, and Crashlytics, then fetches the FCM token. Token registration
with our backend is currently a no-op stub (`NoOpBackendRegistrar`) — the
real `POST /v1/devices` call lands with the API contract (API-004).

## Test push to a debug device

1. Run the debug build, open Logcat, confirm no Firebase init errors.
2. Firebase console → Cloud Messaging → send a test message to the FCM
   token (log it temporarily, or copy from `FirebaseMessaging.getInstance().getToken()`).
3. Notification should arrive when the app is backgrounded.

## CI

CI has no real `google-services.json`; `.github/workflows/android.yml`
generates a placeholder with the correct package name so
`processDebugGoogleServices` passes. Placeholder keys are fake — push
delivery is not tested in CI.
