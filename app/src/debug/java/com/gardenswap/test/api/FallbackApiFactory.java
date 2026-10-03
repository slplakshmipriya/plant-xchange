package com.gardenswap.test.api;

/**
 * Debug builds only: when no real backend is configured, hand out the
 * mock backend so the app can be exercised without a server. This class
 * (and the mock) exists solely in the debug source set and can never
 * ship in a release APK.
 */
public final class FallbackApiFactory {

    private FallbackApiFactory() {
    }

    public static GardenSwapApi create() {
        return new MockGardenSwapApi();
    }
}
