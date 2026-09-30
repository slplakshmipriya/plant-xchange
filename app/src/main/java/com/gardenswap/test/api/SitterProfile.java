package com.gardenswap.test.api;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A plant sitter's public profile (API-070). Immutable. */
public final class SitterProfile {

    private final String sitterId;
    private final String displayName;
    private final String[] services;
    /**
     * Daily rate, or null when the sitter hasn't set one ("rate on
     * request"). {@code rateUnit} is "credits" or "usd" when set.
     */
    private final Double rateAmount;
    private final String rateUnit;
    private final int radiusMiles;
    private final boolean idVerified;
    private final double rating;
    private final int reviewCount;
    private final int completedSits;
    /** ISO dates (yyyy-MM-dd) the sitter marked unavailable. Empty = open. */
    private final Set<String> unavailableDates;

    public SitterProfile(String sitterId, String displayName, String[] services,
                         Double rateAmount, String rateUnit, int radiusMiles,
                         boolean idVerified, double rating, int reviewCount,
                         int completedSits, Set<String> unavailableDates) {
        this.sitterId = sitterId;
        this.displayName = displayName;
        this.services = services.clone();
        this.rateAmount = rateAmount;
        this.rateUnit = rateUnit;
        this.radiusMiles = radiusMiles;
        this.idVerified = idVerified;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.completedSits = completedSits;
        this.unavailableDates = unavailableDates == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(unavailableDates));
    }

    public String getSitterId() {
        return sitterId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String[] getServices() {
        return services.clone();
    }

    public Double getRateAmount() {
        return rateAmount;
    }

    /** "credits", "usd", or null when no rate is set. */
    public String getRateUnit() {
        return rateUnit;
    }

    public int getRadiusMiles() {
        return radiusMiles;
    }

    public boolean isIdVerified() {
        return idVerified;
    }

    /** Aggregated score with Bayesian smoothing (API-072) — never 5.0 from 1 review. */
    public double getRating() {
        return rating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public int getCompletedSits() {
        return completedSits;
    }

    /** Unmodifiable set of ISO dates (yyyy-MM-dd) the sitter is unavailable. */
    public Set<String> getUnavailableDates() {
        return unavailableDates;
    }

    /** True when the sitter marked the given ISO date unavailable. */
    public boolean isUnavailableOn(String isoDate) {
        return unavailableDates.contains(isoDate);
    }
}
