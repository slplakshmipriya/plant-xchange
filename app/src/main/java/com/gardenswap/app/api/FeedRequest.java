package com.gardenswap.app.api;

/**
 * Feed request (planned: API-123, {@code GET /v1/feed}).
 *
 * <p>{@code way} is nullable: {@code "seedling"}, {@code "harvest"},
 * {@code "pick"}, {@code "sitting"} — or null for the unfiltered feed.
 */
public final class FeedRequest {

    /** Nullable way filter; null = all ways. */
    public final String way;
    public final int limit;

    public FeedRequest(String way, int limit) {
        this.way = way;
        this.limit = limit;
    }
}
