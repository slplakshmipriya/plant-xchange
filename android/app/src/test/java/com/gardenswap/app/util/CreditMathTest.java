package com.gardenswap.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM unit tests for {@link CreditMath} (AND-001). No Android dependencies. */
public class CreditMathTest {

    @Test
    public void starterBalance_isThreeCredits() {
        assertEquals(3, CreditMath.starterBalance());
    }

    @Test
    public void clampListingCost_boundsToOneThroughThree() {
        assertEquals(1, CreditMath.clampListingCost(0));
        assertEquals(1, CreditMath.clampListingCost(-5));
        assertEquals(1, CreditMath.clampListingCost(1));
        assertEquals(2, CreditMath.clampListingCost(2));
        assertEquals(3, CreditMath.clampListingCost(3));
        assertEquals(3, CreditMath.clampListingCost(99));
    }

    @Test
    public void canAfford_exactBalanceIsAffordable() {
        assertTrue(CreditMath.canAfford(3, 3));
        assertTrue(CreditMath.canAfford(5, 1));
        assertTrue(CreditMath.canAfford(3, 0));
        assertFalse(CreditMath.canAfford(2, 3));
        assertFalse(CreditMath.canAfford(0, 1));
        assertFalse(CreditMath.canAfford(3, -1));
    }

    @Test
    public void applySpend_deductsCost() {
        assertEquals(1, CreditMath.applySpend(3, 2));
        assertEquals(0, CreditMath.applySpend(3, 3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void applySpend_insufficientBalanceThrows() {
        CreditMath.applySpend(2, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void applySpend_negativeCostThrows() {
        CreditMath.applySpend(3, -1);
    }

    @Test
    public void applyIssue_addsCredits() {
        assertEquals(5, CreditMath.applyIssue(3, 2));
        assertEquals(3, CreditMath.applyIssue(3, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void applyIssue_negativeAmountThrows() {
        CreditMath.applyIssue(3, -2);
    }

    @Test
    public void weeklyEarnRemaining_capsAtTen() {
        assertEquals(10, CreditMath.weeklyEarnRemaining(0));
        assertEquals(4, CreditMath.weeklyEarnRemaining(6));
        assertEquals(0, CreditMath.weeklyEarnRemaining(10));
        assertEquals(0, CreditMath.weeklyEarnRemaining(25));
        assertEquals(10, CreditMath.weeklyEarnRemaining(-3));
    }

    @Test
    public void isExpired_boundaryIsInclusive() {
        long expiry = 1_700_000_000_000L;
        assertFalse(CreditMath.isExpired(expiry, expiry - 1));
        assertTrue(CreditMath.isExpired(expiry, expiry));
        assertTrue(CreditMath.isExpired(expiry, expiry + 1));
    }

    @Test
    public void spendThenIssue_roundTrips() {
        int balance = CreditMath.starterBalance();
        balance = CreditMath.applySpend(balance, 2); // 1
        balance = CreditMath.applyIssue(balance, 3); // 4
        assertEquals(4, balance);
        assertTrue(CreditMath.canAfford(balance, 3));
    }
}
