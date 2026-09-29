package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gardenswap.test.util.ExchangeConfirmLogic.State;

import org.junit.Test;

public class ExchangeConfirmLogicTest {

    @Test
    public void canActOnlyWhenPending() {
        assertTrue(ExchangeConfirmLogic.canAct(State.PENDING));
        assertFalse(ExchangeConfirmLogic.canAct(State.CONFIRMING));
        assertFalse(ExchangeConfirmLogic.canAct(State.CONFIRMED));
        assertFalse(ExchangeConfirmLogic.canAct(State.DECLINED));
    }

    @Test
    public void confirmStartedTransitions() {
        assertEquals(State.CONFIRMING,
                ExchangeConfirmLogic.onConfirmStarted(State.PENDING));
    }

    @Test
    public void confirmStartedIgnoredWhenNotPending() {
        assertEquals(State.CONFIRMING,
                ExchangeConfirmLogic.onConfirmStarted(State.CONFIRMING));
        assertEquals(State.CONFIRMED,
                ExchangeConfirmLogic.onConfirmStarted(State.CONFIRMED));
        assertEquals(State.DECLINED,
                ExchangeConfirmLogic.onConfirmStarted(State.DECLINED));
    }

    @Test
    public void confirmErrorReturnsToPending() {
        assertEquals(State.PENDING,
                ExchangeConfirmLogic.onConfirmError(State.CONFIRMING));
    }

    @Test
    public void confirmErrorIgnoredWhenNotConfirming() {
        assertEquals(State.PENDING,
                ExchangeConfirmLogic.onConfirmError(State.PENDING));
        assertEquals(State.CONFIRMED,
                ExchangeConfirmLogic.onConfirmError(State.CONFIRMED));
    }

    @Test
    public void confirmSucceededTransitions() {
        assertEquals(State.CONFIRMED,
                ExchangeConfirmLogic.onConfirmSucceeded(State.CONFIRMING));
        assertEquals(State.PENDING,
                ExchangeConfirmLogic.onConfirmSucceeded(State.PENDING));
    }

    @Test
    public void declineTransitionsFromPendingOnly() {
        assertEquals(State.DECLINED,
                ExchangeConfirmLogic.onDecline(State.PENDING));
        assertEquals(State.CONFIRMING,
                ExchangeConfirmLogic.onDecline(State.CONFIRMING));
        assertEquals(State.CONFIRMED,
                ExchangeConfirmLogic.onDecline(State.CONFIRMED));
        assertEquals(State.DECLINED,
                ExchangeConfirmLogic.onDecline(State.DECLINED));
    }

    @Test
    public void doubleConfirmGuarded() {
        State state = State.PENDING;
        state = ExchangeConfirmLogic.onConfirmStarted(state);
        state = ExchangeConfirmLogic.onConfirmSucceeded(state);
        assertEquals(State.CONFIRMED, state);
        assertFalse(ExchangeConfirmLogic.canAct(state));
        // Second tap is a no-op: still CONFIRMED, never re-enters CONFIRMING.
        assertEquals(State.CONFIRMED,
                ExchangeConfirmLogic.onConfirmStarted(state));
    }

    @Test
    public void doubleDeclineGuarded() {
        State state = ExchangeConfirmLogic.onDecline(State.PENDING);
        assertEquals(State.DECLINED, state);
        assertFalse(ExchangeConfirmLogic.canAct(state));
        assertEquals(State.DECLINED,
                ExchangeConfirmLogic.onDecline(state));
    }

    @Test
    public void statusMessagePendingEmpty() {
        assertEquals("", ExchangeConfirmLogic.statusMessage(State.PENDING, 2, false));
    }

    @Test
    public void statusMessageConfirming() {
        assertEquals("Confirming…",
                ExchangeConfirmLogic.statusMessage(State.CONFIRMING, 2, false));
    }

    @Test
    public void statusMessageConfirmedWaiting() {
        assertEquals("You've confirmed. Waiting for the other person — "
                        + "credits move when they confirm too.",
                ExchangeConfirmLogic.statusMessage(State.CONFIRMED, 2, false));
    }

    @Test
    public void statusMessageConfirmedDone() {
        assertEquals("Done! 2 credits moved — both sides confirmed.",
                ExchangeConfirmLogic.statusMessage(State.CONFIRMED, 2, true));
        assertEquals("Done! 1 credit moved — both sides confirmed.",
                ExchangeConfirmLogic.statusMessage(State.CONFIRMED, 1, true));
    }

    @Test
    public void statusMessageDeclined() {
        assertEquals("You've declined this exchange. No credits moved.",
                ExchangeConfirmLogic.statusMessage(State.DECLINED, 2, false));
    }

    @Test
    public void creditLinePluralization() {
        assertEquals("1 credit", ExchangeConfirmLogic.creditLine(1));
        assertEquals("2 credits", ExchangeConfirmLogic.creditLine(2));
        assertEquals("0 credits", ExchangeConfirmLogic.creditLine(0));
    }
}
