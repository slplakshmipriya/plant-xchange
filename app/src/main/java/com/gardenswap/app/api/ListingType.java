package com.gardenswap.app.api;

/**
 * Listing kind. Matches the backend {@code ListingIn.type} pattern
 * {@code ^(seedling|harvest|tree)$} (API-020).
 */
public enum ListingType {
    SEEDLING("seedling"),
    HARVEST("harvest"),
    TREE("tree");

    private final String wireValue;

    ListingType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    /** Unknown wire values map to null so callers can handle them explicitly. */
    public static ListingType fromString(String value) {
        if (value == null) {
            return null;
        }
        for (ListingType type : values()) {
            if (type.wireValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
