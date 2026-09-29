package com.gardenswap.test.util;

/**
 * Pure claim-sheet rules (UID-013). No Android dependencies.
 *
 * <p>The backend claims whole listings (no partial-claim endpoint), so the
 * quantity stepper records how much the claimer wants; it is validated
 * against the listing's available quantity when known. Pickup-window and
 * notes are sheet-only conveniences for the giver until the claim contract
 * grows fields for them.
 */
public final class ClaimSheetLogic {

    /** Minimum claim quantity — always at least one unit. */
    public static final int MIN_QUANTITY = 1;

    /** Maximum length of the optional note to the giver. */
    public static final int MAX_NOTES_LENGTH = 500;

    /** Preset pickup-window options offered as chips in the sheet. */
    public static final String[] PICKUP_OPTIONS = {"Today", "Tomorrow", "This weekend"};

    private ClaimSheetLogic() {
    }

    /**
     * Clamp a quantity into [MIN_QUANTITY, availableQty]. A null or
     * non-positive available quantity means "unknown" and imposes no cap.
     */
    public static double clampQuantity(double qty, Double availableQty) {
        double max = (availableQty == null || availableQty <= 0)
                ? Double.MAX_VALUE : availableQty;
        if (qty < MIN_QUANTITY) {
            return MIN_QUANTITY;
        }
        if (qty > max) {
            return max;
        }
        return qty;
    }

    /** True when qty is a valid claim amount against the available quantity. */
    public static boolean validQuantity(double qty, Double availableQty) {
        if (qty < MIN_QUANTITY) {
            return false;
        }
        return availableQty == null || availableQty <= 0 || qty <= availableQty;
    }

    /** Label for a pickup-window chip index, or null when out of range. */
    public static String pickupLabel(int index) {
        if (index < 0 || index >= PICKUP_OPTIONS.length) {
            return null;
        }
        return PICKUP_OPTIONS[index];
    }

    /** True when the optional giver note fits the length budget. */
    public static boolean validNotes(String notes) {
        return notes == null || notes.length() <= MAX_NOTES_LENGTH;
    }
}
