package com.gardenswap.app.util;

import com.gardenswap.app.api.BookingStatus;

/**
 * Pure sitter/review guards (AND-070/072). No Android dependencies, so this
 * runs as a plain JVM unit test.
 *
 * <p>Client-side mirrors of server enforcement: reviews are accepted only
 * post-completion, once per side (API-072 enforces; this keeps the UI
 * honest). Rating 1–5.
 */
public final class ReviewGuard {

    private ReviewGuard() {
    }

    /** True only when the booking reached COMPLETED. */
    public static boolean canSubmitReview(BookingStatus status) {
        return status == BookingStatus.COMPLETED;
    }

    /** Rating must be 1–5 inclusive. */
    public static boolean isValidRating(int rating) {
        return rating >= 1 && rating <= 5;
    }

    /** Review text: non-empty, max 2000 chars. */
    public static boolean isValidText(String text) {
        return text != null && !text.trim().isEmpty() && text.length() <= 2000;
    }

    /** "$15.00" from cents. */
    public static String formatPrice(int cents) {
        return "$" + (cents / 100) + "." + String.format("%02d", Math.abs(cents % 100));
    }

    /**
     * Booking sheet price line: "3 visits × $15.00 = $45.00 + $8.10 fee".
     * The fee math is display-only — the server computes the charge.
     */
    public static String priceLine(int visits, int ratePerVisitCents, int feeCents) {
        int subtotal = visits * ratePerVisitCents;
        return visits + " visit" + (visits == 1 ? "" : "s") + " × "
                + formatPrice(ratePerVisitCents) + " = " + formatPrice(subtotal)
                + " + " + formatPrice(feeCents) + " fee";
    }

    /** "4.8 ★ (23 reviews)" — Bayesian-smoothed score, never bare 5.0 from 1 review. */
    public static String ratingLine(double rating, int reviewCount) {
        return String.format("%.1f ★ (%d review%s)", rating, reviewCount,
                reviewCount == 1 ? "" : "s");
    }
}
