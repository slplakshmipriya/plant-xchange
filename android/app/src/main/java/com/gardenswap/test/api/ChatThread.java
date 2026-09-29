package com.gardenswap.test.api;

/** A chat thread, one per exchange/booking (proposed: API-080). Immutable. */
public final class ChatThread {

    private final String threadId;
    private final String listingSummary;
    private final String listingStatus;
    private final String otherPartyName;
    private final String lastMessagePreview;
    private final long lastMessageAtMs;
    private final int unreadCount;

    public ChatThread(String threadId, String listingSummary, String listingStatus,
                      String otherPartyName, String lastMessagePreview,
                      long lastMessageAtMs, int unreadCount) {
        this.threadId = threadId;
        this.listingSummary = listingSummary;
        this.listingStatus = listingStatus;
        this.otherPartyName = otherPartyName;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageAtMs = lastMessageAtMs;
        this.unreadCount = unreadCount;
    }

    public String getThreadId() {
        return threadId;
    }

    /** Short context line shown under the thread (e.g. "Cherokee Purple tomato · 2 credits"). */
    public String getListingSummary() {
        return listingSummary;
    }

    public String getListingStatus() {
        return listingStatus;
    }

    public String getOtherPartyName() {
        return otherPartyName;
    }

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public long getLastMessageAtMs() {
        return lastMessageAtMs;
    }

    public int getUnreadCount() {
        return unreadCount;
    }
}
