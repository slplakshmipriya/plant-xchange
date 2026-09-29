package com.gardenswap.test.util;

import com.gardenswap.test.api.ListingStatus;

/**
 * State-driven rules for the listing detail screen (AND-021).
 *
 * <p>Pure JVM — no Android dependencies. The backend is the authority on the
 * lifecycle; this class only decides what the UI <i>offers</i>, never what
 * the server allows.
 */
public final class ListingDetailLogic {

    private ListingDetailLogic() {
    }

    /**
     * Whether the viewer may claim: listing is live and the viewer is not the
     * owner. Claiming your own listing is meaningless and hidden.
     */
    public static boolean canClaim(ListingStatus status, String ownerUid, String viewerUid) {
        return status == ListingStatus.LIVE
                && ownerUid != null
                && !ownerUid.equals(viewerUid);
    }

    /** Whether the owner may cancel: only from draft/live. */
    public static boolean canCancel(ListingStatus status, String ownerUid, String viewerUid) {
        return status != null && status.isEditable()
                && ownerUid != null && ownerUid.equals(viewerUid);
    }

    /** Whether the owner may edit fields: draft/live only. */
    public static boolean canEdit(ListingStatus status, String ownerUid, String viewerUid) {
        return canCancel(status, ownerUid, viewerUid);
    }

    /** Human label for the status chip. */
    public static String statusLabel(ListingStatus status) {
        if (status == null) {
            return "Unknown";
        }
        switch (status) {
            case DRAFT:
                return "Draft";
            case LIVE:
                return "Live";
            case CLAIMED:
                return "Claimed";
            case COMPLETED:
                return "Completed";
            case EXPIRED:
                return "Expired";
            case CANCELLED:
                return "Cancelled";
            default:
                return "Unknown";
        }
    }

    /**
     * Freshness countdown. Null expiry → "No expiry set". Past → "Expired".
     * Otherwise the largest two units ("3d 4h", "5h 12m", "45m", "30s").
     */
    public static String formatCountdown(Long expiresAtMs, long nowMs) {
        if (expiresAtMs == null) {
            return "No expiry set";
        }
        long remaining = expiresAtMs - nowMs;
        if (remaining <= 0) {
            return "Expired";
        }
        long seconds = remaining / 1000;
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return seconds + "s";
    }
}
