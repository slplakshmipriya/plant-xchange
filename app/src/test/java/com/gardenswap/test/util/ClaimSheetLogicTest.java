package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM unit tests for {@link ClaimSheetLogic} (UID-013). No Android dependencies. */
public class ClaimSheetLogicTest {

    @Test
    public void clampQuantity_floorsAtMin() {
        assertEquals(1.0, ClaimSheetLogic.clampQuantity(0, 5.0), 0.0);
        assertEquals(1.0, ClaimSheetLogic.clampQuantity(-3, 5.0), 0.0);
        assertEquals(1.0, ClaimSheetLogic.clampQuantity(1, 5.0), 0.0);
    }

    @Test
    public void clampQuantity_capsAtAvailable() {
        assertEquals(5.0, ClaimSheetLogic.clampQuantity(99, 5.0), 0.0);
        assertEquals(2.5, ClaimSheetLogic.clampQuantity(2.5, 2.5), 0.0);
        assertEquals(3.0, ClaimSheetLogic.clampQuantity(3, 10.0), 0.0);
    }

    @Test
    public void clampQuantity_unknownAvailableHasNoCap() {
        assertEquals(99.0, ClaimSheetLogic.clampQuantity(99, null), 0.0);
        assertEquals(99.0, ClaimSheetLogic.clampQuantity(99, 0.0), 0.0);
        assertEquals(99.0, ClaimSheetLogic.clampQuantity(99, -2.0), 0.0);
    }

    @Test
    public void validQuantity_rejectsBelowMinAndAboveAvailable() {
        assertFalse(ClaimSheetLogic.validQuantity(0, 5.0));
        assertFalse(ClaimSheetLogic.validQuantity(-1, null));
        assertFalse(ClaimSheetLogic.validQuantity(6, 5.0));
    }

    @Test
    public void validQuantity_acceptsInRange() {
        assertTrue(ClaimSheetLogic.validQuantity(1, 5.0));
        assertTrue(ClaimSheetLogic.validQuantity(5, 5.0));
        assertTrue(ClaimSheetLogic.validQuantity(42, null));
        assertTrue(ClaimSheetLogic.validQuantity(2.5, 2.5));
    }

    @Test
    public void pickupLabel_mapsOptionsAndRejectsOutOfRange() {
        assertEquals("Today", ClaimSheetLogic.pickupLabel(0));
        assertEquals("Tomorrow", ClaimSheetLogic.pickupLabel(1));
        assertEquals("This weekend", ClaimSheetLogic.pickupLabel(2));
        assertNull(ClaimSheetLogic.pickupLabel(-1));
        assertNull(ClaimSheetLogic.pickupLabel(3));
    }

    @Test
    public void validNotes_acceptsNullAndShortNotes() {
        assertTrue(ClaimSheetLogic.validNotes(null));
        assertTrue(ClaimSheetLogic.validNotes(""));
        assertTrue(ClaimSheetLogic.validNotes("I can pick up after 5pm"));
    }

    @Test
    public void validNotes_rejectsOverlongNotes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ClaimSheetLogic.MAX_NOTES_LENGTH + 1; i++) {
            sb.append('x');
        }
        assertFalse(ClaimSheetLogic.validNotes(sb.toString()));
    }
}
