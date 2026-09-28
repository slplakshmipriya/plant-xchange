package com.gardenswap.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.gardenswap.app.api.LedgerEntry;

import org.junit.Test;

public class LedgerFormatterTest {

    private static final long DAY = 24 * 3_600_000L;

    @Test
    public void formatDeltaSigns() {
        assertEquals("+2", LedgerFormatter.formatDelta(2));
        assertEquals("−3", LedgerFormatter.formatDelta(-3));
        assertEquals("0", LedgerFormatter.formatDelta(0));
    }

    @Test
    public void formatEntryUsesReason() {
        LedgerEntry e = new LedgerEntry("e1", 2, LedgerEntry.Kind.ISSUED,
                "Tomato seedling swap", "x1", 0L, null);
        assertEquals("+2 · Tomato seedling swap", LedgerFormatter.formatEntry(e));
    }

    @Test
    public void formatEntryFallsBackToKindWhenReasonMissing() {
        LedgerEntry e = new LedgerEntry("e2", 3, LedgerEntry.Kind.ISSUED,
                null, null, 0L, null);
        assertEquals("+3 · issued", LedgerFormatter.formatEntry(e));
    }

    @Test
    public void expiryBannerNullWhenNothingExpiring() {
        assertNull(LedgerFormatter.expiryBanner(null, 1_000_000L));
    }

    @Test
    public void expiryBannerCountsDown() {
        long now = 1_000_000_000_000L;
        assertEquals("Credits expire in 12d. Spend them before they're gone!",
                LedgerFormatter.expiryBanner(now + 12 * DAY, now));
    }

    @Test
    public void expiryBannerWhenAlreadyExpired() {
        long now = 1_000_000_000_000L;
        assertEquals("Some credits have expired — check your ledger.",
                LedgerFormatter.expiryBanner(now - DAY, now));
    }

    @Test
    public void weeklyCapLine() {
        assertEquals("2 of 10 weekly credits earned (8 left)",
                LedgerFormatter.weeklyCapLine(2));
        assertEquals("10 of 10 weekly credits earned (0 left)",
                LedgerFormatter.weeklyCapLine(10));
    }

    @Test
    public void starterNoteMentionsThreeCredits() {
        assertEquals("You started with 3 credits — earn more by sharing your harvest.",
                LedgerFormatter.starterNote());
    }
}
