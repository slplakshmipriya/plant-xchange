package com.gardenswap.test.api;

import java.util.List;

/**
 * Client contract for the GardenSwap backend (API-004).
 *
 * <p><b>Wave 1 methods are frozen</b> (getMe, upsertProfile, createIdvSession,
 * getIdvStatus, registerFcmToken) with real HTTP implementations.
 * <b>Wave 2 methods</b> (listings, want-list, harvest — API-020/030/040) and
 * <b>Wave 3 methods</b> (trees, wallet, sitters, chat) are client-proposed
 * against the planned contract; {@link MockGardenSwapApi} implements them
 * with canned responses. At each integration checkpoint the real client must
 * reconcile drift against {@code api/openapi.yaml}; drift is a P0 bug
 * against the drifting side (see DEPENDENCY_TREE.md).
 *
 * <p>All calls are asynchronous and deliver results on the main thread.
 */
public interface GardenSwapApi {

    /** Single-shot async result. Exactly one method fires per call. */
    interface Callback<T> {
        void onSuccess(T result);

        void onError(ApiException error);
    }

    // ------------------------------------------------------------ Wave 1 (frozen)

    /** Fetch the signed-in user's profile. */
    void getMe(Callback<UserProfile> callback);

    /** Create or update the signed-in user's profile (API-010). */
    void upsertProfile(ProfileUpdate update, Callback<UserProfile> callback);

    /**
     * Upload an avatar image and return the absolute public URL.
     * Runs sign → PUT raw bytes → finalize (API-022).
     */
    void uploadAvatar(byte[] imageBytes, String contentType, Callback<String> callback);

    /** List the signed-in user's swap history (API-070). */
    void getSwaps(Callback<List<Swap>> callback);

    /** Open an ID-verification session with the provider (API-012). */
    void createIdvSession(Callback<IdvSession> callback);

    /** Current ID-verification status (API-012). */
    void getIdvStatus(Callback<IdvStatus> callback);

    /**
     * Test-harness seam: an IDV provider flow just reported {@code status}.
     * The production client ignores this — the SERVER's status (from the
     * provider webhook) is the only truth, re-read via {@link #getIdvStatus}.
     * The mock backend overrides it to mirror the stub outcome so debug
     * builds can exercise the verified UI states.
     */
    default void noteProviderOutcome(IdvStatus status) {
        // no-op in production
    }

    /**
     * Register this device's FCM token for push (AND-002): the token is
     * POSTed to the backend, which targets pushes per device. (Was: per-user
     * FCM topic subscription — guessable from any listing's owner uid.)
     */
    void registerFcmToken(String userId, String fcmToken, Callback<Void> callback);

    /**
     * Deregister this device's FCM token (logout). Best-effort: callers sign
     * out regardless of the result.
     */
    void unregisterFcmToken(String fcmToken, Callback<Void> callback);

    // ------------------------------------------------------------ Wave 3 (proposed)

    /** Tree listing detail (proposed: API-050, GET /v1/trees/{id}). */
    void getTreeDetail(String treeId, Callback<TreeListing> callback);

    /**
     * Pick-your-own tree directory (planned: API-126, {@code GET /v1/trees}).
     */
    void listTrees(Callback<List<TreeListing>> callback);

    /**
     * Ranked nearby-listings feed for Explore (planned: API-123,
     * {@code GET /v1/feed?way=&limit=}). The backend owns freshness-first
     * ranking; the client renders in wire order.
     */
    void getFeed(FeedRequest request, Callback<List<Listing>> callback);

    /**
     * Subscribe/unsubscribe to ripe-window alerts for a tree
     * (proposed: API-050, POST /v1/trees/{id}/alerts).
     */
    void setRipeAlert(String treeId, boolean subscribe, Callback<Boolean> callback);

    /** Credit balance + ledger history (proposed: API-060/061, GET /v1/ledger). */
    void getWallet(Callback<Wallet> callback);

    /**
     * Confirm one side of a credit exchange (proposed: API-060,
     * POST /v1/exchanges/{id}/confirm). Credits move only when both sides
     * confirm — enforced server-side.
     */
    void confirmExchange(String exchangeId, Callback<ExchangeConfirmation> callback);

    /** Sitter search by zip (proposed: API-070, GET /v1/sitters?zip=). */
    void getSitters(String zip, Callback<List<SitterProfile>> callback);

    /** Sitter detail (proposed: API-070, GET /v1/sitters/{id}). */
    void getSitter(String sitterId, Callback<SitterProfile> callback);

    /** Register/update the signed-in user's sitter profile (PUT /v1/sitters/me). */
    void upsertSitterProfile(SitterProfileIn profile, Callback<Void> callback);

    /** Replace the signed-in sitter's available dates (PUT /v1/sitters/me/availability). */
    void setSitterAvailability(java.util.List<String> availableIsoDates,
                               Callback<Void> callback);

    /**
     * Replace the signed-in sitter's advertised services (PUT /v1/sitters/me
     * with a services-only body). Keys are the fixed service taxonomy
     * (see {@code util/SitterServices}); empty = "on request".
     */
    void updateSitterServices(String[] services, Callback<Void> callback);

    /**
     * Public reviews written about a sitter (GET /v1/sitters/{id}/reviews).
     * Server returns newest first.
     */
    void getSitterReviews(String sitterId, Callback<List<Review>> callback);

    /**
     * Request a booking (API-070, POST /v1/sitting-requests).
     *
     * <p>Wire body:
     * {@code {"sitter_uid", "plant_count", "dates": ["YYYY-MM-DD", ...],
     * "services": ["watering", ...], "notes"}}. No start/end range: the
     * booker picks individual dates from the sitter's available days.
     */
    void requestBooking(BookingRequest request, Callback<Booking> callback);

    /**
     * Submit a review for a booking (proposed: API-072,
     * POST /v1/bookings/{id}/reviews). Server accepts only post-completion,
     * once per side.
     */
    void submitReview(String bookingId, Review review, Callback<Void> callback);

    /** Chat thread list (proposed: API-080, GET /v1/threads). */
    void getThreads(Callback<List<ChatThread>> callback);

    /**
     * Open (or fetch) the chat thread about a listing (API-080,
     * POST /v1/threads). Idempotent per listing; the callback receives the
     * full thread (other party's display name/avatar included).
     */
    void openThread(String listingId, Callback<ChatThread> callback);

    /** Messages in a thread (proposed: API-080, GET /v1/threads/{id}/messages). */
    void getMessages(String threadId, Callback<List<ChatMessage>> callback);

    /** Send a message (proposed: API-080, POST /v1/threads/{id}/messages). */
    void sendMessage(String threadId, String text, Callback<ChatMessage> callback);
    // ------------------------------------------------------------ Wave 2 (proposed)

    /** Create a listing (API-020). Photo + spray disclosure are required. */
    void createListing(ListingInput input, Callback<Listing> callback);

    /** Fetch one listing by id (API-020). */
    void getListing(String listingId, Callback<Listing> callback);

    /** Partial update incl. status transitions (API-020). Illegal moves are a backend 422. */
    void patchListing(String listingId, ListingPatch patch, Callback<Listing> callback);

    /**
     * Claim a live listing (API-060). The backend checks the claimer's
     * balance and assigns claimer atomically — this is NOT a status PATCH.
     *
     * <p>Whole-listing claim; delegates to
     * {@link #claimListing(String, ClaimRequest, Callback)} with
     * {@link ClaimRequest#single()}.
     */
    void claimListing(String listingId, Callback<Listing> callback);

    /**
     * Claim a live listing with quantity, pickup window, and notes
     * (planned: API-135, {@code POST /v1/listings/{id}/claims}). Partial
     * claims decrement {@code remaining_qty} on the wire; whole claims behave
     * like {@link #claimListing(String, Callback)}.
     */
    void claimListing(String listingId, ClaimRequest request, Callback<Listing> callback);

    /**
     * Claimer-side claim cancellation (planned: API-135,
     * {@code POST /v1/listings/{id}/claims/cancel}). Restores the listing to
     * LIVE and releases the held quantity/credits.
     */
    void cancelClaim(String listingId, Callback<Listing> callback);

    /**
     * Report a listing, user, or booking for abuse/safety/spam
     * (planned: API-143, {@code POST /v1/reports}).
     */
    void reportContent(ReportRequest request, Callback<Void> callback);

    /** Cancel an owned listing (API-020). Owner-only; illegal moves are a backend 422. */
    void cancelListing(String listingId, Callback<Listing> callback);

    /** Listings owned by the signed-in user (API-020). */
    void listMyListings(Callback<List<Listing>> callback);

    /** The signed-in user's want-list (API-030). */
    void getWantList(Callback<List<WantItem>> callback);

    /** Add a variety to the want-list (API-030). */
    void addWant(String variety, Callback<WantItem> callback);

    /** Remove a want-list entry (API-030). */
    void removeWant(String wantId, Callback<Void> callback);

    /**
     * Live listings matching the signed-in user's want-list.
     * Client-proposed; the backend match engine (API-030) is authoritative.
     */
    void getMatches(Callback<List<Listing>> callback);

    /** Harvest-log entries for a listing, oldest behavior undefined (AND-040). */
    void getHarvestEvents(String listingId, Callback<List<HarvestEvent>> callback);

    /**
     * Log a harvest event: positive {@code delta} = produce added, negative =
     * taken. Client-proposed (AND-040); the backend harvest rules (API-040)
     * define the wire form. The {@code note} parameter is accepted for API
     * compatibility but never sent — the backend stores no harvest notes.
     */
    void logHarvestEvent(String listingId, double delta, String note,
            Callback<HarvestEvent> callback);

    // ------------------------------------------------------------ PRD parity (r2)

    /**
     * Owner accepts a pending claim (planned:
     * {@code POST /v1/listings/{id}/claims/accept} with {@code {claim_id}}).
     * Returns the updated listing.
     */
    void acceptClaim(String listingId, String claimId, Callback<Listing> callback);

    /**
     * Owner declines a pending claim (planned:
     * {@code POST /v1/listings/{id}/claims/decline} with {@code {claim_id}}).
     * Returns the updated listing (back to LIVE in the mock).
     */
    void declineClaim(String listingId, String claimId, Callback<Listing> callback);

    /**
     * Sitting bookings for the signed-in user (planned:
     * {@code GET /v1/bookings?role=&completed=}). {@code role} is
     * {@code "sitter"} or {@code "owner"}; {@code completedOnly} filters to
     * completed bookings (the review flow's input).
     */
    void listBookings(String role, boolean completedOnly, Callback<List<Booking>> callback);

    /**
     * Pick-your-own slots for a tree (planned:
     * {@code GET /v1/trees/{id}/slots} → {@code {"slots": [...]}}).
     */
    void listTreeSlots(String treeId, Callback<List<Slot>> callback);

    /**
     * Claim one pick-your-own slot (planned:
     * {@code POST /v1/trees/{treeId}/slots/{slotId}/claim}).
     */
    void claimTreeSlot(String treeId, String slotId, Callback<Slot> callback);

    /**
     * Upload raw file bytes via the /v1/uploads flow (sign → PUT → finalize).
     * Returns the storage <b>key</b> (e.g. {@code u/&lt;uid&gt;/&lt;id&gt;.jpg}),
     * not the public URL. Use the key with {@link #sendAttachment}.
     */
    void uploadFileKey(byte[] bytes, String contentType, Callback<String> callback);

    /**
     * Send a photo attachment to a thread:
     * {@code POST /v1/threads/{id}/attachments} with {@code {uploadKey}}.
     * The key must come from {@link #uploadFileKey} (a /v1/uploads storage key,
     * not a URL). Returns the created {@link ChatMessage}
     * ({@link ChatMessage.Kind#PHOTO}).
     */
    void sendAttachment(String threadId, String uploadKey, Callback<ChatMessage> callback);

    /**
     * Delete your own message: {@code DELETE /v1/threads/{id}/messages/{mid}}.
     * Sender-only (403 otherwise). Returns the tombstone {@link ChatMessage}
     * ({@link ChatMessage#isDeleted()} true, content cleared).
     */
    void deleteMessage(String threadId, String messageId, Callback<ChatMessage> callback);

    /**
     * Seasonal credit expiry state (planned:
     * {@code GET /v1/users/me/credit-expiry} → {@code {balance, expiring:
     * [{credits, expires_at_ms}], season_end_ms}}).
     */
    void getCreditExpiry(Callback<CreditExpiry> callback);

    /**
     * Notification preferences (planned:
     * {@code GET /v1/users/me/notification-prefs}).
     */
    void getNotificationPrefs(Callback<NotificationPrefs> callback);

    /**
     * Update notification preferences (planned:
     * {@code PUT /v1/users/me/notification-prefs} with
     * {@code {categories: {...}, quiet_hours: {start, end}}}).
     */
    void updateNotificationPrefs(NotificationPrefs prefs,
            Callback<NotificationPrefs> callback);

    /**
     * Create a Stripe payment intent for a sitting booking (planned:
     * {@code POST /v1/payments/sitting-intent} with {@code {booking_id}} →
     * {@code {client_secret}}).
     */
    void createSittingPaymentIntent(String bookingId, Callback<PaymentIntent> callback);
}
