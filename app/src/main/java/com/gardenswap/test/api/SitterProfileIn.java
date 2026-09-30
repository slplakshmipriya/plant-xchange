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

    public SitterProfileIn(String bio, int experienceYears,
                           double serviceRadiusMiles, boolean active) {
        this.bio = bio == null ? "" : bio;
        this.experienceYears = experienceYears;
        this.serviceRadiusMiles = serviceRadiusMiles;
        this.active = active;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("bio", bio);
        o.put("experience_years", experienceYears);
        o.put("service_radius_miles", serviceRadiusMiles);
        o.put("active", active);
        return o;
    }
}
