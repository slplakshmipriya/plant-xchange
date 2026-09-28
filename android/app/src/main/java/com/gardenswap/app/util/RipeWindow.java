package com.gardenswap.app.util;

/**
 * Pure ripe-window logic for tree listings (AND-050). No Android
 * dependencies, so this runs as a plain JVM unit test.
 *
 * <p>A tree is "ripe" while {@code ripeStartMs <= now < ripeEndMs}. The
 * countdown UI shows time until the window opens (before) or closes (during).
 */
public final class RipeWindow {

    private RipeWindow() {
    }

    /** True when the tree is currently in its ripe window. */
    public static boolean isRipe(long ripeStartMs, long ripeEndMs, long nowMs) {
        return nowMs >= ripeStartMs && nowMs < ripeEndMs;
    }

    /** True when the window has not opened yet. */
    public static boolean isUpcoming(long ripeStartMs, long nowMs) {
        return nowMs < ripeStartMs;
    }

    /** True when the window has closed. */
    public static boolean isPast(long ripeEndMs, long nowMs) {
        return nowMs >= ripeEndMs;
    }

    /**
     * Human countdown: "ripe in 3d 4h", "ripe for 2d more", or
     * "window ended".
     */
    public static String countdownText(long ripeStartMs, long ripeEndMs, long nowMs) {
        if (isRipe(ripeStartMs, ripeEndMs, nowMs)) {
            return "Ripe now — " + formatDuration(ripeEndMs - nowMs) + " left";
        }
        if (isUpcoming(ripeStartMs, nowMs)) {
            return "Ripe in " + formatDuration(ripeStartMs - nowMs);
        }
        return "Ripe window ended";
    }

    /** "3d 4h", "45m", "20s" — largest two non-zero units. */
    static String formatDuration(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long totalSeconds = ms / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append('d');
            if (hours > 0) {
                sb.append(' ').append(hours).append('h');
            }
        } else if (hours > 0) {
            sb.append(hours).append('h');
            if (minutes > 0) {
                sb.append(' ').append(minutes).append('m');
            }
        } else if (minutes > 0) {
            sb.append(minutes).append('m');
        } else {
            sb.append(seconds).append('s');
        }
        return sb.toString();
    }
}
