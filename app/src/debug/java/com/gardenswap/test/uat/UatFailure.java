package com.gardenswap.test.uat;

/**
 * Thrown when a UAT step observes the wrong outcome. The message should
 * describe what the user would have seen, e.g. "expected 2 photos on the
 * listing detail, the API returned 0".
 */
public final class UatFailure extends Exception {
    public UatFailure(String message) {
        super(message);
    }

    public UatFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
