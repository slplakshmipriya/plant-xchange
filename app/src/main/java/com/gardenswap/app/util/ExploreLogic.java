package com.gardenswap.app.util;

import com.gardenswap.app.api.ListingType;

/**
 * Pure Explore-screen logic (UID-010): greeting copy and way-card routing.
 *
 * <p>JVM-testable; {@code ExploreActivity} keeps only view code.
 */
public final class ExploreLogic {

    public static final String WAY_SEEDLINGS = "Seedlings";
    public static final String WAY_PICK = "Pick-your-own";
    public static final String WAY_HARVEST = "Harvest";
    public static final String WAY_CARE = "Plant care";

    private ExploreLogic() {
    }

    /**
     * Time-of-day greeting. Morning 05:00-11:59, afternoon 12:00-17:59,
     * evening otherwise (out-of-range hours fall through to evening).
     */
    public static String greeting(int hourOfDay) {
        if (hourOfDay >= 5 && hourOfDay < 12) {
            return "Good morning";
        }
        if (hourOfDay >= 12 && hourOfDay < 18) {
            return "Good afternoon";
        }
        return "Good evening";
    }

    /**
     * Listing filter a way card selects, or null when the card does not
     * filter the nearby list ("Plant care" opens the sitter flow instead,
     * so the activity navigates rather than filtering).
     */
    public static ListingType filterForWayCard(String wayLabel) {
        if (WAY_SEEDLINGS.equals(wayLabel)) {
            return ListingType.SEEDLING;
        }
        if (WAY_PICK.equals(wayLabel)) {
            return ListingType.TREE;
        }
        if (WAY_HARVEST.equals(wayLabel)) {
            return ListingType.HARVEST;
        }
        return null;
    }

    /**
     * {@link com.gardenswap.app.ui.FilterChipRow} index matching a way
     * card's filter (0 = All).
     */
    public static int chipIndexForWayCard(String wayLabel) {
        ListingType filter = filterForWayCard(wayLabel);
        if (filter == ListingType.SEEDLING) {
            return 1;
        }
        if (filter == ListingType.TREE) {
            return 2;
        }
        if (filter == ListingType.HARVEST) {
            return 3;
        }
        return 0;
    }
}
