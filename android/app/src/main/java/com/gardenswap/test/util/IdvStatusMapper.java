package com.gardenswap.test.util;

import com.gardenswap.test.api.IdvStatus;
import com.gardenswap.test.ui.BadgeState;

/**
 * Maps backend verification state to badge UI state (AND-011).
 * Pure JVM logic — no Android dependencies.
 */
public final class IdvStatusMapper {

    private IdvStatusMapper() {
    }

    /** Backend IDV enum → badge state. */
    public static BadgeState map(IdvStatus status) {
        if (status == null) {
            return BadgeState.UNVERIFIED;
        }
        switch (status) {
            case VERIFIED:
                return BadgeState.ID_VERIFIED;
            case PENDING:
                return BadgeState.PENDING;
            case FAILED:
            case UNVERIFIED:
            default:
                return BadgeState.UNVERIFIED;
        }
    }

    /** Phone-auth state → badge state. */
    public static BadgeState mapPhone(boolean phoneVerified) {
        return phoneVerified ? BadgeState.PHONE_VERIFIED : BadgeState.UNVERIFIED;
    }
}
