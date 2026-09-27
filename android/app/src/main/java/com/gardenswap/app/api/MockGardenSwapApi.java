package com.gardenswap.app.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
    public void getIdvStatus(Callback<IdvStatus> callback) {
        emit(callback, mockIdvStatus);
    }

    @Override
    public void registerFcmToken(String userId, String fcmToken, Callback<Void> callback) {
        Log.d(TAG, "registerFcmToken user=" + userId + " (mock: dropped)");
        emit(callback, null);
    }

    // ------------------------------------------------------------ Wave 3 (proposed)

    @Override
    public void getTreeDetail(String treeId, Callback<TreeListing> callback) {
        long now = System.currentTimeMillis();
        long day = 24 * 3_600_000L;
        TreeListing tree = TreeListing.builder(treeId)
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
        emit(callback, tree);
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
                1500, 5, true, 4.8, 23, 31));
        sitters.add(new SitterProfile("s2", "Dev", new String[]{"watering"},
                1200, 3, false, 4.5, 6, 8));
        emit(callback, sitters);
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
    public void requestBooking(BookingRequest request, Callback<Booking> callback) {
        int total = 1500; // mock: one visit at the sitter's rate
        int fee = Math.round(total * 0.18f); // 18% platform fee, inside the 15-20% band
        emit(callback, new Booking("b1", request.getSitterId(), BookingStatus.REQUESTED,
                request.getStartMs(), request.getEndMs(), request.getServices(),
                total, fee, request.getCareInstructions()));
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

    private <T> void emit(Callback<T> callback, T value) {
        main.postDelayed(() -> callback.onSuccess(value), LATENCY_MS);
    }
}
