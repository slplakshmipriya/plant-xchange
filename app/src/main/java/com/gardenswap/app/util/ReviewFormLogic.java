package com.gardenswap.app.util;

/**
 * Pure review-form presentation rules (UID-018). No Android dependencies,
 * so this runs as a plain JVM unit test.
 *
 * <p>Rating labels shown under the star row, plus the submit-readiness
 * rule mirroring {@link ReviewActivity}'s client-side checks: a 1–5 star
 * rating is required, review text stays optional but must pass
 * {@link ReviewGuard#isValidText} when present.
 */
public final class ReviewFormLogic {

    private ReviewFormLogic() {
    }

    /** "Excellent" for 5 down to "Poor" for 1; empty when no rating yet. */
    public static String ratingLabel(int stars) {
        switch (stars) {
            case 5:
                return "Excellent";
            case 4:
                return "Great";
            case 3:
                return "Good";
            case 2:
                return "Fair";
            case 1:
                return "Poor";
            default:
                return "";
        }
    }

    /**
     * True when the form may be submitted: a 1–5 rating, and text that is
     * blank (optional) or valid per {@link ReviewGuard#isValidText}.
     */
    public static boolean canSubmit(int stars, String text) {
        if (!ReviewGuard.isValidRating(stars)) {
            return false;
        }
        return text == null || text.trim().isEmpty() || ReviewGuard.isValidText(text);
    }
}
