package com.gardenswap.test.util;

/**
 * Fixed service taxonomy for sitter profiles (backend {@code SITTER_SERVICES}).
 * Keys are snake_case on the wire; {@link #displayName(String)} gives the
 * human label shown on chips.
 */
public final class SitterServices {

    public static final String[] KEYS = {
            "watering",
            "repotting",
            "fertilizing",
            "pruning",
            "pest_control",
            "vacation_care",
    };

    private SitterServices() {
    }

    /** "pest_control" -> "Pest control". Unknown keys pass through. */
    public static String displayName(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
