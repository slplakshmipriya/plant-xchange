package com.gardenswap.test.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Create-listing payload (API-020 {@code POST /v1/listings}).
 *
 * <p>Null = field omitted. Server requires {@code photos} (min 1),
 * {@code sprayDisclosure}, and {@code creditCost} 1..3.
 */
public final class ListingInput {

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
    private final String visitRules;
    /** See {@link Listing#isFree}. */
    private final boolean free;
    /** Pot size for seedling listings (e.g. "4 in"), nullable. */
    private final String potSize;
    /** Plant age for seedling listings (e.g. "6 weeks"), nullable. */
    private final String plantAge;
    /** Requested pickup window length in days; PRD §4 default is 4. */
    private final int pickupWindowDays;

    private ListingInput(Builder builder) {
        this.type = builder.type;
        this.photos = new ArrayList<>(builder.photos);
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
        this.visitRules = builder.visitRules;
        this.free = builder.free;
        this.potSize = builder.potSize;
        this.plantAge = builder.plantAge;
        this.pickupWindowDays = builder.pickupWindowDays;
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

    public String getVisitRules() {
        return visitRules;
    }

    public boolean isFree() {
        return free;
    }

    public String getPotSize() {
        return potSize;
    }

    public String getPlantAge() {
        return plantAge;
    }

    public int getPickupWindowDays() {
        return pickupWindowDays;
    }

    public static Builder builder(ListingType type) {
        return new Builder(type);
    }

    public static final class Builder {
        private final ListingType type;
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
        private String visitRules;
        private boolean free = false;
        private String potSize;
        private String plantAge;
        private int pickupWindowDays = 4;

        public Builder(ListingType type) {
            this.type = type;
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

        public Builder visitRules(String visitRules) {
            this.visitRules = visitRules;
            return this;
        }

        public Builder free(boolean free) {
            this.free = free;
            return this;
        }

        public Builder potSize(String potSize) {
            this.potSize = potSize;
            return this;
        }

        public Builder plantAge(String plantAge) {
            this.plantAge = plantAge;
            return this;
        }

        public Builder pickupWindowDays(int pickupWindowDays) {
            this.pickupWindowDays = pickupWindowDays;
            return this;
        }

        public ListingInput build() {
            return new ListingInput(this);
        }
    }
}
