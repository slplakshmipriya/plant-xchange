package com.gardenswap.test.api;

/** One credit-ledger entry (proposed: API-060). Immutable. */
public final class LedgerEntry {

    public enum Kind {
        ISSUED, SPENT, EXPIRED
    }

    private final String entryId;
    private final int delta;
    private final Kind kind;
    private final String reason;
    private final String refId;
    private final long createdAtMs;
    private final Long expiresAtMs;

    public LedgerEntry(String entryId, int delta, Kind kind, String reason,
                       String refId, long createdAtMs, Long expiresAtMs) {
        this.entryId = entryId;
        this.delta = delta;
        this.kind = kind;
        this.reason = reason;
        this.refId = refId;
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
    }

    public String getEntryId() {
        return entryId;
    }

    public int getDelta() {
        return delta;
    }

    public Kind getKind() {
        return kind;
    }

    public String getReason() {
        return reason;
    }

    public String getRefId() {
        return refId;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public Long getExpiresAtMs() {
        return expiresAtMs;
    }
}
