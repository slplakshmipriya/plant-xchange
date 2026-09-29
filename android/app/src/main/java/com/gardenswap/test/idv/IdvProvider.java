package com.gardenswap.test.idv;

import android.app.Activity;

import com.gardenswap.test.api.IdvStatus;

/**
 * Identity-verification provider abstraction (AND-011).
 *
 * <p>The real provider SDK (Stripe Identity / Didit, API-012) is not bundled
 * yet. {@link StubIdvProvider} implements this interface for development and
 * the Wave 1 integration checkpoint; swapping in the real SDK means replacing
 * the implementation only — this interface stays unchanged.
 */
public interface IdvProvider {

    /** Exactly one method fires per {@link #start} call. */
    interface Listener {
        void onResult(IdvStatus status);

        void onCanceled();
    }

    /**
     * Launch the provider's verification flow for {@code sessionId}.
     *
     * @param activity  host activity, used to start the provider UI
     * @param sessionId opaque session from {@code GardenSwapApi.createIdvSession}
     * @param listener  receives the terminal verification status
     */
    void start(Activity activity, String sessionId, Listener listener);
}
