package com.gardenswap.app.api;

/** A plant sitter's public profile (proposed: API-070). Immutable. */
public final class SitterProfile {

    private final String sitterId;
    private final String displayName;
    private final String[] services;
    private final int ratePerVisitCents;
    private final int radiusMiles;
    private final boolean idVerified;
    private final double rating;
    private final int reviewCount;
    private final int completedSits;

    public SitterProfile(String sitterId, String displayName, String[] services,
                         int ratePerVisitCents, int radiusMiles, boolean idVerified,
                         double rating, int reviewCount, int completedSits) {
        this.sitterId = sitterId;
        this.displayName = displayName;
        this.services = services.clone();
        this.ratePerVisitCents = ratePerVisitCents;
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

    public int getRatePerVisitCents() {
        return ratePerVisitCents;
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
