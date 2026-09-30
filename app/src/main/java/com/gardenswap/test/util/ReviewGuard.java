package com.gardenswap.test.util;

import com.gardenswap.test.api.BookingStatus;

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

    /**
     * "4.8 ★ (23 reviews)" — Bayesian-smoothed score, never bare 5.0 from 1 review. */
    public static String ratingLine(double rating, int reviewCount) {
        return String.format("%.1f ★ (%d review%s)", rating, reviewCount,
                reviewCount == 1 ? "" : "s");
    }
}
