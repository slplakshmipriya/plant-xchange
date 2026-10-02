package com.gardenswap.test.util;

/**
 * Pure credit-economy arithmetic (PRD section 8). No Android dependencies,
 * so this runs as a plain JVM unit test.
 *
 * <p>Rules encoded here:
 * <ul>
 *   <li>New accounts start with {@link #STARTER_CREDITS} credits.</li>
 *   <li>Listing costs are bounded to [{@link #MIN_LISTING_COST},
 *       {@link #MAX_LISTING_COST}] credits.</li>
 *   <li>Earning is capped at {@link #WEEKLY_EARN_CAP} credits per week.</li>
 * </ul>
 *
 * <p><b>Client-side mirror only.</b> The backend ledger (API-060/API-061) is
 * the single source of truth for balances, issuance, and caps — the server
 * never trusts values computed here (SEC-060). These helpers exist so the UI
 * can render affordability and remaining-cap affordances without a round trip;
 * every constant must match the backend's values.
 */
public final class CreditMath {

    public static final int STARTER_CREDITS = 3;
    public static final int MIN_LISTING_COST = 1;
    public static final int MAX_LISTING_COST = 100;
    public static final int WEEKLY_EARN_CAP = 10;

    private CreditMath() {
        // utility class
    }

    /** Balance granted to a brand-new account. */
    public static int starterBalance() {
        return STARTER_CREDITS;
    }

    /** Clamp a requested listing cost into the allowed 1-100 credit band. */
    public static int clampListingCost(int requested) {
        return Math.max(MIN_LISTING_COST, Math.min(MAX_LISTING_COST, requested));
    }

    /** True when {@code balance} covers {@code cost}. */
    public static boolean canAfford(int balance, int cost) {
        return cost >= 0 && balance >= cost;
    }

    /**
     * Returns the balance after spending {@code cost} credits.
     *
     * @throws IllegalArgumentException if cost is negative or exceeds the balance
     */
    public static int applySpend(int balance, int cost) {
        if (cost < 0) {
            throw new IllegalArgumentException("cost must be >= 0, was " + cost);
        }
        if (balance < cost) {
            throw new IllegalArgumentException(
                    "insufficient credits: balance=" + balance + ", cost=" + cost);
        }
        return balance - cost;
    }

    /**
     * Returns the balance after issuing {@code amount} credits.
     *
     * @throws IllegalArgumentException if amount is negative
     */
    public static int applyIssue(int balance, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0, was " + amount);
        }
        return balance + amount;
    }

    /** Credits the user may still earn this week given {@code earnedThisWeek}. */
    public static int weeklyEarnRemaining(int earnedThisWeek) {
        return Math.max(0, WEEKLY_EARN_CAP - Math.max(0, earnedThisWeek));
    }

    /** True when {@code nowEpochMs} has reached {@code expiryEpochMs}. */
    public static boolean isExpired(long expiryEpochMs, long nowEpochMs) {
        return nowEpochMs >= expiryEpochMs;
    }
}
