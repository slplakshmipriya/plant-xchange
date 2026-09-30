package com.gardenswap.test.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Booking request payload (API-070).
 *
 * <p>The booker picks individual dates from the sitter's available days —
 * there is no start/end range. The wire body is:
 * {@code {"sitter_uid", "plant_count", "dates": ["YYYY-MM-DD", ...],
 * "services": ["watering", ...], "notes"}}.
 */
public final class BookingRequest {

    private final String sitterId;
    /** Sorted ISO dates (yyyy-MM-dd) the booker wants sitting. */
    private final List<String> dates;
    private final String[] services;
    private final String careInstructions;
    /**
     * Number of plants needing care. The booking UI does not collect this
     * yet; defaults to 1 until it does (the backend requires &gt; 0).
     */
    private final int plantCount;

    public BookingRequest(String sitterId, List<String> dates,
                          String[] services, String careInstructions) {
        this(sitterId, dates, services, careInstructions, 1);
    }

    public BookingRequest(String sitterId, List<String> dates,
                          String[] services, String careInstructions, int plantCount) {
        this.sitterId = sitterId;
        List<String> copy = new ArrayList<>();
        if (dates != null) {
            for (String d : dates) {
                if (d != null && !d.trim().isEmpty()) {
                    copy.add(d.trim());
                }
            }
        }
        Collections.sort(copy);
        this.dates = Collections.unmodifiableList(copy);
        this.services = services == null ? new String[0] : services.clone();
        this.careInstructions = careInstructions;
        this.plantCount = Math.max(1, plantCount);
    }

    public String getSitterId() {
        return sitterId;
    }

    /** Unmodifiable sorted list of ISO dates (yyyy-MM-dd). */
    public List<String> getDates() {
        return dates;
    }

    public String[] getServices() {
        return services.clone();
    }

    public String getCareInstructions() {
        return careInstructions;
    }

    public int getPlantCount() {
        return plantCount;
    }
}
