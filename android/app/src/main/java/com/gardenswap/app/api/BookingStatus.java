package com.gardenswap.app.api;

/** Booking lifecycle (proposed: API-070). Server-enforced state machine. */
public enum BookingStatus {
    REQUESTED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static BookingStatus fromString(String wire) {
        if (wire == null) {
            return REQUESTED;
        }
        try {
            return valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return REQUESTED; // forward-compatible: unknown states don't crash the UI
        }
    }
}
