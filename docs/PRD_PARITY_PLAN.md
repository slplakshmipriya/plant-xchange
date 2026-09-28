# PRD parity implementation plan

Branch: `prd-parity` (from `main` post ui-feature merge).
Tickets: `garden-swap-app/JIRA_BACKLOG.md` → PHASE: PRD parity (AND-122..165, API-123..150).

## Build order

1. **Critical path — contract** (`prd-parity/contract`): all API surface
   additions below. Merge to `prd-parity` first.
2. **Parallel UI tracks** (after contract merges): feed, create, claim,
   sitter, pyo, trust, notify. Each on its own `prd-parity/<track>` branch in
   its own worktree under `~/workspace/wt/`, then merged into `prd-parity`.

## Hard file-ownership (no agent touches another track's files)

| Track | Files |
|---|---|
| contract | `api/**`, `AndroidManifest.xml`, `app/build.gradle` (firebase-messaging only, if missing) |
| feed | `explore/ExploreActivity.java`, `util/ExploreLogic.java`, `util/ExploreLogicTest.java` |
| create | `listings/CreateListingActivity.java`, `util/CreateListingValidator.java` |
| claim | `ui/ClaimBottomSheet.java`, `listings/ListingDetailActivity.java`, `wallet/ConfirmExchangeActivity.java` |
| sitter | `sitters/SitterProfileActivity.java`, `sitters/ReviewActivity.java` |
| pyo | `trees/TreeListActivity.java` (new), `trees/TreeDetailActivity.java` |
| trust | `chat/ChatActivity.java`, `ui/ReportDialog.java` (new) |
| notify | `notifications/GardenSwapMessagingService.java` (new), `GardenSwapApp.java` |

Cross-track wiring that needs another track's file goes in the owning track's
brief (e.g. feed owns the Explore "Pick" card tap → starts TreeListActivity).

## Contract additions (critical path)

New files in `api/`:

```java
// FeedRequest.java
public final class FeedRequest {
    public final String way;   // nullable: "seedling", "harvest", "pick", "sitting"
    public final int limit;
    public FeedRequest(String way, int limit) { ... }
}

// ClaimRequest.java
public final class ClaimRequest {
    public final int quantity;        // default 1
    public final Long pickupStartMs;  // nullable
    public final Long pickupEndMs;    // nullable
    public final String notes;        // nullable
    public ClaimRequest(int quantity, Long pickupStartMs, Long pickupEndMs, String notes) { ... }
    public static ClaimRequest single() { return new ClaimRequest(1, null, null, null); }
}

// ReportRequest.java
public final class ReportRequest {
    public final String targetType; // "LISTING", "USER", "BOOKING"
    public final String targetId;
    public final String category;   // "spam", "safety", "fraud", "inappropriate", "other"
    public final String details;      // nullable
    public ReportRequest(String targetType, String targetId, String category, String details) { ... }
}
```

`GardenSwapApi` interface additions (keep the existing
`claimListing(String, Callback)`; make impls delegate to the new overload):

```java
void getFeed(FeedRequest request, Callback<List<Listing>> callback);
void listTrees(Callback<List<TreeListing>> callback);
void claimListing(String listingId, ClaimRequest request, Callback<Listing> callback);
void cancelClaim(String listingId, Callback<Listing> callback);
void reportContent(ReportRequest request, Callback<Void> callback);
```

Model changes:

- `Review`: add `reviewerRole` (String, default `"OWNER"`) and
  `verifiedBooking` (boolean, default `false`) + getters + builder setters.
- `ListingInput`: add `potSize` (String, nullable), `plantAge` (String,
  nullable), `pickupWindowDays` (int, default 4) + builder methods.
- `TreeListing`: add `SPRAY_NONE="none"`, `SPRAY_ORGANIC="organic"`,
  `SPRAY_SYNTHETIC="synthetic"`, `SPRAY_UNKNOWN="unknown"` constants.

`HttpGardenSwapApi` (planned endpoints — backend implements per API-123/126/
135/143; mark with a comment until then; mirror the existing JSON parsing
style of the file):

- `GET {base}/feed?way={way}&limit={limit}` → `List<Listing>`
- `GET {base}/trees` → `List<TreeListing>`
- `POST {base}/listings/{id}/claims` body
  `{quantity,pickupStartMs,pickupEndMs,notes}` → `Listing`
- `POST {base}/listings/{id}/claims/cancel` → `Listing`
- `POST {base}/reports` body `{targetType,targetId,category,details}` → void

`MockGardenSwapApi`: implement the new methods with sensible in-memory
behavior (feed → current feed list filtered by way; listTrees → the existing
mock tree; claim with quantity decrements when the model supports it;
cancelClaim restores; reportContent → success). Keep the old no-arg
`getFeed(Callback)` as a deprecated delegating method so existing callers
compile until the feed track rewrites them.

`AndroidManifest.xml`:

```xml
<activity android:name=".trees.TreeListActivity" android:exported="false" />
<service android:name=".notifications.GardenSwapMessagingService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

Verify `firebase-messaging` is in `app/build.gradle` (firebase-bom is used);
add `implementation 'com.google.firebase:firebase-messaging'` if missing.

## Rules for every agent

- No Android/Java toolchain in this VM: do NOT run gradle. Static checks
  only — brace balance on edited files, import/reference sanity.
- Read the files you touch before editing. Follow existing code style
  (programmatic views, `Ui.*` helpers).
- Commit at the end as `Lakshmi Priya
  <slplakshmipriya@users.noreply.github.com>` on your track branch.
- Never touch another track's files (table above). If you're blocked by a
  missing contract piece, stop and report — don't improvise it.
