package com.gardenswap.test;

/**
 * Registers this device's FCM token with the backend (AND-002).
 *
 * <p>The real implementation arrives with the API contract (API-004); until
 * then {@link NoOpBackendRegistrar} logs the token and drops it.
 */
public interface BackendRegistrar {

    /**
     * Register {@code fcmToken} for {@code userId} (empty userId = not
     * signed in yet; AND-010 wires Firebase Auth sign-in).
     */
    void registerFcmToken(String userId, String fcmToken);
}
