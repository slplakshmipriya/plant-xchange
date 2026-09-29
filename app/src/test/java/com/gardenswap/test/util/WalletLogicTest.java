package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WalletLogicTest {

    @Test
    public void expiringSoon() {
        assertEquals("Credits expire in 3 days — spend them!",
                WalletLogic.expiryCue(3));
    }

    @Test
    public void exactlySevenDaysCountsAsSoon() {
        assertEquals("Credits expire in 7 days — spend them!",
                WalletLogic.expiryCue(7));
    }

    @Test
    public void eightDaysIsNotSoon() {
        assertNull(WalletLogic.expiryCue(8));
    }

    @Test
    public void expiresToday() {
        assertEquals("Credits expire today — spend them!",
                WalletLogic.expiryCue(0));
    }

    @Test
    public void expiresTomorrow() {
        assertEquals("Credits expire tomorrow — spend them!",
                WalletLogic.expiryCue(1));
    }

    @Test
    public void nullWhenNothingExpiring() {
        assertNull(WalletLogic.expiryCue(null));
    }

    @Test
    public void nullWhenAlreadyExpired() {
        assertNull(WalletLogic.expiryCue(-1));
    }

    @Test
    public void nullWhenFarFuture() {
        assertNull(WalletLogic.expiryCue(365));
    }
}
