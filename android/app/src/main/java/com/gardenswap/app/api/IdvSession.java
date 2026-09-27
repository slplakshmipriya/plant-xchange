package com.gardenswap.app.api;

/** Opaque ID-verification session handle (API-012). */
public final class IdvSession {

    private final String sessionId;
    private final String providerUrl;

    public IdvSession(String sessionId, String providerUrl) {
        this.sessionId = sessionId;
        this.providerUrl = providerUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getProviderUrl() {
        return providerUrl;
    }
}
