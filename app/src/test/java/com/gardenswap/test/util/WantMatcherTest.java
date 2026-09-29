package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.ListingStatus;
import com.gardenswap.test.api.WantItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** JVM tests for {@link WantMatcher} (AND-030). */
public class WantMatcherTest {

    private static Listing listing(String id, String variety, ListingStatus status) {
        return Listing.builder(id)
                .ownerUid("someone-else")
                .variety(variety)
                .status(status)
                .creditCost(1)
                .sprayDisclosure("none")
                .build();
    }

    private static WantItem want(String variety) {
        return new WantItem("w-" + variety, variety, 0L);
    }

    @Test
    public void matchesLiveListingsByVarietySubstring() {
        List<WantItem> wants = Collections.singletonList(want("Cherokee Purple"));
        List<Listing> listings = Arrays.asList(
                listing("a", "Cherokee Purple tomato", ListingStatus.LIVE),
                listing("b", "Roma tomato", ListingStatus.LIVE));
        List<Listing> matches = WantMatcher.matches(wants, listings);
        assertEquals(1, matches.size());
        assertEquals("a", matches.get(0).getId());
    }

    @Test
    public void matchingIsCaseInsensitiveAndBidirectional() {
        List<WantItem> wants = Collections.singletonList(want("basil"));
        assertTrue(WantMatcher.matchesAny(wants, "Genovese BASIL starts"));
        assertTrue(WantMatcher.matchesAny(
                Collections.singletonList(want("Genovese basil plant")), "basil"));
    }

    @Test
    public void terminalListingsNeverMatch() {
        List<WantItem> wants = Collections.singletonList(want("tomato"));
        for (ListingStatus status : new ListingStatus[]{
                ListingStatus.CLAIMED, ListingStatus.COMPLETED,
                ListingStatus.EXPIRED, ListingStatus.CANCELLED, ListingStatus.DRAFT}) {
            List<Listing> matches = WantMatcher.matches(wants,
                    Collections.singletonList(listing("x", "tomato", status)));
            assertTrue("status " + status + " must not match", matches.isEmpty());
        }
    }

    @Test
    public void nullsAreSafe() {
        assertTrue(WantMatcher.matches(null, null).isEmpty());
        assertFalse(WantMatcher.matchesAny(null, "tomato"));
        assertFalse(WantMatcher.matchesAny(Collections.singletonList(want("tomato")), null));
    }

    @Test
    public void blankWantNeverMatches() {
        assertFalse(WantMatcher.matchesAny(
                Collections.singletonList(want("   ")), "tomato"));
    }
}
