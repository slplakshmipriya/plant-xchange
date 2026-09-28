package com.gardenswap.app.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A swap listing as returned by the backend (API-020).
 *
 * <p>Geo coordinates are the backend's <b>fuzzed</b> values (~0.5 mi jitter);
 * the true location never reaches the client (SEC-010).
 */
public final class Listing {

    private final String id;
    private final String ownerUid;
    private final ListingType type;
    private final List<String> photos;
    private final String variety;
    private final Double quantity;
    private final String unit;
    private final int creditCost;
    private final Long pickupStartMs;
    private final Long pickupEndMs;
    private final Long expiresAtMs;
    private final Double geoLat;
    private final Double geoLon;
    private final String sprayDisclosure;
    private final ListingStatus status;
    private final long createdAtMs;
    /**
     * Free listing: no credits change hands. Client-proposed (AND-040); the
     * backend wire semantics are decided with API-040.
     */
    private final boolean free;
    /** Kilos/units still available on a harvest listing; null when untracked. */
    private final Double remainingQty;
    /** Uid of the claimer once claimed; null otherwise. */
    private final String claimerUid;
    /** Tree visit rules (type=tree); null otherwise. */
    private final String visitRules;

    private Listing(Builder builder) {
        this.id = builder.id;
        this.ownerUid = builder.ownerUid;
        this.type = builder.type;
        this.photos = Collections.unmodifiableList(new ArrayList<>(builder.photos));
        this.variety = builder.variety;
        this.quantity = builder.quantity;
        this.unit = builder.unit;
        this.creditCost = builder.creditCost;
        this.pickupStartMs = builder.pickupStartMs;
        this.pickupEndMs = builder.pickupEndMs;
        this.expiresAtMs = builder.expiresAtMs;
        this.geoLat = builder.geoLat;
        this.geoLon = builder.geoLon;
        this.sprayDisclosure = builder.sprayDisclosure;
        this.status = builder.status;
        this.createdAtMs = builder.createdAtMs;
        this.free = builder.free;
        this.remainingQty = builder.remainingQty;
        this.claimerUid = builder.claimerUid;
        this.visitRules = builder.visitRules;
    }

    public String getId() {
        return id;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public ListingType getType() {
        return type;
    }

    public List<String> getPhotos() {
        return photos;
    }

    public String getVariety() {
        return variety;
    }

    public Double getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public int getCreditCost() {
        return creditCost;
    }

    public Long getPickupStartMs() {
        return pickupStartMs;
    }

    public Long getPickupEndMs() {
        return pickupEndMs;
    }

    public Long getExpiresAtMs() {
        return expiresAtMs;
    }

    public Double getGeoLat() {
        return geoLat;
    }

    public Double getGeoLon() {
        return geoLon;
    }

    public String getSprayDisclosure() {
        return sprayDisclosure;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public boolean isFree() {
        return free;
    }

    public Double getRemainingQty() {
        return remainingQty;
    }

    public String getClaimerUid() {
        return claimerUid;
    }

    public String getVisitRules() {
        return visitRules;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String ownerUid;
        private ListingType type = ListingType.SEEDLING;
        private List<String> photos = new ArrayList<>();
        private String variety;
        private Double quantity;
        private String unit;
        private int creditCost = 1;
        private Long pickupStartMs;
        private Long pickupEndMs;
        private Long expiresAtMs;
        private Double geoLat;
        private Double geoLon;
        private String sprayDisclosure;
        private ListingStatus status = ListingStatus.DRAFT;
        private long createdAtMs = System.currentTimeMillis();
        private boolean free = false;
        private Double remainingQty;
        private String claimerUid;
        private String visitRules;

        public Builder(String id) {
            this.id = id;
        }

        public Builder ownerUid(String ownerUid) {
            this.ownerUid = ownerUid;
            return this;
        }

        public Builder type(ListingType type) {
            this.type = type;
            return this;
        }

        public Builder photos(List<String> photos) {
            this.photos = photos == null ? new ArrayList<>() : new ArrayList<>(photos);
            return this;
        }

        public Builder variety(String variety) {
            this.variety = variety;
            return this;
        }

        public Builder quantity(Double quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder creditCost(int creditCost) {
            this.creditCost = creditCost;
            return this;
        }

        public Builder pickupWindow(Long startMs, Long endMs) {
            this.pickupStartMs = startMs;
            this.pickupEndMs = endMs;
            return this;
        }

        public Builder expiresAtMs(Long expiresAtMs) {
            this.expiresAtMs = expiresAtMs;
            return this;
        }

        public Builder geo(Double lat, Double lon) {
            this.geoLat = lat;
            this.geoLon = lon;
            return this;
        }

        public Builder sprayDisclosure(String sprayDisclosure) {
            this.sprayDisclosure = sprayDisclosure;
            return this;
        }

        public Builder status(ListingStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAtMs(long createdAtMs) {
            this.createdAtMs = createdAtMs;
            return this;
        }

        public Builder free(boolean free) {
            this.free = free;
            return this;
        }

        public Builder remainingQty(Double remainingQty) {
            this.remainingQty = remainingQty;
            return this;
        }

        public Builder claimerUid(String claimerUid) {
            this.claimerUid = claimerUid;
            return this;
        }

        public Builder visitRules(String visitRules) {
            this.visitRules = visitRules;
            return this;
        }

        public Listing build() {
            return new Listing(this);
        }
    }
}
