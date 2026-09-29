package com.gardenswap.test.api;

/**
 * A plant/credit swap between two gardeners (UID-023).
 *
 * <p><b>Placeholder model.</b> There is no swap-history endpoint yet, so the
 * profile screens render {@link com.gardenswap.test.profile.SwapSamples} until
 * the backend API lands. Reuses {@link BookingStatus} for lifecycle because
 * the swap states (requested → confirmed → in progress → completed /
 * cancelled) match the booking state machine.
 */
public final class Swap {

    private final String swapId;
    private final String counterparty;
    private final String listingTitle;
    private final BookingStatus status;

    public Swap(String swapId, String counterparty, String listingTitle,
                BookingStatus status) {
        this.swapId = swapId;
        this.counterparty = counterparty;
        this.listingTitle = listingTitle;
        this.status = status;
    }

    public String getSwapId() {
        return swapId;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public String getListingTitle() {
        return listingTitle;
    }

    public BookingStatus getStatus() {
        return status;
    }
}
