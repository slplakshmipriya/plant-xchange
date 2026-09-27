package com.gardenswap.app.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * In-memory mock of {@link GardenSwapApi} (Wave 1).
 *
 * <p>The backend Wave 1 endpoints (API-010/011/012) do not exist yet, so every
 * screen builds against this with canned responses shaped like the planned
 * contract (see {@code docs/api-contract.md}). Results are delivered on the
 * main thread after a short artificial latency.
 *
 * <p>Replaced by the real HTTP client at the Wave 1 integration checkpoint;
 * {@link GardenSwapApi} itself must not change then.
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

    private <T> void emit(Callback<T> callback, T value) {
        main.postDelayed(() -> callback.onSuccess(value), LATENCY_MS);
    }
}
