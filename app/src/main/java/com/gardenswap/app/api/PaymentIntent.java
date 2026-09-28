package com.gardenswap.app.api;

/**
 * Stripe payment intent for a sitting booking (PRD parity, r2 contract).
 *
 * <p>Created per {@code POST /v1/payments/sitting-intent}; the client hands
 * {@link #getClientSecret()} to the Stripe SDK to complete payment.
 * Immutable.
 */
public final class PaymentIntent {

    private final String clientSecret;

    public PaymentIntent(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /** Stripe client secret for confirming the payment. */
    public String getClientSecret() {
        return clientSecret;
    }
}
