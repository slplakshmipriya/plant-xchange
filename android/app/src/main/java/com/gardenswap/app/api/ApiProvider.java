package com.gardenswap.app.api;

import com.gardenswap.app.BuildConfig;

/**
 * Provides the {@link GardenSwapApi} implementation in use.
 *
 * <p>Mock by default. Set {@code USE_REAL_API=true} in the git-ignored
 * {@code local.properties} (with {@code API_BASE_URL} pointing at the
 * backend, e.g. the Cloud Run URL) to use {@link HttpGardenSwapApi}.
 * Callers never change; {@link #set(GardenSwapApi)} remains for tests/DI.
 */
public final class ApiProvider {

    private static GardenSwapApi instance;

    private ApiProvider() {
    }

    public static synchronized GardenSwapApi get() {
        if (instance == null) {
            if (BuildConfig.USE_REAL_API && !BuildConfig.API_BASE_URL.isEmpty()) {
                instance = new HttpGardenSwapApi(BuildConfig.API_BASE_URL);
            } else {
                instance = new MockGardenSwapApi();
            }
        }
        return instance;
    }

    /** Visible for tests / DI. */
    public static synchronized void set(GardenSwapApi api) {
        instance = api;
    }
}
