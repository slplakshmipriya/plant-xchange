package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RipeWindowTest {

    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;

    @Test
    public void ripeInsideWindow() {
        long now = 1_000_000_000_000L;
        assertTrue(RipeWindow.isRipe(now - HOUR, now + HOUR, now));
    }

    @Test
    public void notRipeBeforeWindow() {
        long now = 1_000_000_000_000L;
        assertFalse(RipeWindow.isRipe(now + HOUR, now + 2 * HOUR, now));
        assertTrue(RipeWindow.isUpcoming(now + HOUR, now));
    }

    @Test
    public void notRipeAfterWindowEnds() {
        long now = 1_000_000_000_000L;
        assertFalse(RipeWindow.isRipe(now - 2 * HOUR, now - HOUR, now));
        assertTrue(RipeWindow.isPast(now - HOUR, now));
    }

    @Test
    public void boundaryStartIsRipeEndIsNot() {
        long now = 1_000_000_000_000L;
        assertTrue(RipeWindow.isRipe(now, now + HOUR, now)); // start inclusive
        assertFalse(RipeWindow.isRipe(now - HOUR, now, now)); // end exclusive
    }

    @Test
    public void countdownBeforeWindow() {
        long now = 1_000_000_000_000L;
        assertEquals("Ripe in 2d 3h",
                RipeWindow.countdownText(now + 2 * DAY + 3 * HOUR, now + 3 * DAY, now));
    }

    @Test
    public void countdownDuringWindow() {
        long now = 1_000_000_000_000L;
        assertEquals("Ripe now — 1d left",
                RipeWindow.countdownText(now - HOUR, now + DAY, now));
    }

    @Test
    public void countdownAfterWindow() {
        long now = 1_000_000_000_000L;
        assertEquals("Ripe window ended",
                RipeWindow.countdownText(now - 2 * DAY, now - DAY, now));
    }

    @Test
    public void formatDurationUnits() {
        assertEquals("3d 4h", RipeWindow.formatDuration(3 * DAY + 4 * HOUR));
        assertEquals("5h 30m", RipeWindow.formatDuration(5 * HOUR + 30 * 60_000L));
        assertEquals("45m", RipeWindow.formatDuration(45 * 60_000L));
        assertEquals("20s", RipeWindow.formatDuration(20_000L));
        assertEquals("0s", RipeWindow.formatDuration(-1000L));
    }
}
