package com.gardenswap.test.idv;

/**
 * Debug builds only: hands the screen the test-stub provider. This class
 * (and the stub it returns) exists solely in the debug source set, so the
 * simulated verification flow can never ship in a release APK.
 */
public final class IdvProviderFactory {

    private IdvProviderFactory() {
    }

    public static IdvProvider create() {
        return new StubIdvProvider();
    }
}
