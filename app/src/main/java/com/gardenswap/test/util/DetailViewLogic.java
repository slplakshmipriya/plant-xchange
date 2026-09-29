package com.gardenswap.test.util;

import com.gardenswap.test.api.ListingStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure view-state rules for the listing detail screen (UID-012).
 *
 * <p>JVM-only, no Android dependencies. Decides <i>what</i> the detail
 * screen shows; {@code ListingDetailActivity} decides <i>how</i> it looks.
 * Lifecycle authority stays in {@link ListingDetailLogic} — this class only
 * formats display decisions extracted from the activity so they are
 * unit-testable.
 */
public final class DetailViewLogic {

    private DetailViewLogic() {
    }

    /** Claim CTA visibility: live listing and the viewer is not the owner. */
    public static boolean isClaimCtaVisible(ListingStatus status, String ownerUid, String viewerUid) {
        return ListingDetailLogic.canClaim(status, ownerUid, viewerUid);
    }

    /** Cancel CTA visibility: owner, and the listing is still editable. */
    public static boolean isCancelCtaVisible(ListingStatus status, String ownerUid, String viewerUid) {
        return ListingDetailLogic.canCancel(status, ownerUid, viewerUid);
    }

    /** Terminal-state read-only notice visibility. */
    public static boolean showTerminalNotice(ListingStatus status) {
        return status != null && status.isTerminal();
    }

    /** "Free — no credits needed" / "1 credit" / "2 credits". */
    public static String creditLine(boolean free, int creditCost) {
        if (free) {
            return "Free \u2014 no credits needed";
        }
        return creditCost + (creditCost == 1 ? " credit" : " credits");
    }

    /** "6 starts" / "1.5 kg"; null when quantity is null. */
    public static String quantityLine(Double quantity, String unit) {
        if (quantity == null) {
            return null;
        }
        String amount = trim(quantity);
        if (unit == null || unit.trim().isEmpty()) {
            return amount;
        }
        return amount + " " + unit.trim();
    }

    /** "3 photos" / "1 photo" / "No photos". */
    public static String photoCountLabel(int count) {
        if (count <= 0) {
            return "No photos";
        }
        return count + (count == 1 ? " photo" : " photos");
    }

    /**
     * Visit rules split into display items, one per non-blank line.
     * Empty list when rules are null or blank.
     */
    public static List<String> visitRuleItems(String visitRules) {
        if (visitRules == null) {
            return Collections.emptyList();
        }
        List<String> items = new ArrayList<>();
        for (String line : visitRules.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return Collections.unmodifiableList(items);
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
