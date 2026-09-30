package com.gardenswap.test.util;

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
     * Formats a sitter's daily rate: "5 credits/day", "$10/day".
     * A null amount or unit means the sitter hasn't set a rate.
     */
    public static String rateLine(Double amount, String unit) {
        if (amount == null || unit == null) {
            return "Rate on request";
        }
        String n = fmtAmount(amount);
        if ("usd".equals(unit)) {
            return "$" + n + "/day";
        }
        return n + (amount == 1.0 ? " credit/day" : " credits/day");
    }

    /**
     * Booking-sheet price preview for explicitly picked dates. The customer
     * pays the subtotal only — no platform-fee line (the sitter covers the
     * 18% fee, taken from their payout). Display only — the server computes
     * the charge.
     */
    public static String bookingPreview(Double amount, String unit, int days) {
        if (amount == null || unit == null) {
            return "This sitter hasn't set a rate — agree on one in chat.";
        }
        if (days <= 0) {
            return "Pick your dates to see the price.";
        }
        String dayWord = days == 1 ? "day" : "days";
        if ("usd".equals(unit)) {
            return "$" + fmtAmount(amount) + "/day × " + days + " " + dayWord
                    + " = $" + fmtAmount(days * amount);
        }
        return fmtAmount(amount) + " credits/day × " + days + " " + dayWord
                + " = " + fmtAmount(days * amount) + " credits";
    }

    /** Whole numbers render without decimals ("5"); otherwise up to 2 ("7.5"). */
    private static String fmtAmount(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.US, "%.2f", v);
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
