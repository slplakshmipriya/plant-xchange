package com.gardenswap.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReviewFormLogicTest {

    @Test
    public void ratingLabels() {
        assertEquals("Excellent", ReviewFormLogic.ratingLabel(5));
        assertEquals("Great", ReviewFormLogic.ratingLabel(4));
        assertEquals("Good", ReviewFormLogic.ratingLabel(3));
        assertEquals("Fair", ReviewFormLogic.ratingLabel(2));
        assertEquals("Poor", ReviewFormLogic.ratingLabel(1));
    }

    @Test
    public void ratingLabelEmptyWhenUnset() {
        assertEquals("", ReviewFormLogic.ratingLabel(0));
        assertEquals("", ReviewFormLogic.ratingLabel(6));
        assertEquals("", ReviewFormLogic.ratingLabel(-1));
    }

    @Test
    public void canSubmitWithRatingOnly() {
        assertTrue(ReviewFormLogic.canSubmit(5, ""));
        assertTrue(ReviewFormLogic.canSubmit(3, "   "));
        assertTrue(ReviewFormLogic.canSubmit(1, null));
    }

    @Test
    public void canSubmitWithText() {
        assertTrue(ReviewFormLogic.canSubmit(4, "Great sitter, happy plants."));
    }

    @Test
    public void cannotSubmitWithoutRating() {
        assertFalse(ReviewFormLogic.canSubmit(0, "Lovely sit."));
        assertFalse(ReviewFormLogic.canSubmit(6, ""));
        assertFalse(ReviewFormLogic.canSubmit(-1, null));
    }

    @Test
    public void cannotSubmitWithOverlongText() {
        StringBuilder long_ = new StringBuilder();
        for (int i = 0; i < 2001; i++) {
            long_.append('x');
        }
        assertFalse(ReviewFormLogic.canSubmit(5, long_.toString()));
    }
}
