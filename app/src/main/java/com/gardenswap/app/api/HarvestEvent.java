package com.gardenswap.app.api;

/**
 * One harvest-log entry on a harvest listing (AND-040).
 *
 * <p>{@code delta} is signed: positive = produce added (new picking),
 * negative = produce taken (claim/pickup). The log sums to the quantity
 * remaining shown on the harvest screen.
 */
public final class HarvestEvent {

    private final String id;
    private final String listingId;
    private final double delta;
    private final String note;
    private final long createdAtMs;

    public HarvestEvent(String id, String listingId, double delta, String note, long createdAtMs) {
        this.id = id;
        this.listingId = listingId;
        this.delta = delta;
        this.note = note;
        this.createdAtMs = createdAtMs;
    }

    public String getId() {
        return id;
    }

    public String getListingId() {
        return listingId;
    }

    public double getDelta() {
        return delta;
    }

    public String getNote() {
        return note;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }
}
