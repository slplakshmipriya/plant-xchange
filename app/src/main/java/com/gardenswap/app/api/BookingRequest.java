package com.gardenswap.app.api;

/** Booking request payload (proposed: API-070). */
public final class BookingRequest {

    private final String sitterId;
    private final long startMs;
    private final long endMs;
    private final String[] services;
    private final String careInstructions;
    /**
     * Number of plants needing care. The booking UI does not collect this
     * yet; defaults to 1 until it does (the backend requires &gt; 0).
     */
    private final int plantCount;

    public BookingRequest(String sitterId, long startMs, long endMs,
                          String[] services, String careInstructions) {
        this(sitterId, startMs, endMs, services, careInstructions, 1);
    }

    public BookingRequest(String sitterId, long startMs, long endMs,
                          String[] services, String careInstructions, int plantCount) {
        this.sitterId = sitterId;
        this.startMs = startMs;
        this.endMs = endMs;
        this.services = services.clone();
        this.careInstructions = careInstructions;
        this.plantCount = Math.max(1, plantCount);
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

    public int getPlantCount() {
        return plantCount;
    }
}
