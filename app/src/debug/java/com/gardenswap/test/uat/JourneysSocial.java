package com.gardenswap.test.uat;

import com.gardenswap.test.api.ChatMessage;
import com.gardenswap.test.api.ChatThread;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.WantItem;
import com.gardenswap.test.api.FeedRequest;

import java.util.Arrays;
import java.util.List;

/** Want-list, chat, and profile journeys. */
final class JourneysSocial {

    static List<UatJourney> all() {
        return Arrays.asList(
                wantListRoundTrip(),
                wantMatchesLoad(),
                chatSendText(),
                chatSendPhoto(),
                profileRoundTrip(),
                avatarUpload()
        );
    }

    private static UatJourney wantListRoundTrip() {
        return UatJourney.builder("want-list-roundtrip",
                        "Add → list → remove a want-list item")
                .persona("Sam the planner")
                .step("Add a want", ctx -> {
                    WantItem item = ctx.call("add want",
                            cb -> ctx.api().addWant("UAT Heirloom Tomato " + System.currentTimeMillis(), cb));
                    ctx.assertNotNull("want id", item.getId());
                    ctx.put("wantId", item.getId());
                })
                .step("It appears in the want list", ctx -> {
                    String id = ctx.get("wantId");
                    List<WantItem> wants = ctx.call("load want list",
                            cb -> ctx.api().getWantList(cb));
                    boolean found = false;
                    for (WantItem w : wants) {
                        if (id.equals(w.getId())) {
                            found = true;
                            break;
                        }
                    }
                    ctx.assertTrue("want in list", found);
                })
                .step("Remove it", ctx -> {
                    String id = ctx.get("wantId");
                    ctx.call("remove want",
                            cb -> ctx.api().removeWant(id, cb));
                })
                .build();
    }

    private static UatJourney wantMatchesLoad() {
        return UatJourney.builder("want-matches",
                        "Want-list matches load (primary discovery)")
                .persona("Sam the planner")
                .step("Load matches", ctx -> {
                    List<Listing> matches = ctx.call("load matches",
                            cb -> ctx.api().getMatches(cb));
                    ctx.assertNotNull("matches list", matches);
                    ctx.note("matches returned " + matches.size() + " listings");
                })
                .build();
    }

    private static UatJourney chatSendText() {
        return UatJourney.builder("chat-send-text",
                        "Chat threads load and a text message sends")
                .persona("Sam the claimer")
                .step("Load chat threads", ctx -> {
                    List<ChatThread> threads = ctx.call("load threads",
                            cb -> ctx.api().getThreads(cb));
                    ctx.assertNotNull("threads", threads);
                    ctx.note(threads.size() + " threads");
                    if (!threads.isEmpty()) {
                        ctx.put("threadId", threads.get(0).getThreadId());
                    }
                })
                .step("Send a text message (if a thread exists)", ctx -> {
                    String threadId = ctx.get("threadId");
                    if (threadId == null) {
                        ctx.note("no threads yet — text send untested (needs a claim first)");
                        return;
                    }
                    ChatMessage msg = ctx.call("send message",
                            cb -> ctx.api().sendMessage(threadId,
                                    "UAT ping " + System.currentTimeMillis(), cb));
                    ctx.assertNotNull("sent message id", msg.getMessageId());
                })
                .build();
    }

    private static UatJourney chatSendPhoto() {
        return UatJourney.builder("chat-send-photo",
                        "Chat photo attachment uploads and sends")
                .persona("Rosa the sitter")
                .step("Upload the photo bytes", ctx -> {
                    String key = ctx.call("upload attachment",
                            cb -> ctx.api().uploadFileKey(
                                    JourneysListings.tinyJpeg(), "image/jpeg", cb));
                    ctx.assertNotNull("upload key", key);
                    ctx.put("uploadKey", key);
                })
                .step("Send as attachment (if a thread exists)", ctx -> {
                    String threadId = ctx.get("threadId");
                    if (threadId == null) {
                        List<ChatThread> threads = ctx.call("load threads",
                                cb -> ctx.api().getThreads(cb));
                        if (!threads.isEmpty()) {
                            threadId = threads.get(0).getThreadId();
                        }
                    }
                    if (threadId == null) {
                        ctx.note("no threads yet — attachment send untested (needs a claim first)");
                        return;
                    }
                    String key = ctx.get("uploadKey");
                    final String tid = threadId;
                    ChatMessage msg = ctx.call("send attachment",
                            cb -> ctx.api().sendAttachment(tid, key, cb));
                    ctx.assertNotNull("attachment message id", msg.getMessageId());
                })
                .build();
    }

    private static UatJourney profileRoundTrip() {
        return UatJourney.builder("profile-roundtrip",
                        "View and update the user profile")
                .persona("Maya the giver")
                .step("Load my profile", ctx -> {
                    com.gardenswap.test.api.UserProfile me = ctx.call("get me",
                            cb -> ctx.api().getMe(cb));
                    ctx.assertNotNull("profile", me);
                })
                .step("Load the feed as a sanity check", ctx -> {
                    List<Listing> feed = ctx.call("load feed",
                            cb -> ctx.api().getFeed(new FeedRequest(null, 50), cb));
                    ctx.assertNotNull("feed", feed);
                })
                .build();
    }

    private static UatJourney avatarUpload() {
        return UatJourney.builder("avatar-upload",
                        "Avatar upload returns an absolute URL (signup flow)")
                .persona("New user onboarding")
                .step("Upload an avatar", ctx -> {
                    String url = ctx.call("avatar upload",
                            cb -> ctx.api().uploadAvatar(
                                    JourneysListings.tinyJpeg(), "image/jpeg", cb));
                    ctx.assertNotNull("avatar URL", url);
                    ctx.assertTrue("avatar URL is absolute https",
                            url.startsWith("https://"));
                })
                .build();
    }
}
