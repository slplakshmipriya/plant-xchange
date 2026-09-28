package com.gardenswap.app.api;

/**
 * Content report (planned: API-143, {@code POST /v1/reports}).
 *
 * <p>Abuse / safety / spam reports against a listing, user, or booking.
 * The backend triages; the client shows a confirmation only.
 */
public final class ReportRequest {

    /** One of {@code "LISTING"}, {@code "USER"}, {@code "BOOKING"}. */
    public final String targetType;
    public final String targetId;
    /** One of {@code "spam"}, {@code "safety"}, {@code "fraud"},
     * {@code "inappropriate"}, {@code "other"}. */
    public final String category;
    /** Nullable free-text details. */
    public final String details;

    public ReportRequest(String targetType, String targetId, String category, String details) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.category = category;
        this.details = details;
    }
}
