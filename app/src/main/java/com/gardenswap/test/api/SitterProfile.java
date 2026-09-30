package com.gardenswap.test.api;

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

    public SitterProfile(String sitterId, String displayName, String[] services,
                         Double rateAmount, String rateUnit, int radiusMiles,
                         boolean idVerified, double rating, int reviewCount,
                         int completedSits) {
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
}
