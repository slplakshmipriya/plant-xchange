package com.gardenswap.app.api;

/**
 * Server-side lifecycle state (API-020). The backend enforces transitions;
 * the client renders state-driven UI and never coerces an illegal move.
 */
public enum ListingStatus {
    DRAFT("draft"),
    LIVE("live"),
    CLAIMED("claimed"),
    COMPLETED("completed"),
    EXPIRED("expired"),
    CANCELLED("cancelled");

    private final String wireValue;

    ListingStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    /** Unknown wire values map to null (forward-compatible with new states). */
    public static ListingStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        for (ListingStatus status : values()) {
            if (status.wireValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == EXPIRED || this == CANCELLED;
    }

    public boolean isEditable() {
        return this == DRAFT || this == LIVE;
    }
}
