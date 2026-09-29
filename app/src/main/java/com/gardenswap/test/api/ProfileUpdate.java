package com.gardenswap.test.api;

/**
 * Fields to upsert on the profile (API-010). {@code null} = leave unchanged.
 */
public final class ProfileUpdate {

    private final String displayName;
    private final String avatarUrl;
    private final String homeZip;
    private final Boolean ageAttestation;

    public ProfileUpdate(String displayName, String avatarUrl, String homeZip, Boolean ageAttestation) {
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.homeZip = homeZip;
        this.ageAttestation = ageAttestation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getHomeZip() {
        return homeZip;
    }

    public Boolean getAgeAttestation() {
        return ageAttestation;
    }
}
