package com.gardenswap.app;

import android.util.Log;

/**
 * AND-002 stub: logs FCM token registration instead of calling the backend.
 * Replace with a real implementation once the OpenAPI contract (API-004)
 * defines the device-registration endpoint.
 */
public class NoOpBackendRegistrar implements BackendRegistrar {

    private static final String TAG = "BackendRegistrar";

    @Override
    public void registerFcmToken(String userId, String fcmToken) {
        // TODO(API-004): POST /v1/devices {user_id, fcm_token} with the
        // Firebase ID token; then delete this stub.
        Log.i(TAG, "registerFcmToken (no-op stub); userId set=" + (userId != null && !userId.isEmpty())
                + ", token length=" + (fcmToken == null ? 0 : fcmToken.length()));
    }
}
