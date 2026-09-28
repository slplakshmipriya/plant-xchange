package com.gardenswap.app.ui;

/**
 * Verification badge states, shared by {@link VerifiedBadgeView} and the
 * pure-JVM mapping logic. This enum itself has no Android dependencies so
 * unit tests can exercise the status → badge mapping.
 */
public enum BadgeState {
    ID_VERIFIED,
    PHONE_VERIFIED,
    PENDING,
    UNVERIFIED
}
