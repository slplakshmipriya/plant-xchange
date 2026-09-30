package com.gardenswap.test.api;

/**
 * A two-sided review (proposed: API-072). Immutable.
 *
 * <p>Server accepts reviews only post-completion, once per side — this
 * object is the payload, not the enforcement.
 */
public final class Review {

    private final int rating;
    private final String[] tags;
    private final String text;
    /** Who wrote this review: "OWNER" or "SITTER". */
    private final String reviewerRole;
    /** True when the booking is verified complete server-side. */
    private final boolean verifiedBooking;
    /**
     * ISO-8601 creation timestamp from {@code GET /v1/sitters/{uid}/reviews};
     * null for locally-composed reviews that were never fetched.
     */
    private final String createdAt;

    public Review(int rating, String[] tags, String text) {
        this(rating, tags, text, "OWNER", false, null);
    }

    public Review(int rating, String[] tags, String text,
            String reviewerRole, boolean verifiedBooking) {
        this(rating, tags, text, reviewerRole, verifiedBooking, null);
    }

    public Review(int rating, String[] tags, String text,
            String reviewerRole, boolean verifiedBooking, String createdAt) {
        this.rating = rating;
        this.tags = tags == null ? new String[0] : tags.clone();
        this.text = text;
        this.reviewerRole = reviewerRole == null ? "OWNER" : reviewerRole;
        this.verifiedBooking = verifiedBooking;
        this.createdAt = createdAt;
    }

    /** 1–5, validated client-side by {@code ReviewGuard}, enforced server-side. */
    public int getRating() {
        return rating;
    }

    public String[] getTags() {
        return tags.clone();
    }

    public String getText() {
        return text;
    }

    public String getReviewerRole() {
        return reviewerRole;
    }

    public boolean isVerifiedBooking() {
        return verifiedBooking;
    }

    /** ISO-8601 creation timestamp, or null when not fetched from the server. */
    public String getCreatedAt() {
        return createdAt;
    }

    public static Builder builder(int rating) {
        return new Builder(rating);
    }

    public static final class Builder {
        private final int rating;
        private String[] tags = new String[0];
        private String text;
        private String reviewerRole = "OWNER";
        private boolean verifiedBooking = false;
        private String createdAt;

        private Builder(int rating) {
            this.rating = rating;
        }

        public Builder tags(String[] tags) {
            this.tags = tags == null ? new String[0] : tags.clone();
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder reviewerRole(String reviewerRole) {
            this.reviewerRole = reviewerRole;
            return this;
        }

        public Builder verifiedBooking(boolean verifiedBooking) {
            this.verifiedBooking = verifiedBooking;
            return this;
        }

        public Builder createdAt(String createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Review build() {
            return new Review(rating, tags, text, reviewerRole, verifiedBooking,
                    createdAt);
        }
    }
}
