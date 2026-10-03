package com.gardenswap.test.api;

/**
 * Release builds: there is no fallback backend. A release APK built
 * without {@code USE_REAL_API}/{@code API_BASE_URL} (git-ignored
 * {@code local.properties}) is a misconfiguration — fail closed with a
 * clear error instead of silently serving mock listings, a mock wallet,
 * and stub payment secrets to real signed-in users.
 */
public final class FallbackApiFactory {

    private FallbackApiFactory() {
    }

    public static GardenSwapApi create() {
        throw new IllegalStateException(
                "Release build has no API configured: set USE_REAL_API=true "
                        + "and API_BASE_URL in local.properties before building.");
    }
}
