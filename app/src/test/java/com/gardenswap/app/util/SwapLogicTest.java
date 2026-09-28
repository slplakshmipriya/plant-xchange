package com.gardenswap.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gardenswap.app.api.BookingStatus;
import com.gardenswap.app.api.Swap;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** JVM unit tests for {@link SwapLogic} (UID-023). No Android dependencies. */
public class SwapLogicTest {

    private static Swap swap(String id, BookingStatus status) {
        return new Swap(id, "counterparty", "title", status);
    }

    @Test
    public void isActive_trueForInFlightStatuses() {
        assertTrue(SwapLogic.isActive(BookingStatus.REQUESTED));
        assertTrue(SwapLogic.isActive(BookingStatus.CONFIRMED));
        assertTrue(SwapLogic.isActive(BookingStatus.IN_PROGRESS));
    }

    @Test
    public void isActive_falseForTerminalStatuses() {
        assertFalse(SwapLogic.isActive(BookingStatus.COMPLETED));
        assertFalse(SwapLogic.isActive(BookingStatus.CANCELLED));
    }

    @Test
    public void isActive_nullStatusTreatedAsActive() {
        assertTrue(SwapLogic.isActive(null));
    }

    @Test
    public void partition_splitsActiveAndCompleted() {
        List<Swap> swaps = Arrays.asList(
                swap("a", BookingStatus.REQUESTED),
                swap("b", BookingStatus.IN_PROGRESS),
                swap("c", BookingStatus.COMPLETED),
                swap("d", BookingStatus.CANCELLED));
        SwapLogic.Partition p = SwapLogic.partition(swaps);
        assertEquals(2, p.active().size());
        assertEquals(2, p.completed().size());
        assertEquals("a", p.active().get(0).getSwapId());
        assertEquals("b", p.active().get(1).getSwapId());
        assertEquals("c", p.completed().get(0).getSwapId());
        assertEquals("d", p.completed().get(1).getSwapId());
    }

    @Test
    public void partition_nullListYieldsEmpty() {
        SwapLogic.Partition p = SwapLogic.partition(null);
        assertTrue(p.active().isEmpty());
        assertTrue(p.completed().isEmpty());
    }

    @Test
    public void partition_skipsNullEntries() {
        List<Swap> swaps = new ArrayList<>();
        swaps.add(null);
        swaps.add(swap("a", BookingStatus.CONFIRMED));
        swaps.add(null);
        SwapLogic.Partition p = SwapLogic.partition(swaps);
        assertEquals(1, p.active().size());
        assertTrue(p.completed().isEmpty());
    }

    @Test
    public void partition_nullStatusGoesActive() {
        SwapLogic.Partition p = SwapLogic.partition(
                Arrays.asList(swap("a", null)));
        assertEquals(1, p.active().size());
        assertTrue(p.completed().isEmpty());
    }

    @Test
    public void partition_emptyListYieldsEmpty() {
        SwapLogic.Partition p =
                SwapLogic.partition(new ArrayList<Swap>());
        assertTrue(p.active().isEmpty());
        assertTrue(p.completed().isEmpty());
    }
}
