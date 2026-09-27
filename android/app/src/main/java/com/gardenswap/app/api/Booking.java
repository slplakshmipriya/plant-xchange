package com.gardenswap.app.api;

/** A sitting booking (proposed: API-070). Immutable. */
public final class Booking {

    private final String bookingId;
    private final String sitterId;
    private final BookingStatus status;
    private final long startMs;
    private final long endMs;
    private final String[] services;
    private final int totalCents;
    private final int feeCents;
    private final String careInstructions;

    public Booking(String bookingId, String sitterId, BookingStatus status,
                   long startMs, long endMs, String[] services,
                   int totalCents, int feeCents, String careInstructions) {
        this.bookingId = bookingId;
        this.sitterId = sitterId;
        this.status = status;
        this.startMs = startMs;
        this.endMs = endMs;
        this.services = services.clone();
        this.totalCents = totalCents;
        this.feeCents = feeCents;
        this.careInstructions = careInstructions;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getSitterId() {
        return sitterId;
    }

    public BookingStatus getStatus() {
        return status;
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

    /** Total charged to the owner, in cents. */
    public int getTotalCents() {
        return totalCents;
    }

    /** Platform fee portion, in cents (15–20% per PRD). */
    public int getFeeCents() {
        return feeCents;
    }

    public String getCareInstructions() {
        return careInstructions;
    }
}
