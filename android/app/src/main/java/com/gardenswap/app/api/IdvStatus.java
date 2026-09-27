package com.gardenswap.app.api;

import java.util.Locale;

/**
 * Backend ID-verification enum (API-012). Pure Java so JVM unit tests can
 * exercise it. Unknown wire values map to {@link #UNVERIFIED} — never crash
 * on a backend enum the client doesn't know yet.
 */
public enum IdvStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    FAILED;

    /** Parse the backend wire value (case-insensitive); unknown → UNVERIFIED. */
    public static IdvStatus fromString(String raw) {
        if (raw == null) {
            return UNVERIFIED;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            return UNVERIFIED;
        }
    }
}
