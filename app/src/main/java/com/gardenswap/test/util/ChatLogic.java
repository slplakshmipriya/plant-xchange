package com.gardenswap.test.util;

import com.gardenswap.test.api.ChatMessage;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Pure presentation logic for the messaging screens (UID-019).
 *
 * <p>No Android dependencies, so this runs as a plain JVM unit test.
 */
public final class ChatLogic {

    private ChatLogic() {
    }

    /**
     * Groups consecutive messages from the same sender so the UI can render
     * them as a clustered bubble run. System messages always stand alone:
     * each one forms its own group and breaks the surrounding run.
     */
    public static List<List<ChatMessage>> groupConsecutive(List<ChatMessage> messages) {
        List<List<ChatMessage>> groups = new ArrayList<>();
        if (messages == null) {
            return groups;
        }
        List<ChatMessage> current = null;
        for (ChatMessage message : messages) {
            boolean boundary = message.getKind() == ChatMessage.Kind.SYSTEM;
            if (current == null || boundary || !sameSender(current.get(current.size() - 1), message)) {
                current = new ArrayList<>();
                groups.add(current);
            }
            current.add(message);
            if (boundary) {
                current = null; // system message stands alone
            }
        }
        return groups;
    }

    private static boolean sameSender(ChatMessage a, ChatMessage b) {
        return a.isMine() == b.isMine()
                && a.getSenderName() != null
                && a.getSenderName().equals(b.getSenderName());
    }

    /**
     * Short relative timestamp for thread rows and bubble captions:
     * "now", "5m", "2h", "3d", or "M/d" for anything older than a week.
     */
    public static String shortTime(long nowMs, long atMs) {
        long diffMs = Math.max(0, nowMs - atMs);
        long minutes = diffMs / 60_000L;
        if (minutes < 1) {
            return "now";
        }
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h";
        }
        long days = hours / 24;
        if (days < 7) {
            return days + "d";
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(atMs);
        return (calendar.get(Calendar.MONTH) + 1) + "/" + calendar.get(Calendar.DAY_OF_MONTH);
    }
}
