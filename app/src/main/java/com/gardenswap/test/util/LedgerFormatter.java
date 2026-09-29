package com.gardenswap.test.util;

import com.gardenswap.test.api.LedgerEntry;

/**
 * Pure display formatting for the credit wallet (AND-060). No Android
 * dependencies, so this runs as a plain JVM unit test.
 *
 * <p>Display-only: the backend ledger (API-060) is authoritative for
 * balances. Never derive a balance here — render what the server sent.
 */
public final class LedgerFormatter {

    private LedgerFormatter() {
    }

    /** "+2" / "−2" / "0" — signed delta for a ledger line. */
    public static String formatDelta(int delta) {
        if (delta > 0) {
            return "+" + delta;
        }
        if (delta < 0) {
            return "−" + Math.abs(delta); // U+2212, typographically correct minus
        }
        return "0";
    }

    /** One-line summary of a ledger entry: "+2 · Tomato seedling swap". */
    public static String formatEntry(LedgerEntry entry) {
        String reason = entry.getReason() == null || entry.getReason().isEmpty()
                ? entry.getKind().name().toLowerCase()
                : entry.getReason();
        return formatDelta(entry.getDelta()) + " · " + reason;
    }

    /**
     * Expiry banner text for the soonest-expiring credits, or null when
     * nothing is expiring.
     */
    public static String expiryBanner(Long nextExpiryMs, long nowMs) {
        if (nextExpiryMs == null) {
            return null;
        }
        long remaining = nextExpiryMs - nowMs;
        if (remaining <= 0) {
            return "Some credits have expired — check your ledger.";
        }
        return "Credits expire in " + RipeWindow.formatDuration(remaining)
                + ". Spend them before they're gone!";
    }

    /** Weekly cap affordance: "2 of 10 weekly credits earned". */
    public static String weeklyCapLine(int earnedThisWeek) {
        int remaining = CreditMath.weeklyEarnRemaining(earnedThisWeek);
        return earnedThisWeek + " of " + CreditMath.WEEKLY_EARN_CAP
                + " weekly credits earned (" + remaining + " left)";
    }

    /** Onboarding copy: every new account starts with 3 credits. */
    public static String starterNote() {
        return "You started with " + CreditMath.STARTER_CREDITS
                + " credits — earn more by sharing your harvest.";
    }
}
