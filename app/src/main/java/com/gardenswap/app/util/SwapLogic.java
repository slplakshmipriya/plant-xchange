package com.gardenswap.app.util;

import com.gardenswap.app.api.BookingStatus;
import com.gardenswap.app.api.Swap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-JVM swap-list logic (UID-023). No Android dependencies.
 *
 * <p>A swap is <b>active</b> while it can still change state
 * ({@code REQUESTED}, {@code CONFIRMED}, {@code IN_PROGRESS}); it is
 * <b>completed</b> once it reaches a terminal state ({@code COMPLETED},
 * {@code CANCELLED}). Cancelled swaps land in the completed/history
 * section rather than disappearing. Null entries are skipped and a null
 * status is treated as active (visible beats hidden).
 */
public final class SwapLogic {

    private SwapLogic() {
    }

    /** Active vs completed swap lists. Immutable snapshots. */
    public static final class Partition {
        private final List<Swap> active;
        private final List<Swap> completed;

        private Partition(List<Swap> active, List<Swap> completed) {
            this.active = Collections.unmodifiableList(active);
            this.completed = Collections.unmodifiableList(completed);
        }

        public List<Swap> active() {
            return active;
        }

        public List<Swap> completed() {
            return completed;
        }
    }

    /** Splits {@code swaps} into active and completed lists. Null-safe. */
    public static Partition partition(List<Swap> swaps) {
        List<Swap> active = new ArrayList<>();
        List<Swap> completed = new ArrayList<>();
        if (swaps != null) {
            for (Swap swap : swaps) {
                if (swap == null) {
                    continue;
                }
                if (isActive(swap.getStatus())) {
                    active.add(swap);
                } else {
                    completed.add(swap);
                }
            }
        }
        return new Partition(active, completed);
    }

    /** True for REQUESTED, CONFIRMED, IN_PROGRESS (and null). */
    public static boolean isActive(BookingStatus status) {
        if (status == null) {
            return true;
        }
        switch (status) {
            case REQUESTED:
            case CONFIRMED:
            case IN_PROGRESS:
                return true;
            case COMPLETED:
            case CANCELLED:
            default:
                return false;
        }
    }
}
