package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** JVM tests for {@link SitterLogic} display formatting (UID-017). */
public class SitterLogicTest {

    @Test
    public void rateLineCreditsSingular() {
        assertEquals("1 credit/day", SitterLogic.rateLine(1.0, "credits"));
    }

    @Test
    public void rateLineCreditsPlural() {
        assertEquals("3 credits/day", SitterLogic.rateLine(3.0, "credits"));
    }

    @Test
    public void rateLineUsd() {
        assertEquals("$10/day", SitterLogic.rateLine(10.0, "usd"));
    }

    @Test
    public void rateLineUsdCents() {
        assertEquals("$12.50/day", SitterLogic.rateLine(12.5, "usd"));
    }

    @Test
    public void rateLineNullIsOnRequest() {
        assertEquals("Rate on request", SitterLogic.rateLine(null, "credits"));
        assertEquals("Rate on request", SitterLogic.rateLine(5.0, null));
    }

    @Test
    public void bookingPreviewUsd() {
        assertEquals("$10/day × 7 days = $70",
                SitterLogic.bookingPreview(10.0, "usd", 7));
    }

    @Test
    public void bookingPreviewUsdSingularDay() {
        assertEquals("$10/day × 1 day = $10",
                SitterLogic.bookingPreview(10.0, "usd", 1));
    }

    @Test
    public void bookingPreviewCredits() {
        assertEquals("5 credits/day × 7 days = 35 credits",
                SitterLogic.bookingPreview(5.0, "credits", 7));
    }

    @Test
    public void bookingPreviewNoRate() {
        assertEquals("This sitter hasn't set a rate — agree on one in chat.",
                SitterLogic.bookingPreview(null, null, 7));
    }

    @Test
    public void bookingPreviewNoDatesPicked() {
        assertEquals("Pick your dates to see the price.",
                SitterLogic.bookingPreview(10.0, "usd", 0));
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
