package com.gardenswap.test.api;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Request body for {@code PUT /v1/sitters/me} (backend
 * {@code SitterProfileIn}): registers the signed-in user as a plant sitter,
 * or updates their existing sitter profile (upsert).
 */
public final class SitterProfileIn {

    private final String bio;
    private final int experienceYears;
    private final double serviceRadiusMiles;
    private final boolean active;
    /** Daily rate amount, or null for "rate on request". */
    private final Double rateAmount;
    /** "credits" or "usd" when a rate is set, null otherwise. */
    private final String rateUnit;
    /** Services offered, from the backend taxonomy (snake_case keys). */
    private final String[] services;

    public SitterProfileIn(String bio, int experienceYears,
                           double serviceRadiusMiles, boolean active,
                           Double rateAmount, String rateUnit, String[] services) {
        this.bio = bio == null ? "" : bio;
        this.experienceYears = experienceYears;
        this.serviceRadiusMiles = serviceRadiusMiles;
        this.active = active;
        this.rateAmount = rateAmount;
        this.rateUnit = rateUnit;
        this.services = services == null ? new String[0] : services.clone();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("bio", bio);
        o.put("experience_years", experienceYears);
        o.put("service_radius_miles", serviceRadiusMiles);
        o.put("active", active);
        if (rateAmount != null && rateUnit != null) {
            o.put("rate_amount", rateAmount);
            o.put("rate_unit", rateUnit);
        }
        org.json.JSONArray arr = new org.json.JSONArray();
        for (String s : services) {
            arr.put(s);
        }
        o.put("services", arr);
        return o;
    }
}
