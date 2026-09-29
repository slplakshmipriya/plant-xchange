# UAT Harness — design and journey catalog

Branch: `test/uat-harness` (off `test/prd-parity-rename`). Debug builds only —
nothing here ships in release.

## The idea

Pepper can't tap the phone directly (no ADB/UI automation from here), so the
app carries its own UAT pilot: scripted **journeys** that drive the real
`GardenSwapApi` — the exact code path the UI uses — and assert the outcomes a
user would see on screen. Journeys run headless inside the app; a report is
produced; the report comes back for review.

Two ways to run:

1. **On-device console** (debug build): install the debug APK and tap the
   **"UAT Console"** launcher icon (debug builds only — it's a second icon
   next to the app). Tap "Run all" / "Run photo regression" / any single
   journey. Results render inline (PASS/FAIL per journey, with the failing
   step's detail). The full text report goes to logcat (tag `UAT`), to the
   app's files dir (`uat-report.txt`), and to the clipboard via the
   "Copy report" button — paste it back for review.

2. **Remote trigger** (how Pepper drives it): send an FCM data message with
   `type=uat_run` and `journey=<id|all|photo-regression>`. The debug-only hook
   in `GardenSwapMessagingService` launches the console and auto-runs. The
   report handoff is currently manual (copy from logcat/files dir); the
   `UatReporter` payload is shaped so a future `POST /v1/debug/uat-report`
   backend endpoint can ingest it without app changes.

What this verifies: API contract, JSON parsing, validators, upload pipeline,
business rules (claim guards, credit floor), and data the UI renders.
What it does NOT verify: pixels, layout, tap targets — that still needs eyes
on the device (the screenshots you've been sending).

## Journey catalog (24 journeys, by PRD pillar)

### Pillar 1 — Seedling swap
| id | journey |
|---|---|
| `listing-create-1-photo` | Upload 1 photo (sign→PUT→finalize) → publish → appears in feed with photo |
| `listing-create-2-photos` | Upload 2 photos → publish → detail returns both (gallery data) |
| `listing-create-free` | Free harvest listing publishes (1-credit floor) |
| `feed-loads` | Feed loads; cards have renderable fields |
| `listing-detail` | Detail loads with variety/giver info |
| `listing-cancel` | Giver cancels own listing |
| `claim-own-rejected` | Claiming your own listing → `cannot_claim_own` |
| `claim-cancel-documented` | Two-party claim cancel (counterparty pending) |
| `swap-history` | `/v1/me/swaps` loads |

### Pillar 2 — Plant sitting
| id | journey |
|---|---|
| `sitter-browse` | List sitters by ZIP → open sitter detail |
| `bookings-list` | Booking list loads |

### Pillar 3 — Pick-your-own
| id | journey |
|---|---|
| `trees-load` | Tree list → tree detail |
| `tree-slots` | Slot list for a tree |

### Pillar 4 — Harvest swap
| id | journey |
|---|---|
| `harvest-events` | Harvest log for a listing |

### Cross-cutting
| id | journey |
|---|---|
| `wallet-loads` | Balance + ledger |
| `credit-expiry` | Seasonal expiry info |
| `want-list-roundtrip` | Add → list → remove a want |
| `want-matches` | Matches (primary discovery) load |
| `chat-send-text` | Threads load → send text |
| `chat-send-photo` | Upload bytes → send as attachment |
| `profile-roundtrip` | `getMe` + feed sanity |
| `avatar-upload` | Avatar upload returns absolute https URL |
| `idv-status` | IDV status loads |
| `notif-prefs` | Notification prefs round-trip |

## Known gaps (documented in code)

- **Two-party flows** (giver accepts/declines a claimer's claim, both confirm a
  swap, chat between two users) need a second account. The journeys that need
  this say so in their notes; the runner executes everything else with the
  current session. Next step: second-session support in `UatContext`.
- **Photo rendering pixels** (Glide actually painting, gallery thumbnails
  tappable) are not asserted headless — the console + your screenshots cover
  that.
- **Push notification content** (want-list match push, ripe alerts) needs a
  real push round-trip; the harness covers the API side (matches, slots).

## Files

- `app/src/debug/java/com/gardenswap/test/uat/` — `UatJourney`, `UatStep`,
  `UatFailure`, `UatContext`, `UatRunner`, `UatRegistry`,
  `JourneysListings`, `JourneysClaims`, `JourneysSocial`, `JourneysMoney`,
  `UatRunnerActivity`, `UatReporter`
- `app/src/debug/AndroidManifest.xml` — console activity (debug only)
- `GardenSwapMessagingService` — `type=uat_run` FCM hook (debug-guarded,
  reflection-based so release keeps compiling)
