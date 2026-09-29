package com.gardenswap.test.api;

/** User profile (API-010). Immutable; build via {@link Builder}. */
public final class UserProfile {

    private final String userId;
    private final String displayName;
    private final String avatarUrl;
    private final String homeZip;
    private final boolean phoneVerified;
    private final IdvStatus idvStatus;

    private UserProfile(Builder builder) {
        this.userId = builder.userId;
        this.displayName = builder.displayName;
        this.avatarUrl = builder.avatarUrl;
        this.homeZip = builder.homeZip;
        this.phoneVerified = builder.phoneVerified;
        this.idvStatus = builder.idvStatus;
    }

    public String getUserId() {
        return userId;
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

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public IdvStatus getIdvStatus() {
        return idvStatus;
    }

    public static Builder builder(String userId) {
        return new Builder(userId);
    }

    public Builder toBuilder() {
        return new Builder(userId)
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .homeZip(homeZip)
                .phoneVerified(phoneVerified)
                .idvStatus(idvStatus);
    }

    public static final class Builder {
        private final String userId;
        private String displayName;
        private String avatarUrl;
        private String homeZip;
        private boolean phoneVerified;
        private IdvStatus idvStatus = IdvStatus.UNVERIFIED;

        private Builder(String userId) {
            if (userId == null) {
                throw new IllegalArgumentException("userId must not be null");
            }
            this.userId = userId;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return this;
        }

        public Builder homeZip(String homeZip) {
            this.homeZip = homeZip;
            return this;
        }

        public Builder phoneVerified(boolean phoneVerified) {
            this.phoneVerified = phoneVerified;
            return this;
        }

        public Builder idvStatus(IdvStatus idvStatus) {
            this.idvStatus = idvStatus == null ? IdvStatus.UNVERIFIED : idvStatus;
            return this;
        }

        public UserProfile build() {
            return new UserProfile(this);
        }
    }
}
