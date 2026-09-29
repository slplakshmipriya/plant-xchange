package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gardenswap.test.api.ChatMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ChatLogicTest {

    private static ChatMessage text(String id, String sender, boolean mine, long atMs) {
        return new ChatMessage(id, "t1", sender, mine, ChatMessage.Kind.TEXT, "hi", atMs);
    }

    private static ChatMessage system(String id, long atMs) {
        return new ChatMessage(id, "t1", "System", false,
                ChatMessage.Kind.SYSTEM, "Claim confirmed.", atMs);
    }

    @Test
    public void emptyAndNullGiveNoGroups() {
        assertTrue(ChatLogic.groupConsecutive(new ArrayList<ChatMessage>()).isEmpty());
        assertTrue(ChatLogic.groupConsecutive(null).isEmpty());
    }

    @Test
    public void singleMessageIsOneGroup() {
        List<List<ChatMessage>> groups =
                ChatLogic.groupConsecutive(singleton(text("m1", "Priya", false, 1L)));
        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).size());
    }

    @Test
    public void consecutiveSameSenderClusters() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(text("m1", "Priya", false, 1L));
        messages.add(text("m2", "Priya", false, 2L));
        messages.add(text("m3", "Priya", false, 3L));
        List<List<ChatMessage>> groups = ChatLogic.groupConsecutive(messages);
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).size());
    }

    @Test
    public void alternatingSendersSplit() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(text("m1", "Priya", false, 1L));
        messages.add(text("m2", "You", true, 2L));
        messages.add(text("m3", "Priya", false, 3L));
        List<List<ChatMessage>> groups = ChatLogic.groupConsecutive(messages);
        assertEquals(3, groups.size());
    }

    @Test
    public void systemMessageStandsAloneAndBreaksRuns() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(text("m1", "Priya", false, 1L));
        messages.add(system("s1", 2L));
        messages.add(text("m2", "Priya", false, 3L));
        messages.add(text("m3", "Priya", false, 4L));
        List<List<ChatMessage>> groups = ChatLogic.groupConsecutive(messages);
        assertEquals(3, groups.size());
        assertEquals(1, groups.get(0).size());
        assertEquals(ChatMessage.Kind.SYSTEM, groups.get(1).get(0).getKind());
        assertEquals(2, groups.get(2).size());
    }

    @Test
    public void sameMineFlagDifferentNameDoesNotCluster() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(text("m1", "Priya", false, 1L));
        messages.add(text("m2", "Dev", false, 2L));
        assertEquals(2, ChatLogic.groupConsecutive(messages).size());
    }

    @Test
    public void shortTimeNow() {
        long now = 1_700_000_000_000L;
        assertEquals("now", ChatLogic.shortTime(now, now - 30_000L));
        assertEquals("now", ChatLogic.shortTime(now, now + 60_000L)); // future clamps
    }

    @Test
    public void shortTimeMinutesAndHours() {
        long now = 1_700_000_000_000L;
        assertEquals("5m", ChatLogic.shortTime(now, now - 5 * 60_000L));
        assertEquals("59m", ChatLogic.shortTime(now, now - 59 * 60_000L));
        assertEquals("2h", ChatLogic.shortTime(now, now - 2 * 3_600_000L));
        assertEquals("23h", ChatLogic.shortTime(now, now - 23 * 3_600_000L));
    }

    @Test
    public void shortTimeDays() {
        long now = 1_700_000_000_000L;
        assertEquals("3d", ChatLogic.shortTime(now, now - 3 * 86_400_000L));
        assertEquals("6d", ChatLogic.shortTime(now, now - 6 * 86_400_000L));
    }

    @Test
    public void shortTimeOlderThanAWeekIsMonthDay() {
        long now = 1_700_000_000_000L;
        long at = now - 10 * 86_400_000L;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(at);
        String expected = (calendar.get(Calendar.MONTH) + 1) + "/"
                + calendar.get(Calendar.DAY_OF_MONTH);
        assertEquals(expected, ChatLogic.shortTime(now, at));
    }

    private static List<ChatMessage> singleton(ChatMessage message) {
        List<ChatMessage> list = new ArrayList<>();
        list.add(message);
        return list;
    }
}
