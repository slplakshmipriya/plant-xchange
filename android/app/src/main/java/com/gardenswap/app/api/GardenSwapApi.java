package com.gardenswap.app.api;

/**
 * Client contract for the GardenSwap backend (API-004).
 *
 * <p><b>Frozen for Wave 1.</b> The backend Wave 1 endpoints (API-010/011/012)
 * do not exist yet, so {@link MockGardenSwapApi} implements this interface
 * with canned responses. The real HTTP client arrives at the Wave 1
 * integration checkpoint and must implement this exact interface — callers
 * (e.g. onboarding) must not change.
 *
 * <p>All calls are asynchronous and deliver results on the main thread.
 */
public interface GardenSwapApi {

    /** Single-shot async result. Exactly one method fires per call. */
    interface Callback<T> {
        void onSuccess(T result);

        void onError(ApiException error);
    }

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
}
