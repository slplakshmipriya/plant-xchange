package com.gardenswap.test.idv;

/**
 * Release builds: no IDV provider is wired yet (verification is not
 * implemented), so there is nothing to hand the screen. The screen renders
 * the server-reported status and marks verification unavailable rather
 * than simulating success with the test stub.
 */
public final class IdvProviderFactory {

    private IdvProviderFactory() {
    }

    public static IdvProvider create() {
        return null;
    }
}
