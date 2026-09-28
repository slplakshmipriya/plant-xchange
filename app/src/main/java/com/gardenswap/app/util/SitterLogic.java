package com.gardenswap.app.util;

import java.util.Locale;

/**
 * Pure display formatting for the sitter screens (UID-017).
 *
 * <p>All methods are null-safe and JVM-testable; see {@link SitterLogicTest}.
 */
public final class SitterLogic {

    private SitterLogic() {
    }

    /**
     * Formats a sitter's daily rate: "1 credit/day", "3 credits/day".
     * Non-positive rates render as "Free".
     */
    public static String rateText(int creditsPerDay) {
        if (creditsPerDay <= 0) {
            return "Free";
        }
        return creditsPerDay + (creditsPerDay == 1 ? " credit/day" : " credits/day");
    }

    /**
     * Formats an aggregate rating: "★ 4.8 · 23 reviews".
     * Sitters with no reviews render as "New sitter".
     */
    public static String starsText(double rating, int reviewCount) {
        if (reviewCount <= 0 || Double.isNaN(rating) || rating < 0) {
            return "New sitter";
        }
        String count = reviewCount == 1 ? "1 review" : reviewCount + " reviews";
        return "★ " + String.format(Locale.US, "%.1f", rating) + " · " + count;
    }

    /**
     * Joins services for display: "watering, repotting".
     * Null/blank input renders as "Services on request".
     */
    public static String servicesLine(String[] services) {
        if (services == null) {
            return "Services on request";
        }
        StringBuilder sb = new StringBuilder();
        for (String service : services) {
            if (service == null || service.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(service.trim());
        }
        return sb.length() == 0 ? "Services on request" : sb.toString();
    }

    /**
     * First initial for the avatar placeholder, upper-cased.
     * Blank names render as "?".
     */
    public static String initial(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "?";
        }
        return displayName.trim().substring(0, 1).toUpperCase(Locale.US);
    }
}
