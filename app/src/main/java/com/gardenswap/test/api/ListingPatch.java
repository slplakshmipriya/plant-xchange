package com.gardenswap.test.api;

/**
 * Partial listing update (API-020 {@code PATCH /v1/listings/{id}}).
 * Null fields are left unchanged; {@code status} drives the state machine
 * (illegal transitions are a 422 server-side).
 */
public final class ListingPatch {

    private final String variety;
    private final Double quantity;
    private final String unit;
    private final Integer creditCost;
    private final Long expiresAtMs;
    private final String sprayDisclosure;
    private final String visitRules;
    private final ListingStatus status;

    private ListingPatch(Builder builder) {
        this.variety = builder.variety;
        this.quantity = builder.quantity;
        this.unit = builder.unit;
        this.creditCost = builder.creditCost;
        this.expiresAtMs = builder.expiresAtMs;
        this.sprayDisclosure = builder.sprayDisclosure;
        this.visitRules = builder.visitRules;
        this.status = builder.status;
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

    public Integer getCreditCost() {
        return creditCost;
    }

    public Long getExpiresAtMs() {
        return expiresAtMs;
    }

    public String getSprayDisclosure() {
        return sprayDisclosure;
    }

    public String getVisitRules() {
        return visitRules;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String variety;
        private Double quantity;
        private String unit;
        private Integer creditCost;
        private Long expiresAtMs;
        private String sprayDisclosure;
        private String visitRules;
        private ListingStatus status;

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

        public Builder creditCost(Integer creditCost) {
            this.creditCost = creditCost;
            return this;
        }

        public Builder expiresAtMs(Long expiresAtMs) {
            this.expiresAtMs = expiresAtMs;
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

        public Builder status(ListingStatus status) {
            this.status = status;
            return this;
        }

        public ListingPatch build() {
            return new ListingPatch(this);
        }
    }
}
