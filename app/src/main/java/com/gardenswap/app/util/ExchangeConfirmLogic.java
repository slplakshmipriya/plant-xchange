package com.gardenswap.app.util;

import com.gardenswap.app.api.ExchangeConfirmation;

/**
 * Pure state machine for the two-party exchange confirmation screen (UID-016).
 *
 * <p>States: {@code PENDING -> CONFIRMING -> CONFIRMED}, or
 * {@code PENDING -> DECLINED}. Guards against double-confirm and
 * double-decline. Decline is mock-only: the API contract exposes
 * {@code confirmExchange} but no decline endpoint, so declining never
 * touches the network. No Android dependencies — unit-testable on the JVM.
 */
public final class ExchangeConfirmLogic {

    public enum State {
        PENDING,
        CONFIRMING,
        CONFIRMED,
        DECLINED
    }

    private ExchangeConfirmLogic() {
    }

    /** True only when the user may still act (guards double-confirm/decline). */
    public static boolean canAct(State state) {
        return state == State.PENDING;
    }

    /** User tapped Confirm. */
    public static State onConfirmStarted(State state) {
        return state == State.PENDING ? State.CONFIRMING : state;
    }

    /** Confirm API call failed — back to PENDING so the user can retry. */
    public static State onConfirmError(State state) {
        return state == State.CONFIRMING ? State.PENDING : state;
    }

    /** Confirm API call succeeded. */
    public static State onConfirmSucceeded(State state) {
        return state == State.CONFIRMING ? State.CONFIRMED : state;
    }

    /** User tapped Decline (mock-only: no decline endpoint exists). */
    public static State onDecline(State state) {
        return state == State.PENDING ? State.DECLINED : state;
    }

    /** "2 credits" / "1 credit". */
    public static String creditLine(int creditCost) {
        return creditCost + " credit" + (creditCost == 1 ? "" : "s");
    }

    /**
     * Status line for the current state. {@code otherConfirmed} comes from
     * the {@link ExchangeConfirmation} result once confirmed.
     */
    public static String statusMessage(State state, int creditCost, boolean otherConfirmed) {
        switch (state) {
            case CONFIRMING:
                return "Confirming…";
            case CONFIRMED:
                if (otherConfirmed) {
                    return "Done! " + creditLine(creditCost)
                            + " moved — both sides confirmed.";
                }
                return "You've confirmed. Waiting for the other person — "
                        + "credits move when they confirm too.";
            case DECLINED:
                return "You've declined this exchange. No credits moved.";
            case PENDING:
            default:
                return "";
        }
    }
}
