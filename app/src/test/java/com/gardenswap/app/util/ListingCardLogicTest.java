package com.gardenswap.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.gardenswap.app.api.Listing;
import com.gardenswap.app.api.ListingType;

import org.junit.Test;

/** JVM tests for {@link ListingCardLogic} urgency and kind-label rules (UID-011). */
public class ListingCardLogicTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long HOUR_MS = 3_600_000L;

    private Listing.Builder base() {
        return Listing.builder("test-1")
                .type(ListingType.SEEDLING)
                .variety("Cherokee Purple tomato")
                .quantity(6.0)
                .unit("starts")
                .creditCost(2);
    }

    @Test
    public void nullListingHasNoCue() {
        assertNull(ListingCardLogic.urgencyCue(null, NOW));
    }

    @Test
    public void lowRemainingQtyShowsOnlyLeft() {
        Listing listing = base().remainingQty(2.0).build();
        assertEquals("Only 2 left", ListingCardLogic.urgencyCue(listing, NOW));
    }

    @Test
    public void fractionalQtyFormatsWithoutTrailingZero() {
        Listing listing = base().remainingQty(1.5).build();
        assertEquals("Only 1.5 left", ListingCardLogic.urgencyCue(listing, NOW));
    }

    @Test
    public void quantityFallsBackWhenRemainingQtyNull() {
        Listing listing = base().quantity(1.0).remainingQty(null).build();
        assertEquals("Only 1 left", ListingCardLogic.urgencyCue(listing, NOW));
    }

    @Test
    public void plentyOfStockHasNoCue() {
        assertNull(ListingCardLogic.urgencyCue(base().quantity(6.0).build(), NOW));
    }

    @Test
    public void zeroQuantityHasNoCue() {
        assertNull(ListingCardLogic.urgencyCue(base().quantity(0.0).build(), NOW));
    }

    @Test
    public void nullQuantitiesHaveNoCue() {
        assertNull(ListingCardLogic.urgencyCue(base().quantity(null).remainingQty(null).build(), NOW));
    }

    @Test
    public void harvestExpiringWithinADayShowsEndsToday() {
        Listing listing = base()
                .type(ListingType.HARVEST)
                .expiresAtMs(NOW + 12 * HOUR_MS)
                .build();
        assertEquals("Ends today", ListingCardLogic.urgencyCue(listing, NOW));
    }

    @Test
    public void harvestExpiringLaterHasNoCue() {
        Listing listing = base()
                .type(ListingType.HARVEST)
                .expiresAtMs(NOW + 30 * HOUR_MS)
                .build();
        assertNull(ListingCardLogic.urgencyCue(listing, NOW));
    }

    @Test
    public void expiredHarvestHasNoCue() {
        Listing listing = base()
                .type(ListingType.HARVEST)
                .expiresAtMs(NOW - HOUR_MS)
                .build();
        assertNull(ListingCardLogic.urgencyCue(listing, NOW));
    }

    @Test
    public void nonHarvestExpiringSoonHasNoCue() {
        Listing listing = base()
                .type(ListingType.SEEDLING)
                .expiresAtMs(NOW + 12 * HOUR_MS)
                .build();
        assertNull(ListingCardLogic.urgencyCue(listing, NOW));
    }

    @Test
    public void lowStockWinsOverExpiry() {
        Listing listing = base()
                .type(ListingType.HARVEST)
                .remainingQty(1.0)
                .expiresAtMs(NOW + 12 * HOUR_MS)
                .build();
        assertEquals("Only 1 left", ListingCardLogic.urgencyCue(listing, NOW));
    }

    @Test
    public void kindLabelSeedling() {
        assertEquals("Seedling", ListingCardLogic.kindLabel(ListingType.SEEDLING));
    }

    @Test
    public void kindLabelHarvest() {
        assertEquals("Harvest", ListingCardLogic.kindLabel(ListingType.HARVEST));
    }

    @Test
    public void kindLabelTreeIsPickYourOwn() {
        assertEquals("Pick-your-own", ListingCardLogic.kindLabel(ListingType.TREE));
    }

    @Test
    public void kindLabelNullFallsBack() {
        assertEquals("Listing", ListingCardLogic.kindLabel(null));
    }
}
