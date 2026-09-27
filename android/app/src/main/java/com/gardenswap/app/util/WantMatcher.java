package com.gardenswap.app.util;

import com.gardenswap.app.api.Listing;
import com.gardenswap.app.api.ListingStatus;
import com.gardenswap.app.api.WantItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-side want-list matching (AND-030).
 *
 * <p>Pure JVM. The backend match engine (API-030) is the authority; this
 * mirrors its rule so the mock-phase UI can badge matches without a server:
 * a live listing matches when its variety contains a wanted variety (or vice
 * versa), case-insensitive. Claimed/terminal listings never match.
 */
public final class WantMatcher {

    private WantMatcher() {
    }

    /** Live listings matching any want, in listing order. */
    public static List<Listing> matches(List<WantItem> wants, List<Listing> listings) {
        List<Listing> result = new ArrayList<>();
        if (wants == null || listings == null) {
            return result;
        }
        for (Listing listing : listings) {
            if (listing.getStatus() != ListingStatus.LIVE) {
                continue;
            }
            if (matchesAny(wants, listing.getVariety())) {
                result.add(listing);
            }
        }
        return result;
    }

    /** True when the variety matches at least one want. */
    public static boolean matchesAny(List<WantItem> wants, String variety) {
        if (wants == null || variety == null) {
            return false;
        }
        String haystack = variety.toLowerCase(Locale.US).trim();
        for (WantItem want : wants) {
            if (want.getVariety() == null) {
                continue;
            }
            String needle = want.getVariety().toLowerCase(Locale.US).trim();
            if (!needle.isEmpty() && (haystack.contains(needle) || needle.contains(haystack))) {
                return true;
            }
        }
        return false;
    }
}
