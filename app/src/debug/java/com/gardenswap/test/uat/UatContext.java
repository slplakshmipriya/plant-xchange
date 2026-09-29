package com.gardenswap.test.uat;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared state for one journey run: the real API client (via
 * {@link ApiProvider}, so journeys exercise the exact code path the UI
 * uses), a scratch map for passing values between steps, and assertion
 * helpers that throw {@link UatFailure} with user-visible wording.
 *
 * <p>The app's API is callback-based; {@link #call} adapts it to blocking
 * calls with a 30s timeout so journey steps read top-to-bottom.
 */
public final class UatContext {

    private final Map<String, Object> scratch = new HashMap<>();
    private final StringBuilder log = new StringBuilder();

    public GardenSwapApi api() {
        return ApiProvider.get();
    }

    /** Scratch value set by an earlier step (e.g. a created listing id). */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) scratch.get(key);
    }

    public void put(String key, Object value) {
        scratch.put(key, value);
    }

    public void note(String message) {
        synchronized (log) {
            log.append(message).append('\n');
        }
    }

    public String log() {
        synchronized (log) {
            return log.toString();
        }
    }

    /** Functional interface over one callback-style API invocation. */
    public interface ApiCall<T> {
        void invoke(GardenSwapApi.Callback<T> callback);
    }

    /**
     * Runs a callback-style API call synchronously. Returns the value on
     * success; throws {@link UatFailure} on API error or timeout.
     */
    public <T> T call(String what, ApiCall<T> apiCall) throws UatFailure {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<T> value = new AtomicReference<>();
        final AtomicReference<ApiException> error = new AtomicReference<>();
        apiCall.invoke(new GardenSwapApi.Callback<T>() {
            @Override
            public void onSuccess(T result) {
                value.set(result);
                latch.countDown();
            }

            @Override
            public void onError(ApiException e) {
                error.set(e);
                latch.countDown();
            }
        });
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new UatFailure(what + ": timed out after 30s (user would see a spinner forever)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UatFailure(what + ": interrupted", e);
        }
        if (error.get() != null) {
            ApiException e = error.get();
            throw new UatFailure(what + ": API error " + e.getCode()
                    + " (user would see \"" + e.getMessage() + "\")", e);
        }
        return value.get();
    }

    /**
     * Runs an API call that is EXPECTED to fail (negative journeys, e.g.
     * claiming your own listing). Throws {@link UatFailure} if it succeeds.
     */
    public <T> void expectError(String what, String expectedCode, ApiCall<T> apiCall) throws UatFailure {
        try {
            Object result = call(what, apiCall);
            throw new UatFailure(what + ": expected error '" + expectedCode
                    + "' but the call succeeded (returned " + result + ")");
        } catch (UatFailure f) {
            if (f.getMessage() != null && f.getMessage().contains(expectedCode)) {
                note(what + ": correctly rejected with '" + expectedCode + "'");
                return;
            }
            throw f;
        }
    }

    // ---- assertions (user-visible wording) ----

    public void assertTrue(String what, boolean condition) throws UatFailure {
        if (!condition) {
            throw new UatFailure("expected " + what + " but it was false");
        }
        note("ok: " + what);
    }

    public void assertEquals(String what, Object expected, Object actual) throws UatFailure {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new UatFailure("expected " + what + " = <" + expected
                    + "> but saw <" + actual + ">");
        }
        note("ok: " + what + " = <" + expected + ">");
    }

    public void assertNotNull(String what, Object value) throws UatFailure {
        if (value == null) {
            throw new UatFailure("expected " + what + " to be present but it was missing");
        }
        note("ok: " + what + " present");
    }

    public void assertNotEmpty(String what, java.util.Collection<?> c) throws UatFailure {
        if (c == null || c.isEmpty()) {
            throw new UatFailure("expected " + what + " to have items but it was empty");
        }
        note("ok: " + what + " has " + c.size() + " item(s)");
    }
}
