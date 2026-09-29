package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import com.gardenswap.test.api.HarvestEvent;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** JVM tests for {@link HarvestLogLogic} (AND-040). */
public class HarvestLogLogicTest {

    private static HarvestEvent event(double delta, long atMs) {
        return new HarvestEvent("e", "listing-1", delta, "", atMs);
    }

    @Test
    public void remainingSumsDeltasAndFloorsAtZero() {
        List<HarvestEvent> events = Arrays.asList(event(-5, 1), event(3, 2), event(-20, 3));
        assertEquals(0.0, HarvestLogLogic.remaining(20, events), 0.001);
        assertEquals(15.0, HarvestLogLogic.remaining(20,
                Collections.singletonList(event(-5, 1))), 0.001);
    }

    @Test
    public void remainingWithNoEventsIsInitial() {
        assertEquals(20.0, HarvestLogLogic.remaining(20, null), 0.001);
        assertEquals(20.0, HarvestLogLogic.remaining(20, Collections.emptyList()), 0.001);
    }

    @Test
    public void progressTowardZero() {
        assertEquals(0.25, HarvestLogLogic.progressTowardZero(20,
                Collections.singletonList(event(-5, 1))), 0.001);
        assertEquals(1.0, HarvestLogLogic.progressTowardZero(20,
                Collections.singletonList(event(-25, 1))), 0.001);
        // Net additions never report progress.
        assertEquals(0.0, HarvestLogLogic.progressTowardZero(20,
                Collections.singletonList(event(5, 1))), 0.001);
    }

    @Test
    public void newestFirstOrdering() {
        List<HarvestEvent> sorted = HarvestLogLogic.newestFirst(
                Arrays.asList(event(-1, 100), event(-2, 300), event(-3, 200)));
        assertEquals(300, sorted.get(0).getCreatedAtMs());
        assertEquals(200, sorted.get(1).getCreatedAtMs());
        assertEquals(100, sorted.get(2).getCreatedAtMs());
    }

    @Test
    public void validateDelta() {
        assertNull(HarvestLogLogic.validateDelta("5"));
        assertNull(HarvestLogLogic.validateDelta("-2.5"));
        assertNotNull(HarvestLogLogic.validateDelta(""));
        assertNotNull(HarvestLogLogic.validateDelta("0"));
        assertNotNull(HarvestLogLogic.validateDelta("abc"));
    }
}
