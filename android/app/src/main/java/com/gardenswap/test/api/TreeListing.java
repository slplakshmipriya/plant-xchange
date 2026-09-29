package com.gardenswap.test.api;

/**
 * A pick-your-own tree listing (API-050). Immutable; build via
 * {@link Builder}.
 *
 * <p>SEC-010: the owner's exact address is never exposed — contact and the
 * pickup location unlock only after an exchange/booking is confirmed.
 */
public final class TreeListing {

    private final String treeId;
    private final String variety;
    private final long ripeStartMs;
    private final long ripeEndMs;
    private final String perPickerLimit;
    private final String pickupRules;
    private final String sprayDisclosure;
    private final String ownerDisplayName;
    private final boolean addressUnlocked;
    private final boolean ripeAlertsSubscribed;

    private TreeListing(Builder builder) {
        this.treeId = builder.treeId;
        this.variety = builder.variety;
        this.ripeStartMs = builder.ripeStartMs;
        this.ripeEndMs = builder.ripeEndMs;
        this.perPickerLimit = builder.perPickerLimit;
        this.pickupRules = builder.pickupRules;
        this.sprayDisclosure = builder.sprayDisclosure;
        this.ownerDisplayName = builder.ownerDisplayName;
        this.addressUnlocked = builder.addressUnlocked;
        this.ripeAlertsSubscribed = builder.ripeAlertsSubscribed;
    }

    public String getTreeId() {
        return treeId;
    }

    public String getVariety() {
        return variety;
    }

    public long getRipeStartMs() {
        return ripeStartMs;
    }

    public long getRipeEndMs() {
        return ripeEndMs;
    }

    public String getPerPickerLimit() {
        return perPickerLimit;
    }

    public String getPickupRules() {
        return pickupRules;
    }

    public String getSprayDisclosure() {
        return sprayDisclosure;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    /** True only after a confirmed exchange — otherwise the address stays hidden. */
    public boolean isAddressUnlocked() {
        return addressUnlocked;
    }

    public boolean isRipeAlertsSubscribed() {
        return ripeAlertsSubscribed;
    }

    public Builder toBuilder() {
        return new Builder(treeId)
                .variety(variety)
                .ripeWindow(ripeStartMs, ripeEndMs)
                .perPickerLimit(perPickerLimit)
                .pickupRules(pickupRules)
                .sprayDisclosure(sprayDisclosure)
                .ownerDisplayName(ownerDisplayName)
                .addressUnlocked(addressUnlocked)
                .ripeAlertsSubscribed(ripeAlertsSubscribed);
    }

    public static Builder builder(String treeId) {
        return new Builder(treeId);
    }

    public static final class Builder {
        private final String treeId;
        private String variety;
        private long ripeStartMs;
        private long ripeEndMs;
        private String perPickerLimit;
        private String pickupRules;
        private String sprayDisclosure;
        private String ownerDisplayName;
        private boolean addressUnlocked;
        private boolean ripeAlertsSubscribed;

        private Builder(String treeId) {
            if (treeId == null) {
                throw new IllegalArgumentException("treeId must not be null");
            }
            this.treeId = treeId;
        }

        public Builder variety(String variety) {
            this.variety = variety;
            return this;
        }

        public Builder ripeWindow(long startMs, long endMs) {
            this.ripeStartMs = startMs;
            this.ripeEndMs = endMs;
            return this;
        }

        public Builder perPickerLimit(String perPickerLimit) {
            this.perPickerLimit = perPickerLimit;
            return this;
        }

        public Builder pickupRules(String pickupRules) {
            this.pickupRules = pickupRules;
            return this;
        }

        public Builder sprayDisclosure(String sprayDisclosure) {
            this.sprayDisclosure = sprayDisclosure;
            return this;
        }

        public Builder ownerDisplayName(String ownerDisplayName) {
            this.ownerDisplayName = ownerDisplayName;
            return this;
        }

        public Builder addressUnlocked(boolean addressUnlocked) {
            this.addressUnlocked = addressUnlocked;
            return this;
        }

        public Builder ripeAlertsSubscribed(boolean subscribed) {
            this.ripeAlertsSubscribed = subscribed;
            return this;
        }

        public TreeListing build() {
            return new TreeListing(this);
        }
    }
}
