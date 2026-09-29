package com.gardenswap.test.profile;

import com.gardenswap.test.api.BookingStatus;
import com.gardenswap.test.api.Swap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Placeholder swap history (UID-023).
 *
 * <p><b>MOCK DATA — delete when the swap-history API lands.</b> The mock API
 * has no swap/booking-history endpoint, so both profile screens share this
 * hard-coded list. It exercises active vs completed partitioning and the
 * row UI; it is not real user data.
 */
final class SwapSamples {

    private SwapSamples() {
    }

    static List<Swap> swaps() {
        List<Swap> out = new ArrayList<>();
        out.add(new Swap("swap-1", "Ana",
                "Cherry tomato seedlings", BookingStatus.IN_PROGRESS));
        out.add(new Swap("swap-2", "Marcus",
                "Basil bunch", BookingStatus.REQUESTED));
        out.add(new Swap("swap-3", "Priya",
                "Zucchini harvest box", BookingStatus.COMPLETED));
        out.add(new Swap("swap-4", "Sam",
                "Mint cuttings", BookingStatus.CANCELLED));
        return Collections.unmodifiableList(out);
    }
}
