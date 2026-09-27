package com.gardenswap.app.idv;

import android.app.Activity;
import android.content.Intent;

import com.gardenswap.app.api.IdvStatus;

/**
 * Test-harness {@link IdvProvider} (AND-011). Simulates the provider web flow:
 * {@code pending → verified/failed}.
 *
 * <p>{@link #setDefaultOutcome(IdvStatus)} is the harness toggle controlling
 * which outcome the stub auto-reports after a short "pending" delay; debug
 * builds also get on-screen override buttons in {@link StubIdvActivity}.
 * The real provider SDK replaces this class wholesale.
 */
public class StubIdvProvider implements IdvProvider {

    private static IdvStatus defaultOutcome = IdvStatus.VERIFIED;
    private static Listener pendingListener;

    /** Harness toggle: which outcome the stub auto-reports. */
    public static void setDefaultOutcome(IdvStatus outcome) {
        defaultOutcome = outcome == null ? IdvStatus.VERIFIED : outcome;
    }

    @Override
    public void start(Activity activity, String sessionId, Listener listener) {
        pendingListener = listener;
        Intent intent = new Intent(activity, StubIdvActivity.class);
        intent.putExtra(StubIdvActivity.EXTRA_OUTCOME, defaultOutcome.name());
        intent.putExtra(StubIdvActivity.EXTRA_SESSION_ID, sessionId);
        activity.startActivity(intent);
    }

    /** Called by {@link StubIdvActivity}. Test-harness only. */
    static void deliverResult(IdvStatus status) {
        Listener listener = pendingListener;
        pendingListener = null;
        if (listener != null) {
            listener.onResult(status);
        }
    }

    /** Called by {@link StubIdvActivity}. Test-harness only. */
    static void deliverCancel() {
        Listener listener = pendingListener;
        pendingListener = null;
        if (listener != null) {
            listener.onCanceled();
        }
    }
}
