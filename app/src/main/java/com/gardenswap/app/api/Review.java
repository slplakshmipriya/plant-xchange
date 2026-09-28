package com.gardenswap.app.api;

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

    public Review(int rating, String[] tags, String text) {
        this.rating = rating;
        this.tags = tags == null ? new String[0] : tags.clone();
        this.text = text;
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
}
