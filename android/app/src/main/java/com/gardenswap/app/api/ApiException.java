package com.gardenswap.app.api;

/** Typed failure from {@link GardenSwapApi}. */
public class ApiException extends Exception {

    private final String code;

    public ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** Machine-readable error code (e.g. "unauthorized", "rate_limited"). */
    public String getCode() {
        return code;
    }
}
