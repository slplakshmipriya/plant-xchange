package com.gardenswap.test.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Seasonal credit expiry state (PRD parity, r2 contract).
 *
 * <p>Credits expire at season end; chunks expiring within 7 days drive the
 * expiry-nudge notification. Immutable.
 */
public final class CreditExpiry {

    /** One credit chunk with a fixed expiry instant. */
    public static final class ExpiringChunk {
        private final int credits;
        private final long expiresAtMs;

        public ExpiringChunk(int credits, long expiresAtMs) {
            this.credits = credits;
            this.expiresAtMs = expiresAtMs;
        }

        public int getCredits() {
            return credits;
        }

        public long getExpiresAtMs() {
            return expiresAtMs;
        }
    }

    private final int balance;
    private final List<ExpiringChunk> expiring;
    private final long seasonEndMs;

    public CreditExpiry(int balance, List<ExpiringChunk> expiring, long seasonEndMs) {
        this.balance = balance;
        this.expiring = Collections.unmodifiableList(
                new ArrayList<>(expiring == null ? new ArrayList<ExpiringChunk>()
                        : expiring));
        this.seasonEndMs = seasonEndMs;
    }

    public int getBalance() {
        return balance;
    }

    /** Chunks expiring before season end, nearest first per the wire. */
    public List<ExpiringChunk> getExpiring() {
        return expiring;
    }

    public long getSeasonEndMs() {
        return seasonEndMs;
    }
}
