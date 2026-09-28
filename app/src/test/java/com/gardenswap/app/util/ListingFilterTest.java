package com.gardenswap.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gardenswap.app.api.Listing;
import com.gardenswap.app.api.ListingType;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** JVM tests for {@link ListingFilter} (UID-011). */
public class ListingFilterTest {

    private List<Listing> mixed() {
        return Arrays.asList(
                Listing.builder("s1").type(ListingType.SEEDLING).variety("Basil").build(),
                Listing.builder("s2").type(ListingType.SEEDLING).variety("Mint").build(),
                Listing.builder("h1").type(ListingType.HARVEST).variety("Tomatoes").build(),
                Listing.builder("t1").type(ListingType.TREE).variety("Apple tree").build());
    }

    @Test
    public void nullListReturnsEmpty() {
        assertTrue(ListingFilter.filter(null, ListingType.SEEDLING).isEmpty());
    }

    @Test
    public void nullFilterReturnsAll() {
        List<Listing> result = ListingFilter.filter(mixed(), null);
        assertEquals(4, result.size());
    }

    @Test
    public void seedlingFilterKeepsOnlySeedlings() {
        List<Listing> result = ListingFilter.filter(mixed(), ListingType.SEEDLING);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(l -> l.getType() == ListingType.SEEDLING));
    }

    @Test
    public void harvestFilterKeepsOnlyHarvest() {
        List<Listing> result = ListingFilter.filter(mixed(), ListingType.HARVEST);
        assertEquals(1, result.size());
        assertEquals("h1", result.get(0).getId());
    }

    @Test
    public void treeFilterKeepsOnlyTrees() {
        List<Listing> result = ListingFilter.filter(mixed(), ListingType.TREE);
        assertEquals(1, result.size());
        assertEquals("t1", result.get(0).getId());
    }

    @Test
    public void emptyResultWhenNothingMatches() {
        List<Listing> seedlingsOnly = Arrays.asList(
                Listing.builder("s1").type(ListingType.SEEDLING).build());
        assertTrue(ListingFilter.filter(seedlingsOnly, ListingType.HARVEST).isEmpty());
    }

    @Test
    public void listingsWithNullTypeExcludedWhenFiltering() {
        List<Listing> withNullType = Arrays.asList(
                Listing.builder("x1").type(null).build(),
                Listing.builder("s1").type(ListingType.SEEDLING).build());
        List<Listing> result = ListingFilter.filter(withNullType, ListingType.SEEDLING);
        assertEquals(1, result.size());
        assertEquals("s1", result.get(0).getId());
    }
}
