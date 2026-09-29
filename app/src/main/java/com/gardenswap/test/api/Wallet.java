package com.gardenswap.test.api;

import java.util.Collections;
import java.util.List;

/**
 * Credit wallet snapshot (proposed: API-060/061). Immutable.
 *
 * <p><b>Server-authoritative.</b> The backend ledger is the single source of
 * truth for balances (SEC-060) — this snapshot is display-only; the client
 * never computes balances from it.
 */
public final class Wallet {

    private final int balance;
    private final Long nextExpiryMs;
    private final int earnedThisWeek;
    private final List<LedgerEntry> entries;

    public Wallet(int balance, Long nextExpiryMs, int earnedThisWeek,
                  List<LedgerEntry> entries) {
        this.balance = balance;
        this.nextExpiryMs = nextExpiryMs;
        this.earnedThisWeek = earnedThisWeek;
        this.entries = Collections.unmodifiableList(entries);
    }

    /** Current credit balance, per the backend ledger. */
    public int getBalance() {
        return balance;
    }

    /** Soonest credit-expiry timestamp, or null when nothing is expiring. */
    public Long getNextExpiryMs() {
        return nextExpiryMs;
    }

    /** Credits earned this week (toward the 10/week cap). */
    public int getEarnedThisWeek() {
        return earnedThisWeek;
    }

    public List<LedgerEntry> getEntries() {
        return entries;
    }
}
