package com.gardenswap.app.api;

/**
 * Fields to upsert on the profile (API-010). {@code null} = leave unchanged.
 */
public final class ProfileUpdate {

    private final String displayName;
    private final String avatarUrl;
    private final String homeZip;

    public ProfileUpdate(String displayName, String avatarUrl, String homeZip) {
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.homeZip = homeZip;
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
}
