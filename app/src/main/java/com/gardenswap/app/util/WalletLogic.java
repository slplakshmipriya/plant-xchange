package com.gardenswap.app.util;

/**
 * Pure wallet display logic (UID-015). No Android dependencies, so this runs
 * as a plain JVM unit test.
 *
 * <p>Expiry-cue rule ({@link #expiryCue}):
 * <ul>
 *   <li>null input → null (nothing expiring)</li>
 *   <li>negative → null (already expired; the past-due case stays with
 *       {@link LedgerFormatter#expiryBanner})</li>
 *   <li>0 → "today", 1 → "tomorrow", 2–7 → "in N days"</li>
 *   <li>more than 7 days → null (not expiring soon)</li>
 * </ul>
 */
public final class WalletLogic {

    /** Credits expiring within this many days count as "expiring soon". */
    public static final int EXPIRY_SOON_DAYS = 7;

    private WalletLogic() {
    }

    /**
     * Short urgency line for soon-to-expire credits, or null when nothing is
     * expiring soon. Day-based only — the caller derives daysUntilExpiry from
     * {@code Wallet.getNextExpiryMs()}.
     */
    public static String expiryCue(Integer daysUntilExpiry) {
        if (daysUntilExpiry == null
                || daysUntilExpiry < 0
                || daysUntilExpiry > EXPIRY_SOON_DAYS) {
            return null;
        }
        if (daysUntilExpiry == 0) {
            return "Credits expire today — spend them!";
        }
        if (daysUntilExpiry == 1) {
            return "Credits expire tomorrow — spend them!";
        }
        return "Credits expire in " + daysUntilExpiry + " days — spend them!";
    }
}
