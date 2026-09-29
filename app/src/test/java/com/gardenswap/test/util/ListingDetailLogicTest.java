package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gardenswap.test.api.ListingStatus;

import org.junit.Test;

/** JVM tests for {@link ListingDetailLogic} (AND-021). */
public class ListingDetailLogicTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void claimOnlyWhenLiveAndNotOwner() {
        assertTrue(ListingDetailLogic.canClaim(ListingStatus.LIVE, "owner", "viewer"));
        assertFalse(ListingDetailLogic.canClaim(ListingStatus.LIVE, "me", "me"));
        assertFalse(ListingDetailLogic.canClaim(ListingStatus.CLAIMED, "owner", "viewer"));
        assertFalse(ListingDetailLogic.canClaim(ListingStatus.EXPIRED, "owner", "viewer"));
        assertFalse(ListingDetailLogic.canClaim(ListingStatus.DRAFT, "owner", "viewer"));
        assertFalse(ListingDetailLogic.canClaim(null, "owner", "viewer"));
    }

    @Test
    public void cancelOnlyForOwnerInEditableStates() {
        assertTrue(ListingDetailLogic.canCancel(ListingStatus.DRAFT, "me", "me"));
        assertTrue(ListingDetailLogic.canCancel(ListingStatus.LIVE, "me", "me"));
        assertFalse(ListingDetailLogic.canCancel(ListingStatus.LIVE, "owner", "viewer"));
        assertFalse(ListingDetailLogic.canCancel(ListingStatus.CLAIMED, "me", "me"));
        assertFalse(ListingDetailLogic.canCancel(ListingStatus.CANCELLED, "me", "me"));
    }

    @Test
    public void countdownFormatsLargestTwoUnits() {
        assertEquals("No expiry set", ListingDetailLogic.formatCountdown(null, NOW));
        assertEquals("Expired", ListingDetailLogic.formatCountdown(NOW - 1, NOW));
        assertEquals("Expired", ListingDetailLogic.formatCountdown(NOW, NOW));
        assertEquals("3d 4h", ListingDetailLogic.formatCountdown(
                NOW + 3 * 86_400_000L + 4 * 3_600_000L, NOW));
        assertEquals("5h 12m", ListingDetailLogic.formatCountdown(
                NOW + 5 * 3_600_000L + 12 * 60_000L, NOW));
        assertEquals("45m", ListingDetailLogic.formatCountdown(NOW + 45 * 60_000L, NOW));
        assertEquals("30s", ListingDetailLogic.formatCountdown(NOW + 30_000L, NOW));
    }

    @Test
    public void statusLabelsCoverAllStates() {
        for (ListingStatus status : ListingStatus.values()) {
            String label = ListingDetailLogic.statusLabel(status);
            assertFalse("label for " + status, label.equals("Unknown"));
        }
        assertEquals("Unknown", ListingDetailLogic.statusLabel(null));
    }
}
