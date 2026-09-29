package com.gardenswap.test.util;

import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.ListingType;

/**
 * Pure presentation logic for {@code ListingCardView} (UID-011).
 *
 * <p>Rules (kept deliberately simple; extend here, not in the view):
 * <ul>
 *   <li>Low stock: remaining quantity (falling back to listed quantity)
 *       is in (0, 2] &rarr; "Only X left".</li>
 *   <li>Ending soon: {@link ListingType#HARVEST} listings expiring within
 *       24h &rarr; "Ends today". Already-expired listings get no cue.</li>
 *   <li>Low stock wins when both apply.</li>
 * </ul>
 */
public final class ListingCardLogic {

    /** Max remaining quantity that still triggers the low-stock cue. */
    public static final double LOW_STOCK_THRESHOLD = 2.0;
    /** Harvest listings expiring within this window show "Ends today". */
    public static final long ENDING_SOON_WINDOW_MS = 24L * 60 * 60 * 1000;

    private ListingCardLogic() {
    }

    /**
     * Returns the urgency cue for a listing, or null when none applies.
     */
    public static String urgencyCue(Listing listing, long nowMs) {
        if (listing == null) {
            return null;
        }
        Double qty = listing.getRemainingQty() != null
                ? listing.getRemainingQty()
                : listing.getQuantity();
        if (qty != null && qty > 0 && qty <= LOW_STOCK_THRESHOLD) {
            return "Only " + formatQty(qty) + " left";
        }
        if (listing.getType() == ListingType.HARVEST && listing.getExpiresAtMs() != null) {
            long remainingMs = listing.getExpiresAtMs() - nowMs;
            if (remainingMs > 0 && remainingMs <= ENDING_SOON_WINDOW_MS) {
                return "Ends today";
            }
        }
        return null;
    }

    /** Display label for the kind tag on the card. */
    public static String kindLabel(ListingType type) {
        if (type == null) {
            return "Listing";
        }
        switch (type) {
            case SEEDLING:
                return "Seedling";
            case HARVEST:
                return "Harvest";
            case TREE:
                return "Pick-your-own";
            default:
                return "Listing";
        }
    }

    private static String formatQty(double qty) {
        if (qty == Math.floor(qty) && !Double.isInfinite(qty)) {
            return Integer.toString((int) qty);
        }
        return Double.toString(qty);
    }
}
