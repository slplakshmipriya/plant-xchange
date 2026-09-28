package com.gardenswap.app.api;

/**
 * Notification preferences for the signed-in user (PRD parity, r2 contract).
 *
 * <p>Wire form is {@code {categories: {harvest_alerts, want_matches,
 * expiry_nudges, credit_warnings, booking_reminders}, quiet_hours:
 * {start, end}}} with {@code "HH:mm"} quiet-hours values; either may be
 * null (no quiet hours).
 */
public final class NotificationPrefs {

    private final boolean harvestAlerts;
    private final boolean wantMatches;
    private final boolean expiryNudges;
    private final boolean creditWarnings;
    private final boolean bookingReminders;
    private final String quietHoursStart;
    private final String quietHoursEnd;

    private NotificationPrefs(Builder builder) {
        this.harvestAlerts = builder.harvestAlerts;
        this.wantMatches = builder.wantMatches;
        this.expiryNudges = builder.expiryNudges;
        this.creditWarnings = builder.creditWarnings;
        this.bookingReminders = builder.bookingReminders;
        this.quietHoursStart = builder.quietHoursStart;
        this.quietHoursEnd = builder.quietHoursEnd;
    }

    /** Everything on, no quiet hours — the product default. */
    public static NotificationPrefs defaultAllOn() {
        return new Builder().all(true).build();
    }

    public boolean isHarvestAlerts() {
        return harvestAlerts;
    }

    public boolean isWantMatches() {
        return wantMatches;
    }

    public boolean isExpiryNudges() {
        return expiryNudges;
    }

    public boolean isCreditWarnings() {
        return creditWarnings;
    }

    public boolean isBookingReminders() {
        return bookingReminders;
    }

    /** Quiet-hours start as "HH:mm"; null when unset. */
    public String getQuietHoursStart() {
        return quietHoursStart;
    }

    /** Quiet-hours end as "HH:mm"; null when unset. */
    public String getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean harvestAlerts = true;
        private boolean wantMatches = true;
        private boolean expiryNudges = true;
        private boolean creditWarnings = true;
        private boolean bookingReminders = true;
        private String quietHoursStart;
        private String quietHoursEnd;

        public Builder() {
        }

        public Builder all(boolean on) {
            this.harvestAlerts = on;
            this.wantMatches = on;
            this.expiryNudges = on;
            this.creditWarnings = on;
            this.bookingReminders = on;
            return this;
        }

        public Builder harvestAlerts(boolean harvestAlerts) {
            this.harvestAlerts = harvestAlerts;
            return this;
        }

        public Builder wantMatches(boolean wantMatches) {
            this.wantMatches = wantMatches;
            return this;
        }

        public Builder expiryNudges(boolean expiryNudges) {
            this.expiryNudges = expiryNudges;
            return this;
        }

        public Builder creditWarnings(boolean creditWarnings) {
            this.creditWarnings = creditWarnings;
            return this;
        }

        public Builder bookingReminders(boolean bookingReminders) {
            this.bookingReminders = bookingReminders;
            return this;
        }

        public Builder quietHours(String start, String end) {
            this.quietHoursStart = start;
            this.quietHoursEnd = end;
            return this;
        }

        public NotificationPrefs build() {
            return new NotificationPrefs(this);
        }
    }
}
