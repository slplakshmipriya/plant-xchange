package com.gardenswap.app.api;

/**
 * Provides the {@link GardenSwapApi} implementation in use.
 *
 * <p>Defaults to {@link MockGardenSwapApi}. The real HTTP client (API-004
 * contract) replaces it at the Wave 1 integration checkpoint via
 * {@link #set(GardenSwapApi)} — callers never change.
 */
public final class ApiProvider {

    private static GardenSwapApi instance;

    private ApiProvider() {
    }

    public static synchronized GardenSwapApi get() {
        if (instance == null) {
            instance = new MockGardenSwapApi();
        }
        return instance;
    }

    /** Visible for tests / DI. */
    public static synchronized void set(GardenSwapApi api) {
        instance = api;
    }
}
