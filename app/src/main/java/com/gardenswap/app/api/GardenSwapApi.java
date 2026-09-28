package com.gardenswap.app.api;

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

    /** Open an ID-verification session with the provider (API-012). */
    void createIdvSession(Callback<IdvSession> callback);

    /** Current ID-verification status (API-012). */
    void getIdvStatus(Callback<IdvStatus> callback);

    /** Register this device's FCM token for push (AND-002). */
    void registerFcmToken(String userId, String fcmToken, Callback<Void> callback);

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

    /** Request a booking (proposed: API-070, POST /v1/bookings). */
    void requestBooking(BookingRequest request, Callback<Booking> callback);

    /**
     * Submit a review for a booking (proposed: API-072,
     * POST /v1/bookings/{id}/reviews). Server accepts only post-completion,
     * once per side.
     */
    void submitReview(String bookingId, Review review, Callback<Void> callback);

    /** Chat thread list (proposed: API-080, GET /v1/threads). */
    void getThreads(Callback<List<ChatThread>> callback);

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
     * define the wire form.
     */
    void logHarvestEvent(String listingId, double delta, String note,
            Callback<HarvestEvent> callback);
}
