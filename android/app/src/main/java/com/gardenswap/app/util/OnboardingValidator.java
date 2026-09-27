package com.gardenswap.app.util;

/**
 * Pure input validation for the onboarding flow (AND-010). No Android
 * dependencies, so this runs as a plain JVM unit test.
 *
 * <p>Client-side validation only — the backend (API-010/011) re-validates
 * everything server-side and never trusts these results.
 */
public final class OnboardingValidator {

    private OnboardingValidator() {
    }

    /**
     * Normalize raw phone input to E.164, or {@code null} if invalid.
     *
     * <p>Accepts: full E.164 ("+15550102030", formatting characters ignored),
     * 10-digit US national numbers (assumed +1), and 11-digit numbers with a
     * leading 1. Digit count must be 10–15 per E.164.
     */
    public static String normalizePhoneToE164(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (raw.trim().startsWith("+")) {
            if (digits.length() < 10 || digits.length() > 15) {
                return null;
            }
            return "+" + digits;
        }
        if (digits.length() == 10) {
            return "+1" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("1")) {
            return "+" + digits;
        }
        return null;
    }

    /** Display name must be 2–40 characters after trimming. */
    public static boolean isValidDisplayName(String name) {
        if (name == null) {
            return false;
        }
        int length = name.trim().length();
        return length >= 2 && length <= 40;
    }

    /** Home ZIP must be exactly 5 digits (ZIP+4 not accepted in MVP). */
    public static boolean isValidZip(String zip) {
        if (zip == null) {
            return false;
        }
        return zip.trim().matches("\\d{5}");
    }
}
