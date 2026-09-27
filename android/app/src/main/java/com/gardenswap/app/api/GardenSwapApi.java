package com.gardenswap.app.api;

import java.util.List;

/**
 * Client contract for the GardenSwap backend (API-004).
 *
 * <p><b>Wave 1 methods are frozen</b> (getMe, upsertProfile, createIdvSession,
 * getIdvStatus, registerFcmToken). <b>Wave 3 methods</b> (trees, wallet,
 * sitters, chat) are client-proposed against the planned contract — the
 * backend Wave 3 endpoints do not exist yet, so {@link MockGardenSwapApi}
 * implements them with canned responses. At the Wave 3 integration checkpoint
 * the real client must reconcile any drift against {@code api/openapi.yaml};
 * drift is a P0 bug against the drifting side (see DEPENDENCY_TREE.md).
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
}
