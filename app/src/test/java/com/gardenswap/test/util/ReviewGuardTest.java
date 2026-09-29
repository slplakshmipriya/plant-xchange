package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gardenswap.test.api.BookingStatus;

import org.junit.Test;

public class ReviewGuardTest {

    @Test
    public void reviewOnlyAfterCompletion() {
        assertTrue(ReviewGuard.canSubmitReview(BookingStatus.COMPLETED));
        assertFalse(ReviewGuard.canSubmitReview(BookingStatus.IN_PROGRESS));
        assertFalse(ReviewGuard.canSubmitReview(BookingStatus.CONFIRMED));
        assertFalse(ReviewGuard.canSubmitReview(BookingStatus.REQUESTED));
        assertFalse(ReviewGuard.canSubmitReview(BookingStatus.CANCELLED));
    }

    @Test
    public void ratingBounds() {
        assertTrue(ReviewGuard.isValidRating(1));
        assertTrue(ReviewGuard.isValidRating(5));
        assertFalse(ReviewGuard.isValidRating(0));
        assertFalse(ReviewGuard.isValidRating(6));
        assertFalse(ReviewGuard.isValidRating(-1));
    }

    @Test
    public void textValidation() {
        assertTrue(ReviewGuard.isValidText("Great sitter!"));
        assertFalse(ReviewGuard.isValidText(""));
        assertFalse(ReviewGuard.isValidText("   "));
        assertFalse(ReviewGuard.isValidText(null));
        StringBuilder long_ = new StringBuilder();
        for (int i = 0; i < 2001; i++) {
            long_.append('x');
        }
        assertFalse(ReviewGuard.isValidText(long_.toString()));
    }

    @Test
    public void formatPrice() {
        assertEquals("$15.00", ReviewGuard.formatPrice(1500));
        assertEquals("$8.10", ReviewGuard.formatPrice(810));
        assertEquals("$0.99", ReviewGuard.formatPrice(99));
    }

    @Test
    public void priceLine() {
        assertEquals("3 visits × $15.00 = $45.00 + $8.10 fee",
                ReviewGuard.priceLine(3, 1500, 810));
        assertEquals("1 visit × $12.00 = $12.00 + $2.16 fee",
                ReviewGuard.priceLine(1, 1200, 216));
    }

    @Test
    public void ratingLine() {
        assertEquals("4.8 ★ (23 reviews)", ReviewGuard.ratingLine(4.8, 23));
        assertEquals("5.0 ★ (1 review)", ReviewGuard.ratingLine(5.0, 1));
    }
}
