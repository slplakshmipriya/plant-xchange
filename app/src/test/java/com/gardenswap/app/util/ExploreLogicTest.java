package com.gardenswap.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.gardenswap.app.api.ListingType;

import org.junit.Test;

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
}
