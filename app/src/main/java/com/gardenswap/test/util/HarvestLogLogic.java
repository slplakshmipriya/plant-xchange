package com.gardenswap.test.util;

import com.gardenswap.test.api.HarvestEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Harvest-log arithmetic (AND-040). Pure JVM.
 *
 * <p>A harvest listing starts with an initial quantity; every event adds or
 * removes. Remaining = initial + sum(deltas), floored at zero. Progress is
 * the fraction of the original harvest already gone (0..1).
 */
public final class HarvestLogLogic {

    private HarvestLogLogic() {
    }

    /** Quantity remaining after applying all events; never negative. */
    public static double remaining(double initialQuantity, List<HarvestEvent> events) {
        double remaining = initialQuantity;
        if (events != null) {
            for (HarvestEvent event : events) {
                remaining += event.getDelta();
            }
        }
        return Math.max(0, remaining);
    }

    /**
     * Fraction of the original harvest already taken, 0..1. A harvest that
     * grew (net additions) reports 0 progress; an empty initial quantity
     * reports 1 when anything was taken.
     */
    public static double progressTowardZero(double initialQuantity, List<HarvestEvent> events) {
        if (initialQuantity <= 0) {
            return remaining(0, events) <= 0 ? 1.0 : 0.0;
        }
        double taken = initialQuantity - remaining(initialQuantity, events);
        return Math.max(0, Math.min(1, taken / initialQuantity));
    }

    /** Events newest-first for display. */
    public static List<HarvestEvent> newestFirst(List<HarvestEvent> events) {
        List<HarvestEvent> sorted = new ArrayList<>(events == null ? new ArrayList<HarvestEvent>() : events);
        sorted.sort((a, b) -> Long.compare(b.getCreatedAtMs(), a.getCreatedAtMs()));
        return sorted;
    }

    /** Validation error for a new event, or null when the delta is usable. */
    public static String validateDelta(String deltaText) {
        if (deltaText == null || deltaText.trim().isEmpty()) {
            return "Enter an amount.";
        }
        try {
            double delta = Double.parseDouble(deltaText.trim());
            if (delta == 0) {
                return "Amount cannot be zero.";
            }
            return null;
        } catch (NumberFormatException e) {
            return "Amount must be a number.";
        }
    }
}
