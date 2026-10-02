package com.gardenswap.test.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gardenswap.test.util.WantMatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory mock of {@link GardenSwapApi} (Waves 1–3).
 *
 * <p>The backend Wave 1 endpoints (API-010/011/012) are real on
 * {@code develop}; the Wave 3 endpoints (trees, wallet, sitters, chat) do
 * not exist yet, so those methods return canned responses shaped like the
 * proposed contract (see {@code docs/api-contract.md}). Results are delivered
 * on the main thread after a short artificial latency.
 *
 * <p>Replaced by the real HTTP client at the integration checkpoints;
 * {@link GardenSwapApi} callers must not change then.
 */
public class MockGardenSwapApi implements GardenSwapApi {

    private static final String TAG = "MockGardenSwapApi";
    private static final long LATENCY_MS = 400;

    private final Handler main = new Handler(Looper.getMainLooper());
    private UserProfile profile = UserProfile.builder("mock-user-1")
            .phoneVerified(true)
            .idvStatus(IdvStatus.UNVERIFIED)
            .build();
    private IdvStatus mockIdvStatus = IdvStatus.UNVERIFIED;

    /** In-memory listing store (AND-020). Keyed by id, insertion order. */
    private final Map<String, Listing> listings = new LinkedHashMap<>();

    /** In-memory want-list (AND-030). */
    private final Map<String, WantItem> wants = new LinkedHashMap<>();

    /** In-memory harvest events per listing (AND-040). */
    private final Map<String, List<HarvestEvent>> harvestEvents = new LinkedHashMap<>();

    /** In-memory pick-your-own slots per tree (PRD parity, r2). */
    private final Map<String, List<Slot>> slotsByTree = new LinkedHashMap<>();

    /** In-memory notification preferences (PRD parity, r2). */
    private NotificationPrefs notificationPrefs = NotificationPrefs.defaultAllOn();

    public MockGardenSwapApi() {
        seedSampleListings();
    }

    /**
     * Sample listings from other gardeners so the feed/matches UI has
     * something to render in the mock phase. Owned by other uids, so they
     * never appear in {@link #listMyListings}.
     */
    private void seedSampleListings() {
        seedListing(Listing.builder("sample-1")
                .ownerUid("gardener-ana")
                .ownerDisplayName("Ana")
                .type(ListingType.SEEDLING)
                .photos(new ArrayList<String>())
                .variety("Cherokee Purple tomato")
                .quantity(6.0).unit("starts")
                .creditCost(2)
                .expiresAtMs(System.currentTimeMillis() + 6 * 86_400_000L)
                .sprayDisclosure("none")
                .status(ListingStatus.LIVE)
                .build());
        seedListing(Listing.builder("sample-2")
                .ownerUid("gardener-ben")
                .ownerDisplayName("Ben")
                .type(ListingType.HARVEST)
                .photos(new ArrayList<String>())
                .variety("Meyer lemons")
                .quantity(8.0).unit("lbs")
                .creditCost(1)
                .expiresAtMs(System.currentTimeMillis() + 2 * 86_400_000L)
                .sprayDisclosure("Neem oil, 4 weeks ago.")
                .status(ListingStatus.LIVE)
                .build());
        seedListing(Listing.builder("sample-3")
                .ownerUid("gardener-ana")
                .ownerDisplayName("Ana")
                .type(ListingType.SEEDLING)
                .photos(new ArrayList<String>())
                .variety("Genovese basil")
                .quantity(12.0).unit("starts")
                .creditCost(1)
                .expiresAtMs(System.currentTimeMillis() + 10 * 86_400_000L)
                .sprayDisclosure("none")
                .status(ListingStatus.LIVE)
                .build());
    }

    /** Harness hook: seed a listing into the mock store. */
    public void seedListing(Listing listing) {
        listings.put(listing.getId(), listing);
    }

    /** Harness hook: force the status returned by {@link #getIdvStatus}. */
    public void setMockIdvStatus(IdvStatus status) {
        mockIdvStatus = status == null ? IdvStatus.UNVERIFIED : status;
    }

    @Override
    public void getMe(Callback<UserProfile> callback) {
        emit(callback, profile);
    }

    @Override
    public void upsertProfile(ProfileUpdate update, Callback<UserProfile> callback) {
        UserProfile.Builder builder = profile.toBuilder();
        if (update.getDisplayName() != null) {
            builder.displayName(update.getDisplayName());
        }
        if (update.getAvatarUrl() != null) {
            builder.avatarUrl(update.getAvatarUrl());
        }
        if (update.getHomeZip() != null) {
            builder.homeZip(update.getHomeZip());
        }
        profile = builder.build();
        Log.d(TAG, "upsertProfile -> name=" + profile.getDisplayName()
                + " zip=" + profile.getHomeZip());
        emit(callback, profile);
    }

    @Override
    public void createIdvSession(Callback<IdvSession> callback) {
        emit(callback, new IdvSession(
                "mock-session-" + System.currentTimeMillis(), "mock://idv/start"));
    }

    @Override
    public void uploadAvatar(byte[] imageBytes, String contentType, Callback<String> callback) {
        // Mock: pretend the upload succeeded and return a fake public URL.
        emit(callback, "https://mock.gardenswap.test/uploads/mock-avatar.jpg");
    }

    @Override
    public void getSwaps(Callback<java.util.List<Swap>> callback) {
        emit(callback, com.gardenswap.test.profile.SwapSamples.swaps());
    }

    @Override
    public void getIdvStatus(Callback<IdvStatus> callback) {
        emit(callback, mockIdvStatus);
    }

    @Override
    public void registerFcmToken(String userId, String fcmToken, Callback<Void> callback) {
        Log.d(TAG, "registerFcmToken user=" + userId + " (mock: dropped)");
        emit(callback, null);
    }

    // ------------------------------------------------------------ Wave 3 (proposed)

    /** In-memory PYO tree; shared by {@link #getTreeDetail} and {@link #listTrees}. */
    private TreeListing mockTree(String treeId) {
        long now = System.currentTimeMillis();
        long day = 24 * 3_600_000L;
        return TreeListing.builder(treeId)
                .variety("Meyer lemon")
                .ripeWindow(now - day, now + 14 * day)
                .perPickerLimit("5 lbs per visit")
                .pickupRules("Owner present for your first 3 visits. Bring your own bag. "
                        + "Park on the street, not the driveway.")
                .sprayDisclosure("Neem oil only, last applied 3 weeks ago.")
                .ownerDisplayName("Priya")
                .addressUnlocked(false)
                .ripeAlertsSubscribed(false)
                .build();
    }

    @Override
    public void getTreeDetail(String treeId, Callback<TreeListing> callback) {
        emit(callback, mockTree(treeId));
    }

    @Override
    public void listTrees(Callback<List<TreeListing>> callback) {
        List<TreeListing> trees = new ArrayList<>();
        trees.add(mockTree("mock-tree-1"));
        emit(callback, trees);
    }

    @Override
    public void setRipeAlert(String treeId, boolean subscribe, Callback<Boolean> callback) {
        emit(callback, subscribe);
    }

    @Override
    public void getWallet(Callback<Wallet> callback) {
        long now = System.currentTimeMillis();
        long day = 24 * 3_600_000L;
        java.util.List<LedgerEntry> entries = new java.util.ArrayList<>();
        entries.add(new LedgerEntry("e1", 3, LedgerEntry.Kind.ISSUED,
                "Starter credits", null, now - 30 * day, now + 60 * day));
        entries.add(new LedgerEntry("e2", 2, LedgerEntry.Kind.ISSUED,
                "Tomato seedling swap", "xchg-1", now - 5 * day, now + 120 * day));
        entries.add(new LedgerEntry("e3", -2, LedgerEntry.Kind.SPENT,
                "Zucchini harvest claim", "xchg-2", now - 2 * day, null));
        emit(callback, new Wallet(3, now + 60 * day, 2, entries));
    }

    @Override
    public void confirmExchange(String exchangeId, Callback<ExchangeConfirmation> callback) {
        // Mock: this side confirms; the other side is still pending.
        emit(callback, new ExchangeConfirmation(exchangeId, true, false, 2, false));
    }

    @Override
    public void getSitters(String zip, Callback<java.util.List<SitterProfile>> callback) {
        java.util.List<SitterProfile> sitters = new java.util.ArrayList<>();
        sitters.add(new SitterProfile("s1", "Maya", new String[]{"watering", "repotting"},
                5.0, "credits", 5, true, 4.8, 23, 31,
                new java.util.HashSet<>(java.util.Arrays.asList("2026-10-03"))));
        sitters.add(new SitterProfile("s2", "Dev", new String[]{"watering"},
                10.0, "usd", 3, false, 4.5, 6, 8,
                java.util.Collections.emptySet()));
        emit(callback, sitters);
    }

    @Override
    public void upsertSitterProfile(SitterProfileIn profile, Callback<Void> callback) {
        emit(callback, null);
    }

    @Override
    public void getSitterReviews(String sitterId,
                                 Callback<java.util.List<Review>> callback) {
        java.util.List<Review> reviews = new java.util.ArrayList<>();
        reviews.add(new Review(5, null,
                "Great communication and my plants looked happy when I got back.",
                "OWNER", true, "2026-09-20T14:05:00+00:00"));
        reviews.add(new Review(4, null,
                "Reliable watering while we were away. Would book again.",
                "OWNER", true, "2026-08-11T09:30:00+00:00"));
        emit(callback, reviews);
    }

    @Override
    public void setSitterAvailability(java.util.List<String> availableIsoDates,
                                      Callback<Void> callback) {
        emit(callback, null);
    }

    @Override
    public void getSitter(String sitterId, Callback<SitterProfile> callback) {
        getSitters("", new Callback<java.util.List<SitterProfile>>() {
            @Override
            public void onSuccess(java.util.List<SitterProfile> result) {
                for (SitterProfile s : result) {
                    if (s.getSitterId().equals(sitterId)) {
                        callback.onSuccess(s);
                        return;
                    }
                }
                callback.onError(new ApiException("not_found", "No such sitter"));
            }

            @Override
            public void onError(ApiException e) {
                callback.onError(e);
            }
        });
    }

    @Override
    public void updateSitterServices(String[] services, Callback<Void> callback) {
        // Mock: nothing persisted; the own-profile editor shows the edited
        // selection locally until the screen reloads.
        Log.d(TAG, "updateSitterServices " + java.util.Arrays.toString(services));
        emit(callback, null);
    }

    @Override
    public void requestBooking(BookingRequest request, Callback<Booking> callback) {
        // Mock: subtotal scales with the number of picked dates at a flat
        // mock rate; the fee flip puts the 18% on the sitter's payout.
        int days = Math.max(1, request.getDates().size());
        int subtotal = 1500 * days; // mock: one day at the sitter's rate
        int fee = Math.round(subtotal * 0.18f); // 18% platform fee, inside the 15-20% band
        emit(callback, new Booking("b1", request.getSitterId(), BookingStatus.REQUESTED,
                request.getDates(), request.getServices(),
                subtotal, fee, subtotal, subtotal - fee,
                request.getCareInstructions()));
    }

    @Override
    public void submitReview(String bookingId, Review review, Callback<Void> callback) {
        Log.d(TAG, "submitReview booking=" + bookingId + " rating=" + review.getRating());
        emit(callback, null);
    }

    @Override
    public void getThreads(Callback<java.util.List<ChatThread>> callback) {
        long now = System.currentTimeMillis();
        java.util.List<ChatThread> threads = new java.util.ArrayList<>();
        threads.add(new ChatThread("t1", "Cherokee Purple tomato · 2 credits", "claimed",
                "Priya", "See you Saturday!", now - 3_600_000L, 1));
        threads.add(new ChatThread("t2", "Meyer lemon pick · 1 credit", "live",
                "Dev", "Ripe window starts Monday", now - 86_400_000L, 0));
        emit(callback, threads);
    }

    @Override
    public void openThread(String listingId, Callback<ChatThread> callback) {
        emit(callback, new ChatThread("mock-thread-" + listingId,
                "Mock listing", "live", "Priya", "", System.currentTimeMillis(), 0));
    }

    @Override
    public void getMessages(String threadId, Callback<java.util.List<ChatMessage>> callback) {
        long now = System.currentTimeMillis();
        java.util.List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(new ChatMessage("m0", threadId, "System", false,
                ChatMessage.Kind.SYSTEM, "Claim confirmed — chat unlocked.", now - 7_200_000L));
        messages.add(new ChatMessage("m1", threadId, "Priya", false,
                ChatMessage.Kind.TEXT, "Hi! Saturday morning works for pickup.", now - 3_700_000L));
        messages.add(new ChatMessage("m2", threadId, "You", true,
                ChatMessage.Kind.TEXT, "See you Saturday!", now - 3_600_000L));
        emit(callback, messages);
    }

    @Override
    public void sendMessage(String threadId, String text, Callback<ChatMessage> callback) {
        emit(callback, new ChatMessage("m" + System.currentTimeMillis(), threadId,
                "You", true, ChatMessage.Kind.TEXT, text, System.currentTimeMillis()));
    }

    @Override
    public void deleteMessage(String threadId, String messageId,
            Callback<ChatMessage> callback) {
        // Mock: only the "You" message is deletable, mirroring the
        // sender-only rule. Returns the tombstone.
        java.util.List<ChatMessage> seed = new java.util.ArrayList<>();
        seed.add(new ChatMessage("m2", threadId, "You", true,
                ChatMessage.Kind.TEXT, "See you Saturday!",
                System.currentTimeMillis() - 3_600_000L));
        for (ChatMessage m : seed) {
            if (m.getMessageId().equals(messageId) && m.isMine()) {
                emit(callback, m.asDeleted());
                return;
            }
        }
        emitError(callback, new ApiException("not_your_message",
                "Only the sender can delete a message."));
    }

    // ------------------------------------------------------------ Wave 2 (proposed)
    @Override
    public void createListing(ListingInput input, Callback<Listing> callback) {
        Listing listing = Listing.builder("mock-listing-" + UUID.randomUUID())
                .ownerUid(profile.getUserId())
                .type(input.getType())
                .photos(input.getPhotos())
                .variety(input.getVariety())
                .quantity(input.getQuantity())
                .unit(input.getUnit())
                .creditCost(input.getCreditCost())
                .pickupWindow(input.getPickupStartMs(), input.getPickupEndMs())
                .expiresAtMs(input.getExpiresAtMs())
                .geo(input.getGeoLat(), input.getGeoLon())
                .sprayDisclosure(input.getSprayDisclosure())
                .status(ListingStatus.LIVE)
                .free(input.isFree())
                .potSize(input.getPotSize())
                .plantAge(input.getPlantAge())
                .pickupWindowDays(input.getPickupWindowDays())
                .build();
        listings.put(listing.getId(), listing);
        Log.d(TAG, "createListing id=" + listing.getId() + " type=" + input.getType());
        emit(callback, listing);
    }

    @Override
    public void getListing(String listingId, Callback<Listing> callback) {
        Listing listing = listings.get(listingId);
        if (listing == null) {
            emitError(callback, new ApiException("listing_not_found", "No such listing"));
            return;
        }
        emit(callback, listing);
    }

    @Override
    public void patchListing(String listingId, ListingPatch patch, Callback<Listing> callback) {
        Listing current = listings.get(listingId);
        if (current == null) {
            emitError(callback, new ApiException("listing_not_found", "No such listing"));
            return;
        }
        // The real backend enforces the state machine (422 on illegal moves);
        // the mock applies the patch permissively for UI development.
        Listing.Builder builder = Listing.builder(current.getId())
                .ownerUid(current.getOwnerUid())
                .type(current.getType())
                .photos(current.getPhotos())
                .variety(patch.getVariety() != null ? patch.getVariety() : current.getVariety())
                .quantity(patch.getQuantity() != null ? patch.getQuantity() : current.getQuantity())
                .unit(patch.getUnit() != null ? patch.getUnit() : current.getUnit())
                .creditCost(patch.getCreditCost() != null ? patch.getCreditCost() : current.getCreditCost())
                .pickupWindow(current.getPickupStartMs(), current.getPickupEndMs())
                .expiresAtMs(patch.getExpiresAtMs() != null ? patch.getExpiresAtMs() : current.getExpiresAtMs())
                .geo(current.getGeoLat(), current.getGeoLon())
                .sprayDisclosure(patch.getSprayDisclosure() != null
                        ? patch.getSprayDisclosure() : current.getSprayDisclosure())
                .status(patch.getStatus() != null ? patch.getStatus() : current.getStatus())
                .createdAtMs(current.getCreatedAtMs())
                .free(current.isFree());
        Listing updated = builder.build();
        listings.put(listingId, updated);
        emit(callback, updated);
    }

    @Override
    public void claimListing(String listingId, Callback<Listing> callback) {
        claimListing(listingId, ClaimRequest.single(), callback);
    }

    @Override
    public void claimListing(String listingId, ClaimRequest request,
            Callback<Listing> callback) {
        Listing current = listings.get(listingId);
        if (current == null) {
            emitError(callback, new ApiException("listing_not_found", "No such listing"));
            return;
        }
        if (current.getStatus() != ListingStatus.LIVE) {
            emitError(callback, new ApiException("listing_not_live",
                    "Someone just claimed this listing"));
            return;
        }
        int qty = Math.max(1, request.quantity);
        Double remaining = current.getRemainingQty();
        ListingStatus status = ListingStatus.CLAIMED;
        String claimer = profile.getUserId();
        Double newRemaining = null;
        if (remaining != null) {
            // Partial-claim model: decrement tracked quantity; the listing
            // stays LIVE until the last unit is claimed.
            newRemaining = Math.max(0, remaining - qty);
            if (newRemaining > 0) {
                status = ListingStatus.LIVE;
                claimer = null;
            }
        }
        Long pickupStart = request.pickupStartMs != null
                ? request.pickupStartMs : current.getPickupStartMs();
        Long pickupEnd = request.pickupEndMs != null
                ? request.pickupEndMs : current.getPickupEndMs();
        if (request.notes != null && !request.notes.trim().isEmpty()) {
            Log.d(TAG, "claimListing id=" + listingId + " notes=" + request.notes.trim());
        }
        Listing claimed = rebuild(current, status, claimer, newRemaining,
                pickupStart, pickupEnd);
        listings.put(listingId, claimed);
        emit(callback, claimed);
    }

    @Override
    public void cancelClaim(String listingId, Callback<Listing> callback) {
        Listing current = listings.get(listingId);
        if (current == null) {
            emitError(callback, new ApiException("listing_not_found", "No such listing"));
            return;
        }
        if (!profile.getUserId().equals(current.getClaimerUid())) {
            emitError(callback, new ApiException("not_claimer",
                    "Only the claimer can cancel the claim"));
            return;
        }
        // Restore: back to LIVE, held quantity released to the full amount.
        Double restored = current.getQuantity() != null
                ? current.getQuantity() : current.getRemainingQty();
        Listing restoredListing = rebuild(current, ListingStatus.LIVE, null, restored,
                current.getPickupStartMs(), current.getPickupEndMs());
        listings.put(listingId, restoredListing);
        emit(callback, restoredListing);
    }

    /** Rebuild a listing with claim/cancel state applied. */
    private Listing rebuild(Listing current, ListingStatus status, String claimerUid,
            Double remainingQty, Long pickupStartMs, Long pickupEndMs) {
        return Listing.builder(current.getId())
                .ownerUid(current.getOwnerUid())
                .type(current.getType())
                .photos(current.getPhotos())
                .variety(current.getVariety())
                .quantity(current.getQuantity())
                .unit(current.getUnit())
                .creditCost(current.getCreditCost())
                .pickupWindow(pickupStartMs, pickupEndMs)
                .expiresAtMs(current.getExpiresAtMs())
                .geo(current.getGeoLat(), current.getGeoLon())
                .sprayDisclosure(current.getSprayDisclosure())
                .status(status)
                .createdAtMs(current.getCreatedAtMs())
                .free(current.isFree())
                .claimerUid(claimerUid)
                .remainingQty(remainingQty)
                .visitRules(current.getVisitRules())
                .potSize(current.getPotSize())
                .plantAge(current.getPlantAge())
                .pickupWindowDays(current.getPickupWindowDays())
                .build();
    }

    @Override
    public void reportContent(ReportRequest request, Callback<Void> callback) {
        Log.d(TAG, "reportContent type=" + request.targetType
                + " id=" + request.targetId + " category=" + request.category);
        emit(callback, null);
    }

    @Override
    public void cancelListing(String listingId, Callback<Listing> callback) {
        Listing current = listings.get(listingId);
        if (current == null) {
            emitError(callback, new ApiException("listing_not_found", "No such listing"));
            return;
        }
        if (!profile.getUserId().equals(current.getOwnerUid())) {
            emitError(callback, new ApiException("not_owner", "Only the owner can cancel"));
            return;
        }
        Listing cancelled = Listing.builder(current.getId())
                .ownerUid(current.getOwnerUid())
                .type(current.getType())
                .photos(current.getPhotos())
                .variety(current.getVariety())
                .quantity(current.getQuantity())
                .unit(current.getUnit())
                .creditCost(current.getCreditCost())
                .pickupWindow(current.getPickupStartMs(), current.getPickupEndMs())
                .expiresAtMs(current.getExpiresAtMs())
                .geo(current.getGeoLat(), current.getGeoLon())
                .sprayDisclosure(current.getSprayDisclosure())
                .status(ListingStatus.CANCELLED)
                .createdAtMs(current.getCreatedAtMs())
                .free(current.isFree())
                .claimerUid(current.getClaimerUid())
                .remainingQty(current.getRemainingQty())
                .visitRules(current.getVisitRules())
                .potSize(current.getPotSize())
                .plantAge(current.getPlantAge())
                .pickupWindowDays(current.getPickupWindowDays())
                .build();
        listings.put(listingId, cancelled);
        emit(callback, cancelled);
    }

    @Override
    public void listMyListings(Callback<List<Listing>> callback) {
        List<Listing> mine = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (profile.getUserId().equals(listing.getOwnerUid())) {
                mine.add(listing);
            }
        }
        emit(callback, mine);
    }

    @Override
    public void getWantList(Callback<List<WantItem>> callback) {
        emit(callback, new ArrayList<>(wants.values()));
    }

    @Override
    public void addWant(String variety, Callback<WantItem> callback) {
        String trimmed = variety == null ? "" : variety.trim();
        if (trimmed.isEmpty()) {
            emitError(callback, new ApiException("invalid_variety", "Variety is required"));
            return;
        }
        WantItem item = new WantItem("mock-want-" + UUID.randomUUID(),
                trimmed, System.currentTimeMillis());
        wants.put(item.getId(), item);
        emit(callback, item);
    }

    @Override
    public void removeWant(String wantId, Callback<Void> callback) {
        if (wants.remove(wantId) == null) {
            emitError(callback, new ApiException("want_not_found", "No such want"));
            return;
        }
        emit(callback, null);
    }

    @Override
    public void getMatches(Callback<List<Listing>> callback) {
        // Mock-phase matching delegates to WantMatcher; the backend engine
        // (API-030) replaces this at the integration checkpoint.
        List<Listing> others = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (!profile.getUserId().equals(listing.getOwnerUid())) {
                others.add(listing);
            }
        }
        emit(callback, WantMatcher.matches(new ArrayList<>(wants.values()), others));
    }

    /**
     * Ranked feed for Explore (planned: API-123). Mock-phase: live listings
     * from other gardeners, insertion order, filtered by
     * {@link FeedRequest#way}. The backend replaces ordering when the real
     * endpoint lands.
     */
    @Override
    public void getFeed(FeedRequest request, Callback<List<Listing>> callback) {
        List<Listing> feed = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (profile.getUserId().equals(listing.getOwnerUid())) {
                continue;
            }
            if (listing.getStatus() != ListingStatus.LIVE) {
                continue;
            }
            if (!wayMatches(request.way, listing.getType())) {
                continue;
            }
            feed.add(listing);
        }
        int limit = request.limit <= 0 ? feed.size() : Math.min(request.limit, feed.size());
        emit(callback, feed.subList(0, limit));
    }

    private static boolean wayMatches(String way, ListingType type) {
        if (way == null || way.trim().isEmpty()) {
            return true;
        }
        switch (way.trim().toLowerCase()) {
            case "seedling":
                return type == ListingType.SEEDLING;
            case "harvest":
                return type == ListingType.HARVEST;
            case "pick":
                return type == ListingType.TREE;
            case "sitting":
                return false; // sitters live on a separate API
            default:
                return true;
        }
    }

    /**
     * @deprecated Use {@link #getFeed(FeedRequest, Callback)}. Kept so
     * existing callers compile until the feed track rewrites them.
     */
    @Deprecated
    public void getFeed(Callback<List<Listing>> callback) {
        getFeed(new FeedRequest(null, 50), callback);
    }

    @Override
    public void getHarvestEvents(String listingId, Callback<List<HarvestEvent>> callback) {
        List<HarvestEvent> events = harvestEvents.get(listingId);
        emit(callback, events == null ? new ArrayList<HarvestEvent>() : new ArrayList<>(events));
    }

    @Override
    public void logHarvestEvent(String listingId, double delta, String note,
            Callback<HarvestEvent> callback) {
        if (!listings.containsKey(listingId)) {
            emitError(callback, new ApiException("listing_not_found", "No such listing"));
            return;
        }
        if (delta == 0) {
            emitError(callback, new ApiException("invalid_delta", "Delta cannot be zero"));
            return;
        }
        HarvestEvent event = new HarvestEvent(
                "mock-event-" + UUID.randomUUID(), listingId, delta,
                note == null ? "" : note, System.currentTimeMillis());
        List<HarvestEvent> events = harvestEvents.get(listingId);
        if (events == null) {
            events = new ArrayList<>();
            harvestEvents.put(listingId, events);
        }
        events.add(event);
        emit(callback, event);
    }

    // ------------------------------------------------------------ PRD parity (r2)

    @Override
    public void acceptClaim(String listingId, String claimId,
            Callback<Listing> callback) {
        Listing current = listings.get(listingId);
        if (current == null) {
            emitError(callback, new ApiException("listing_not_found", "No such listing"));
            return;
        }
        // Owner accepts: the exchange completes; the claimer stays recorded.
        Log.d(TAG, "acceptClaim id=" + listingId + " claim=" + claimId);
        Listing accepted = rebuild(current, ListingStatus.COMPLETED,
                current.getClaimerUid(), current.getRemainingQty(),
                current.getPickupStartMs(), current.getPickupEndMs());
        listings.put(listingId, accepted);
        emit(callback, accepted);
    }

    @Override
    public void declineClaim(String listingId, String claimId,
            Callback<Listing> callback) {
        Listing current = listings.get(listingId);
        if (current == null) {
            emitError(callback, new ApiException("listing_not_found", "No such listing"));
            return;
        }
        // Owner declines: back to LIVE, claimer released.
        Log.d(TAG, "declineClaim id=" + listingId + " claim=" + claimId);
        Listing declined = rebuild(current, ListingStatus.LIVE, null,
                current.getRemainingQty(),
                current.getPickupStartMs(), current.getPickupEndMs());
        listings.put(listingId, declined);
        emit(callback, declined);
    }

    @Override
    public void listBookings(String role, boolean completedOnly,
            Callback<List<Booking>> callback) {
        // Fixed list; the Booking model carries no party names (only ids).
        long now = System.currentTimeMillis();
        long day = 24 * 3_600_000L;
        List<Booking> bookings = new ArrayList<>();
        Booking completed = new Booking("b-completed-1", "s1", BookingStatus.COMPLETED,
                isoRange(now - 30 * day, 8),
                new String[]{"watering", "harvesting"}, 2400 * 8, 432 * 8,
                2400 * 8, 2400 * 8 - 432 * 8,
                "Water the tomatoes every morning.");
        bookings.add(completed);
        Booking upcoming = new Booking("b-upcoming-1", "s2", BookingStatus.CONFIRMED,
                isoRange(now + 7 * day, 8),
                new String[]{"watering"}, 1500 * 8, 270 * 8,
                1500 * 8, 1500 * 8 - 270 * 8, "Feed the cat too.");
        bookings.add(upcoming);
        if (completedOnly) {
            List<Booking> filtered = new ArrayList<>();
            for (Booking b : bookings) {
                if (b.getStatus() == BookingStatus.COMPLETED) {
                    filtered.add(b);
                }
            }
            bookings = filtered;
        }
        Log.d(TAG, "listBookings role=" + role + " completedOnly=" + completedOnly);
        emit(callback, bookings);
    }

    /** Consecutive ISO dates starting at {@code startMs}, for mock bookings. */
    private static java.util.List<String> isoRange(long startMs, int count) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(JsonParsers.formatDate(startMs + i * 24 * 3_600_000L));
        }
        return out;
    }

    @Override
    public void listTreeSlots(String treeId, Callback<List<Slot>> callback) {
        emit(callback, new ArrayList<>(slotsForTree(treeId)));
    }

    /** Lazy-seeded pick-your-own slots for a tree. */
    private List<Slot> slotsForTree(String treeId) {
        List<Slot> slots = slotsByTree.get(treeId);
        if (slots == null) {
            slots = new ArrayList<>();
            long now = System.currentTimeMillis();
            long day = 24 * 3_600_000L;
            for (int i = 0; i < 3; i++) {
                long start = now + (2 + i) * day + 9 * 3_600_000L;
                slots.add(new Slot("slot-" + treeId + "-" + i, treeId, now + (2 + i) * day,
                        start, start + 3 * 3_600_000L, 6, i == 0 ? 2 : 0, 1, null));
            }
            slotsByTree.put(treeId, slots);
        }
        return slots;
    }

    @Override
    public void claimTreeSlot(String treeId, String slotId, Callback<Slot> callback) {
        for (Slot slot : slotsForTree(treeId)) {
            if (slot.getId().equals(slotId)) {
                if (slot.getRemainingCount() <= 0) {
                    emitError(callback, new ApiException("slot_full",
                            "This slot is full"));
                    return;
                }
                Slot claimed = slot.withClaimedCount(slot.getClaimedCount() + 1);
                List<Slot> slots = slotsByTree.get(treeId);
                slots.set(slots.indexOf(slot), claimed);
                emit(callback, claimed);
                return;
            }
        }
        emitError(callback, new ApiException("slot_not_found", "No such slot"));
    }

    @Override
    public void uploadFileKey(byte[] bytes, String contentType, Callback<String> callback) {
        // Mock: pretend the upload succeeded and return a fake storage key.
        emit(callback, "u/mock-user/mock-key.jpg");
    }

    @Override
    public void sendAttachment(String threadId, String uploadKey,
            Callback<ChatMessage> callback) {
        String trimmed = uploadKey == null ? "" : uploadKey.trim();
        if (trimmed.isEmpty()) {
            emitError(callback, new ApiException("empty_attachment", "Pick a photo first."));
            return;
        }
        emit(callback, new ChatMessage("m" + System.currentTimeMillis(), threadId,
                "You", true, ChatMessage.Kind.PHOTO, trimmed,
                System.currentTimeMillis()));
    }

    @Override
    public void getCreditExpiry(Callback<CreditExpiry> callback) {
        // Balance 5 with one chunk expiring in 6 days: demos the 7-day
        // warning; season ends ~60 days out.
        long now = System.currentTimeMillis();
        long day = 24 * 3_600_000L;
        List<CreditExpiry.ExpiringChunk> chunks = new ArrayList<>();
        chunks.add(new CreditExpiry.ExpiringChunk(2, now + 6 * day));
        emit(callback, new CreditExpiry(5, chunks, now + 60 * day));
    }

    @Override
    public void getNotificationPrefs(Callback<NotificationPrefs> callback) {
        emit(callback, notificationPrefs);
    }

    @Override
    public void updateNotificationPrefs(NotificationPrefs prefs,
            Callback<NotificationPrefs> callback) {
        notificationPrefs = prefs == null
                ? NotificationPrefs.defaultAllOn() : prefs;
        Log.d(TAG, "updateNotificationPrefs harvest=" + notificationPrefs.isHarvestAlerts()
                + " quiet=" + notificationPrefs.getQuietHoursStart());
        emit(callback, notificationPrefs);
    }

    @Override
    public void createSittingPaymentIntent(String bookingId,
            Callback<PaymentIntent> callback) {
        Log.d(TAG, "createSittingPaymentIntent booking=" + bookingId);
        emit(callback, new PaymentIntent("stub_secret_for_test"));
    }

    private <T> void emit(Callback<T> callback, T value) {
        main.postDelayed(() -> callback.onSuccess(value), LATENCY_MS);
    }

    private <T> void emitError(Callback<T> callback, ApiException error) {
        main.postDelayed(() -> callback.onError(error), LATENCY_MS);
    }
}
