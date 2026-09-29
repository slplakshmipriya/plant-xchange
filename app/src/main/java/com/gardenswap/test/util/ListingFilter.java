package com.gardenswap.test.util;

import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.ListingType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure filter logic for the Explore filter chips (UID-011).
 */
public final class ListingFilter {

    private ListingFilter() {
    }

    /**
     * Returns the listings matching {@code filterOrNullAll}.
     * A null filter means "All": every listing is returned (as a copy).
     * A null input list returns an empty list.
     */
    public static List<Listing> filter(List<Listing> listings, ListingType filterOrNullAll) {
        if (listings == null) {
            return Collections.emptyList();
        }
        if (filterOrNullAll == null) {
            return new ArrayList<>(listings);
        }
        List<Listing> out = new ArrayList<>();
        for (Listing listing : listings) {
            if (listing != null && filterOrNullAll == listing.getType()) {
                out.add(listing);
            }
        }
        return out;
    }
}
