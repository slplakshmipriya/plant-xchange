package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CoordinateGuardTest {

    @Test
    public void detectsCommaSeparatedPair() {
        assertTrue(CoordinateGuard.looksLikeCoordinates(
                "Meet at 33.4152, -111.8315 tomorrow"));
    }

    @Test
    public void detectsParenthesizedPair() {
        assertTrue(CoordinateGuard.looksLikeCoordinates("I'm at (33.4152,-111.8315)"));
    }

    @Test
    public void detectsSpaceSeparatedPair() {
        assertTrue(CoordinateGuard.looksLikeCoordinates("33.4152 -111.8315"));
    }

    @Test
    public void returnsMatchedText() {
        String matched = CoordinateGuard.findCoordinates("go to 33.4152, -111.8315 ok?");
        assertEquals("33.4152, -111.8315", matched);
    }

    @Test
    public void rejectsOutOfRangeLat() {
        // 91.x is not a valid latitude — likely something else.
        assertFalse(CoordinateGuard.looksLikeCoordinates("score 91.5, 12.3"));
    }

    @Test
    public void rejectsIntegersWithoutDecimals() {
        assertFalse(CoordinateGuard.looksLikeCoordinates("I have 33, 12 seedlings"));
    }

    @Test
    public void plainTextPasses() {
        assertFalse(CoordinateGuard.looksLikeCoordinates(
                "Saturday morning works for pickup!"));
        assertNull(CoordinateGuard.findCoordinates(null));
    }

    @Test
    public void priceLikeDecimalsAreNotCoordinates() {
        // "$12.99" has no pair partner with a decimal — must not trip.
        assertFalse(CoordinateGuard.looksLikeCoordinates("It's $12.99 each"));
    }

    @Test
    public void warningTextMentionsCoordinates() {
        String warning = CoordinateGuard.warningText("33.4152, -111.8315");
        assertTrue(warning.contains("33.4152, -111.8315"));
        assertTrue(warning.contains("exact location stays private"));
    }
}
