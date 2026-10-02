package com.gardenswap.test.util;

/**
 * Pure stepper math for the create-listing credit cost (UID-014).
 *
 * <p>No Android dependencies — safe for JVM unit tests. The bounds encode
 * the product contract that free listings sit on a 1-credit floor (never 0)
 * and paid listings cost 1–100 credits.
 */
public final class CreditStepperLogic {

    /** Minimum credit cost: the free-listing floor. */
    public static final int MIN_CREDIT_COST = 1;

    /** Maximum credit cost for a listing. */
    public static final int MAX_CREDIT_COST = 100;

    private CreditStepperLogic() {
    }

    /** Bounds {@code value} to {@code [min, max]}. */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Steps {@code value} up by one, stopping at {@code max}. */
    public static int increment(int value, int min, int max) {
        return clamp(value + 1, min, max);
    }

    /** Steps {@code value} down by one, stopping at {@code min}. */
    public static int decrement(int value, int min, int max) {
        return clamp(value - 1, min, max);
    }
}
