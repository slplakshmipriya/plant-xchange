package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** JVM tests for {@link SitterLogic} display formatting (UID-017). */
public class SitterLogicTest {

    @Test
    public void rateTextSingular() {
        assertEquals("1 credit/day", SitterLogic.rateText(1));
    }

    @Test
    public void rateTextPlural() {
        assertEquals("3 credits/day", SitterLogic.rateText(3));
    }

    @Test
    public void rateTextZeroIsFree() {
        assertEquals("Free", SitterLogic.rateText(0));
    }

    @Test
    public void rateTextNegativeIsFree() {
        assertEquals("Free", SitterLogic.rateText(-2));
    }

    @Test
    public void starsTextFormatsRatingAndCount() {
        assertEquals("★ 4.8 · 23 reviews", SitterLogic.starsText(4.8, 23));
    }

    @Test
    public void starsTextRoundsToOneDecimal() {
        assertEquals("★ 4.7 · 6 reviews", SitterLogic.starsText(4.666, 6));
    }

    @Test
    public void starsTextSingularReview() {
        assertEquals("★ 5.0 · 1 review", SitterLogic.starsText(5.0, 1));
    }

    @Test
    public void starsTextNoReviewsIsNewSitter() {
        assertEquals("New sitter", SitterLogic.starsText(0.0, 0));
    }

    @Test
    public void starsTextNegativeCountIsNewSitter() {
        assertEquals("New sitter", SitterLogic.starsText(4.5, -1));
    }

    @Test
    public void starsTextNaNRatingIsNewSitter() {
        assertEquals("New sitter", SitterLogic.starsText(Double.NaN, 3));
    }

    @Test
    public void servicesLineJoins() {
        assertEquals("watering, repotting",
                SitterLogic.servicesLine(new String[]{"watering", "repotting"}));
    }

    @Test
    public void servicesLineSingle() {
        assertEquals("watering", SitterLogic.servicesLine(new String[]{"watering"}));
    }

    @Test
    public void servicesLineNullIsFallback() {
        assertEquals("Services on request", SitterLogic.servicesLine(null));
    }

    @Test
    public void servicesLineEmptyIsFallback() {
        assertEquals("Services on request", SitterLogic.servicesLine(new String[]{}));
    }

    @Test
    public void servicesLineSkipsBlanks() {
        assertEquals("watering",
                SitterLogic.servicesLine(new String[]{null, "  ", "watering"}));
    }

    @Test
    public void servicesLineAllBlankIsFallback() {
        assertEquals("Services on request",
                SitterLogic.servicesLine(new String[]{null, "  "}));
    }

    @Test
    public void initialFirstLetterUppercased() {
        assertEquals("M", SitterLogic.initial("maya"));
    }

    @Test
    public void initialTrimsWhitespace() {
        assertEquals("D", SitterLogic.initial("  Dev "));
    }

    @Test
    public void initialNullIsQuestionMark() {
        assertEquals("?", SitterLogic.initial(null));
    }

    @Test
    public void initialBlankIsQuestionMark() {
        assertEquals("?", SitterLogic.initial("   "));
    }
}
