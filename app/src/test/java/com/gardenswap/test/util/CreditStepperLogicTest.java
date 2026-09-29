package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** JVM unit tests for {@link CreditStepperLogic} (UID-014). No Android dependencies. */
public class CreditStepperLogicTest {

    @Test
    public void clamp_boundsToMinAndMax() {
        assertEquals(1, CreditStepperLogic.clamp(0, 1, 3));
        assertEquals(1, CreditStepperLogic.clamp(-50, 1, 3));
        assertEquals(1, CreditStepperLogic.clamp(1, 1, 3));
        assertEquals(2, CreditStepperLogic.clamp(2, 1, 3));
        assertEquals(3, CreditStepperLogic.clamp(3, 1, 3));
        assertEquals(3, CreditStepperLogic.clamp(99, 1, 3));
    }

    @Test
    public void clamp_honoursCustomBounds() {
        assertEquals(5, CreditStepperLogic.clamp(2, 5, 10));
        assertEquals(10, CreditStepperLogic.clamp(42, 5, 10));
        assertEquals(7, CreditStepperLogic.clamp(7, 5, 10));
    }

    @Test
    public void increment_stepsUpAndStopsAtMax() {
        assertEquals(2, CreditStepperLogic.increment(1, 1, 3));
        assertEquals(3, CreditStepperLogic.increment(2, 1, 3));
        assertEquals(3, CreditStepperLogic.increment(3, 1, 3));
        assertEquals(3, CreditStepperLogic.increment(99, 1, 3));
    }

    @Test
    public void decrement_stepsDownAndStopsAtMin() {
        assertEquals(2, CreditStepperLogic.decrement(3, 1, 3));
        assertEquals(1, CreditStepperLogic.decrement(2, 1, 3));
        assertEquals(1, CreditStepperLogic.decrement(1, 1, 3));
        assertEquals(1, CreditStepperLogic.decrement(-5, 1, 3));
    }

    @Test
    public void creditBounds_matchProductContract() {
        assertEquals(1, CreditStepperLogic.MIN_CREDIT_COST);
        assertEquals(3, CreditStepperLogic.MAX_CREDIT_COST);
    }

    @Test
    public void stepper_neverLeavesOneThroughThree() {
        int value = CreditStepperLogic.MIN_CREDIT_COST;
        for (int i = 0; i < 10; i++) {
            value = CreditStepperLogic.increment(value,
                    CreditStepperLogic.MIN_CREDIT_COST,
                    CreditStepperLogic.MAX_CREDIT_COST);
        }
        assertEquals(3, value);
        for (int i = 0; i < 10; i++) {
            value = CreditStepperLogic.decrement(value,
                    CreditStepperLogic.MIN_CREDIT_COST,
                    CreditStepperLogic.MAX_CREDIT_COST);
        }
        assertEquals(1, value);
    }
}
