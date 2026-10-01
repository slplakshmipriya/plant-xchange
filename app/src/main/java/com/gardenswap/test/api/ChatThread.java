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
    /** Uid of the other participant; null when the wire doesn't identify them. */
    private final String participantUserId;
    /** Absolute URL of the other participant's avatar; null when unset. */
    private final String otherAvatarUrl;

    public ChatThread(String threadId, String listingSummary, String listingStatus,
                      String otherPartyName, String lastMessagePreview,
                      long lastMessageAtMs, int unreadCount) {
        this(threadId, listingSummary, listingStatus, otherPartyName,
                lastMessagePreview, lastMessageAtMs, unreadCount, null, null);
    }

    public ChatThread(String threadId, String listingSummary, String listingStatus,
                      String otherPartyName, String lastMessagePreview,
                      long lastMessageAtMs, int unreadCount, String participantUserId) {
        this(threadId, listingSummary, listingStatus, otherPartyName,
                lastMessagePreview, lastMessageAtMs, unreadCount, participantUserId, null);
    }

    public ChatThread(String threadId, String listingSummary, String listingStatus,
                      String otherPartyName, String lastMessagePreview,
                      long lastMessageAtMs, int unreadCount, String participantUserId,
                      String otherAvatarUrl) {
        this.threadId = threadId;
        this.listingSummary = listingSummary;
        this.listingStatus = listingStatus;
        this.otherPartyName = otherPartyName;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageAtMs = lastMessageAtMs;
        this.unreadCount = unreadCount;
        this.participantUserId = participantUserId;
        this.otherAvatarUrl = otherAvatarUrl;
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

    /** Uid of the other participant; null when the wire doesn't identify them. */
    public String getParticipantUserId() {
        return participantUserId;
    }

    /** Immutable copy with the participant uid set (optional wire field). */
    public ChatThread withParticipantUserId(String participantUserId) {
        return new ChatThread(threadId, listingSummary, listingStatus,
                otherPartyName, lastMessagePreview, lastMessageAtMs, unreadCount,
                participantUserId, otherAvatarUrl);
    }

    /** Absolute URL of the other participant's avatar; null when unset. */
    public String getOtherAvatarUrl() {
        return otherAvatarUrl;
    }

    /** Immutable copy with the other participant's avatar URL set. */
    public ChatThread withOtherAvatarUrl(String otherAvatarUrl) {
        return new ChatThread(threadId, listingSummary, listingStatus,
                otherPartyName, lastMessagePreview, lastMessageAtMs, unreadCount,
                participantUserId, otherAvatarUrl);
    }
}
