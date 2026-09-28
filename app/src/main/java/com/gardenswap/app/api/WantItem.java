package com.gardenswap.app.api;

/** One wanted variety on the signed-in user's want-list (AND-030). */
public final class WantItem {

    private final String id;
    private final String variety;
    private final long createdAtMs;

    public WantItem(String id, String variety, long createdAtMs) {
        this.id = id;
        this.variety = variety;
        this.createdAtMs = createdAtMs;
    }

    public String getId() {
        return id;
    }

    public String getVariety() {
        return variety;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }
}
