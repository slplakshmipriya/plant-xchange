package com.gardenswap.app.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side location-leak guard for chat (AND-080, SEC-010).
 *
 * <p>Exact addresses stay hidden until an exchange is confirmed — but a user
 * can still paste raw GPS coordinates into chat. This detector flags
 * decimal lat/lon pairs so the UI can warn before sending. No Android
 * dependencies, so this runs as a plain JVM unit test.
 *
 * <p>Heuristic, not a guarantee: the server-side abuse filter (API-080) is
 * the real enforcement. A candidate pair counts as coordinates when both
 * numbers have a decimal point and fall in the lat [-90, 90] / lon
 * [-180, 180] ranges.
 */
public final class CoordinateGuard {

    /**
     * Matches things like "33.4152, -111.8315", "(33.4152,-111.8315)",
     * "33.4152 -111.8315". Group 1 = lat, group 2 = lon.
     */
    private static final Pattern PAIR = Pattern.compile(
            "[\\(\\[]?\\s*([+-]?\\d{1,3}\\.\\d+)\\s*[,\\s]\\s*([+-]?\\d{1,3}\\.\\d+)\\s*[\\)\\]]?");

    private CoordinateGuard() {
    }

    /**
     * Returns the matched coordinate text when {@code text} looks like it
     * contains a lat/lon pair, or null otherwise.
     */
    public static String findCoordinates(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = PAIR.matcher(text);
        while (m.find()) {
            try {
                double lat = Double.parseDouble(m.group(1));
                double lon = Double.parseDouble(m.group(2));
                if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
                    return m.group(0).trim();
                }
            } catch (NumberFormatException e) {
                // Regex guarantees parseable doubles; ignore defensively.
            }
        }
        return null;
    }

    /** True when {@link #findCoordinates(String)} finds a pair. */
    public static boolean looksLikeCoordinates(String text) {
        return findCoordinates(text) != null;
    }

    /** Warning copy shown in the send-confirmation dialog. */
    public static String warningText(String matched) {
        return "This message looks like it contains GPS coordinates (" + matched + "). "
                + "Your exact location stays private until an exchange is confirmed — "
                + "send anyway?";
    }
}
