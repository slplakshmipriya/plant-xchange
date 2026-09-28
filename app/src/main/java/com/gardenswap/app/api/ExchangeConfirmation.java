package com.gardenswap.app.api;

/**
 * Result of confirming one side of a credit exchange (proposed: API-060).
 * Credits move only when BOTH sides confirm — enforced server-side; this
 * object reports the resulting state.
 */
public final class ExchangeConfirmation {

    private final String exchangeId;
    private final boolean myConfirmed;
    private final boolean otherConfirmed;
    private final int creditCost;
    private final boolean creditsMoved;

    public ExchangeConfirmation(String exchangeId, boolean myConfirmed,
                                boolean otherConfirmed, int creditCost,
                                boolean creditsMoved) {
        this.exchangeId = exchangeId;
        this.myConfirmed = myConfirmed;
        this.otherConfirmed = otherConfirmed;
        this.creditCost = creditCost;
        this.creditsMoved = creditsMoved;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public boolean isMyConfirmed() {
        return myConfirmed;
    }

    public boolean isOtherConfirmed() {
        return otherConfirmed;
    }

    public int getCreditCost() {
        return creditCost;
    }

    /** True once both parties confirmed and the ledger entry was written. */
    public boolean isCreditsMoved() {
        return creditsMoved;
    }
}
