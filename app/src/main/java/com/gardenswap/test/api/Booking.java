package com.gardenswap.test.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A sitting booking (API-070). Immutable.
 *
 * <p>Bookings carry the explicit {@code dates} list the booker picked (no
 * start/end range) and the requested {@code services}. Fee flip: the
 * customer is charged the subtotal only; the 18% fee is deducted from the
 * sitter's payout ({@code customer_total_cents == subtotal_cents},
 * {@code sitter_payout_cents == subtotal_cents - fee_cents}).
 */
public final class Booking {

    private final String bookingId;
    private final String sitterId;
    private final BookingStatus status;
    /** Sorted ISO dates (yyyy-MM-dd) covered by the booking. */
    private final List<String> dates;
    private final String[] services;
    /** Subtotal before the platform fee, in cents. */
    private final int subtotalCents;
    /** Platform fee (18%), in cents — deducted from the sitter's payout. */
    private final int feeCents;
    /** Total charged to the customer, in cents (= subtotal). */
    private final int customerTotalCents;
    /** Amount the sitter receives, in cents (= subtotal − fee). */
    private final int sitterPayoutCents;
    private final String careInstructions;

    public Booking(String bookingId, String sitterId, BookingStatus status,
                   List<String> dates, String[] services,
                   int subtotalCents, int feeCents, int customerTotalCents,
                   int sitterPayoutCents, String careInstructions) {
        this.bookingId = bookingId;
        this.sitterId = sitterId;
        this.status = status;
        List<String> copy = new ArrayList<>();
        if (dates != null) {
            for (String d : dates) {
                if (d != null && !d.trim().isEmpty()) {
                    copy.add(d.trim());
                }
            }
        }
        Collections.sort(copy);
        this.dates = Collections.unmodifiableList(copy);
        this.services = services == null ? new String[0] : services.clone();
        this.subtotalCents = subtotalCents;
        this.feeCents = feeCents;
        this.customerTotalCents = customerTotalCents;
        this.sitterPayoutCents = sitterPayoutCents;
        this.careInstructions = careInstructions;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getSitterId() {
        return sitterId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    /** Unmodifiable sorted list of ISO dates (yyyy-MM-dd). */
    public List<String> getDates() {
        return dates;
    }

    /** Number of sitting days. */
    public int getDateCount() {
        return dates.size();
    }

    public String[] getServices() {
        return services.clone();
    }

    /** Subtotal before the platform fee, in cents. */
    public int getSubtotalCents() {
        return subtotalCents;
    }

    /** Platform fee (18%), in cents — deducted from the sitter's payout. */
    public int getFeeCents() {
        return feeCents;
    }

    /** Total charged to the customer, in cents (= subtotal). */
    public int getCustomerTotalCents() {
        return customerTotalCents;
    }

    /** Amount the sitter receives, in cents (= subtotal − fee). */
    public int getSitterPayoutCents() {
        return sitterPayoutCents;
    }

    public String getCareInstructions() {
        return careInstructions;
    }
}
