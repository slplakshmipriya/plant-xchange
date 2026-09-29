package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.ListingType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** JVM tests for {@link ExploreLogic} (UID-010). */
public class ExploreLogicTest {

    @Test
    public void morningHours() {
        assertEquals("Good morning", ExploreLogic.greeting(5));
        assertEquals("Good morning", ExploreLogic.greeting(11));
    }

    @Test
    public void afternoonHours() {
        assertEquals("Good afternoon", ExploreLogic.greeting(12));
        assertEquals("Good afternoon", ExploreLogic.greeting(17));
    }

    @Test
    public void eveningHours() {
        assertEquals("Good evening", ExploreLogic.greeting(0));
        assertEquals("Good evening", ExploreLogic.greeting(4));
        assertEquals("Good evening", ExploreLogic.greeting(18));
        assertEquals("Good evening", ExploreLogic.greeting(23));
    }

    @Test
    public void outOfRangeFallsBackToEvening() {
        assertEquals("Good evening", ExploreLogic.greeting(-1));
        assertEquals("Good evening", ExploreLogic.greeting(24));
    }

    @Test
    public void wayCardsMapToFilters() {
        assertEquals(ListingType.SEEDLING,
                ExploreLogic.filterForWayCard(ExploreLogic.WAY_SEEDLINGS));
        assertEquals(ListingType.TREE,
                ExploreLogic.filterForWayCard(ExploreLogic.WAY_PICK));
        assertEquals(ListingType.HARVEST,
                ExploreLogic.filterForWayCard(ExploreLogic.WAY_HARVEST));
    }

    @Test
    public void plantCareHasNoListingFilter() {
        assertNull(ExploreLogic.filterForWayCard(ExploreLogic.WAY_CARE));
    }

    @Test
    public void unknownLabelHasNoListingFilter() {
        assertNull(ExploreLogic.filterForWayCard("Compost"));
        assertNull(ExploreLogic.filterForWayCard(null));
    }

    @Test
    public void wayCardsMapToChipIndexes() {
        assertEquals(1, ExploreLogic.chipIndexForWayCard(ExploreLogic.WAY_SEEDLINGS));
        assertEquals(2, ExploreLogic.chipIndexForWayCard(ExploreLogic.WAY_PICK));
        assertEquals(3, ExploreLogic.chipIndexForWayCard(ExploreLogic.WAY_HARVEST));
        assertEquals(0, ExploreLogic.chipIndexForWayCard(ExploreLogic.WAY_CARE));
    }

    @Test
    public void wayForFilterMapsChipsToFeedWays() {
        assertEquals("seedling", ExploreLogic.wayForFilter(ListingType.SEEDLING));
        assertEquals("pick", ExploreLogic.wayForFilter(ListingType.TREE));
        assertEquals("harvest", ExploreLogic.wayForFilter(ListingType.HARVEST));
        assertNull(ExploreLogic.wayForFilter(null));
    }

    @Test
    public void sortByFreshnessOrdersByExpiryNullsLast() {
        Listing soon = new Listing.Builder("test-id").expiresAtMs(100L).build();
        Listing later = new Listing.Builder("test-id").expiresAtMs(500L).build();
        Listing noExpiry = new Listing.Builder("test-id").build();
        List<Listing> sorted = ExploreLogic.sortByFreshness(
                Arrays.asList(noExpiry, later, soon));
        assertEquals(soon, sorted.get(0));
        assertEquals(later, sorted.get(1));
        assertEquals(noExpiry, sorted.get(2));
    }

    @Test
    public void sortByFreshnessDoesNotModifyInput() {
        Listing soon = new Listing.Builder("test-id").expiresAtMs(100L).build();
        Listing later = new Listing.Builder("test-id").expiresAtMs(500L).build();
        List<Listing> input = new ArrayList<>(Arrays.asList(later, soon));
        ExploreLogic.sortByFreshness(input);
        assertEquals(later, input.get(0));
        assertEquals(soon, input.get(1));
    }

    @Test
    public void sortByFreshnessHandlesAllNulls() {
        List<Listing> input = Arrays.asList(
                new Listing.Builder("test-id").build(), new Listing.Builder("test-id").build());
        assertEquals(2, ExploreLogic.sortByFreshness(input).size());
    }

    @Test
    public void sortByFreshnessHandlesEmpty() {
        assertTrue(ExploreLogic.sortByFreshness(new ArrayList<>()).isEmpty());
    }
}
