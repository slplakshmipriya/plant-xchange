package com.gardenswap.app.util;

import static org.junit.Assert.assertEquals;

import com.gardenswap.app.api.IdvStatus;
import com.gardenswap.app.ui.BadgeState;

import org.junit.Test;

/** JVM unit tests for {@link IdvStatusMapper} (AND-011). No Android dependencies. */
public class IdvStatusMapperTest {

    @Test
    public void map_verifiedBecomesIdVerified() {
        assertEquals(BadgeState.ID_VERIFIED, IdvStatusMapper.map(IdvStatus.VERIFIED));
    }

    @Test
    public void map_pendingBecomesPending() {
        assertEquals(BadgeState.PENDING, IdvStatusMapper.map(IdvStatus.PENDING));
    }

    @Test
    public void map_failedBecomesUnverified() {
        assertEquals(BadgeState.UNVERIFIED, IdvStatusMapper.map(IdvStatus.FAILED));
    }

    @Test
    public void map_unverifiedBecomesUnverified() {
        assertEquals(BadgeState.UNVERIFIED, IdvStatusMapper.map(IdvStatus.UNVERIFIED));
    }

    @Test
    public void map_nullBecomesUnverified() {
        assertEquals(BadgeState.UNVERIFIED, IdvStatusMapper.map(null));
    }

    @Test
    public void map_wireStringsRoundTrip() {
        assertEquals(BadgeState.ID_VERIFIED,
                IdvStatusMapper.map(IdvStatus.fromString("verified")));
        assertEquals(BadgeState.PENDING,
                IdvStatusMapper.map(IdvStatus.fromString("PENDING")));
        assertEquals(BadgeState.UNVERIFIED,
                IdvStatusMapper.map(IdvStatus.fromString("bogus")));
        assertEquals(BadgeState.UNVERIFIED,
                IdvStatusMapper.map(IdvStatus.fromString(null)));
    }

    @Test
    public void mapPhone_trueBecomesPhoneVerified() {
        assertEquals(BadgeState.PHONE_VERIFIED, IdvStatusMapper.mapPhone(true));
    }

    @Test
    public void mapPhone_falseBecomesUnverified() {
        assertEquals(BadgeState.UNVERIFIED, IdvStatusMapper.mapPhone(false));
    }
}
