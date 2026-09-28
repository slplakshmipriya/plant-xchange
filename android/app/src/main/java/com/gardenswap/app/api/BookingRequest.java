package com.gardenswap.app.api;

/** Booking request payload (proposed: API-070). */
public final class BookingRequest {

    private final String sitterId;
    private final long startMs;
    private final long endMs;
    private final String[] services;
    private final String careInstructions;

    public BookingRequest(String sitterId, long startMs, long endMs,
                          String[] services, String careInstructions) {
        this.sitterId = sitterId;
        this.startMs = startMs;
        this.endMs = endMs;
        this.services = services.clone();
        this.careInstructions = careInstructions;
    }

    public String getSitterId() {
        return sitterId;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public String[] getServices() {
        return services.clone();
    }

    public String getCareInstructions() {
        return careInstructions;
    }
}
