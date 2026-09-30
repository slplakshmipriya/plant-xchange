package com.gardenswap.test.api;

/**
 * Claim request (planned: API-135, {@code POST /v1/listings/{id}/claims}).
 *
 * <p>Partial claims (harvest): {@code quantity} is kilos/units in the
 * listing's {@code unit}. The backend decrements {@code remaining_qty} and
 * assigns the claimer atomically.
 */
public final class ClaimRequest {

    /** Kilos/units claimed. Quantity is required on listings because the claim
     * flow (stepper, partial claims, remaining_qty) is measured against it;
     * listings without one predate that requirement and can't be claimed. */
    public final int quantity;
    /** Nullable pickup window start, epoch ms. */
    public final Long pickupStartMs;
    /** Nullable pickup window end, epoch ms. */
    public final Long pickupEndMs;
    /** Nullable note to the giver. */
    public final String notes;

    public ClaimRequest(int quantity, Long pickupStartMs, Long pickupEndMs, String notes) {
        this.quantity = quantity;
        this.pickupStartMs = pickupStartMs;
        this.pickupEndMs = pickupEndMs;
        this.notes = notes;
    }

    /** Whole-listing claim with no window or notes. */
    public static ClaimRequest single() {
        return new ClaimRequest(1, null, null, null);
    }
}
