package com.gardenswap.test.api;

import com.gardenswap.test.BuildConfig;

/**
 * Provides the {@link GardenSwapApi} implementation in use.
 *
 * <p>The real HTTP client is used when {@code USE_REAL_API=true} and an
 * {@code API_BASE_URL} are configured (both from git-ignored
 * {@code local.properties}, e.g. the Cloud Run URL). Otherwise the build
 * falls back per build type: debug builds get the mock backend for
 * backend-less development; release builds FAIL CLOSED with an
 * {@link IllegalStateException} instead of silently serving fabricated
 * listings/wallet data to real signed-in users.
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
                instance = FallbackApiFactory.create();
            }
        }
        return instance;
    }
}
