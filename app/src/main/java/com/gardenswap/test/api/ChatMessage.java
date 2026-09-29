package com.gardenswap.test.api;

/** One chat message (proposed: API-080). Immutable. */
public final class ChatMessage {

    public enum Kind {
        TEXT,
        PHOTO,
        SYSTEM
    }

    private final String messageId;
    private final String threadId;
    private final String senderName;
    private final boolean mine;
    private final Kind kind;
    private final String text;
    private final long sentAtMs;

    public ChatMessage(String messageId, String threadId, String senderName,
                       boolean mine, Kind kind, String text, long sentAtMs) {
        this.messageId = messageId;
        this.threadId = threadId;
        this.senderName = senderName;
        this.mine = mine;
        this.kind = kind;
        this.text = text;
        this.sentAtMs = sentAtMs;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getSenderName() {
        return senderName;
    }

    public boolean isMine() {
        return mine;
    }

    public Kind getKind() {
        return kind;
    }

    /** Text body, photo URL when {@link Kind#PHOTO}, copy when {@link Kind#SYSTEM}. */
    public String getText() {
        return text;
    }

    public long getSentAtMs() {
        return sentAtMs;
    }
}
