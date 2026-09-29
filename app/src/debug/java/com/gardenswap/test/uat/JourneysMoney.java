package com.gardenswap.test.uat;

import com.gardenswap.test.api.Booking;
import com.gardenswap.test.api.CreditExpiry;
import com.gardenswap.test.api.NotificationPrefs;
import com.gardenswap.test.api.SitterProfile;
import com.gardenswap.test.api.TreeListing;
import com.gardenswap.test.api.Wallet;

import java.util.Arrays;
import java.util.List;

/** Wallet/credits, sitting, trees/PYO, harvest, and trust journeys. */
final class JourneysMoney {

    static List<UatJourney> all() {
        return Arrays.asList(
                walletLoads(),
                creditExpiryLoads(),
                sitterBrowse(),
                bookingsList(),
                treesLoad(),
                treeSlotsLoad(),
                harvestEventsLoad(),
                idvStatusLoads(),
                notificationPrefsRoundTrip()
        );
    }

    private static UatJourney walletLoads() {
        return UatJourney.builder("wallet-loads",
                        "Wallet balance and ledger load")
                .persona("Maya the giver")
                .step("Load the wallet", ctx -> {
                    Wallet wallet = ctx.call("load wallet",
                            cb -> ctx.api().getWallet(cb));
                    ctx.assertNotNull("wallet", wallet);
                    ctx.note("balance: " + wallet.getBalance());
                })
                .build();
    }

    private static UatJourney creditExpiryLoads() {
        return UatJourney.builder("credit-expiry",
                        "Credit expiry warnings load (seasonal expiry)")
                .persona("Maya the giver")
                .step("Load credit expiry", ctx -> {
                    CreditExpiry expiry = ctx.call("load credit expiry",
                            cb -> ctx.api().getCreditExpiry(cb));
                    ctx.assertNotNull("credit expiry", expiry);
                })
                .build();
    }

    private static UatJourney sitterBrowse() {
        return UatJourney.builder("sitter-browse",
                        "Sitter list and detail load (Pillar 2)")
                .persona("Rosa the plant owner")
                .step("List sitters near a ZIP", ctx -> {
                    List<SitterProfile> sitters = ctx.call("list sitters",
                            cb -> ctx.api().getSitters("85281", cb));
                    ctx.assertNotNull("sitters", sitters);
                    ctx.note(sitters.size() + " sitters near 85281");
                    if (!sitters.isEmpty()) {
                        ctx.put("sitterId", sitters.get(0).getSitterId());
                    }
                })
                .step("Open the first sitter's detail (if any)", ctx -> {
                    String sitterId = ctx.get("sitterId");
                    if (sitterId == null) {
                        ctx.note("no sitters listed — detail untested");
                        return;
                    }
                    SitterProfile detail = ctx.call("sitter detail",
                            cb -> ctx.api().getSitter(sitterId, cb));
                    ctx.assertNotNull("sitter detail", detail.getSitterId());
                })
                .build();
    }

    private static UatJourney bookingsList() {
        return UatJourney.builder("bookings-list",
                        "Sitting bookings list loads")
                .persona("Rosa the plant owner")
                .step("List my bookings", ctx -> {
                    List<Booking> bookings = ctx.call("list bookings",
                            cb -> ctx.api().listBookings("owner", false, cb));
                    ctx.assertNotNull("bookings", bookings);
                    ctx.note(bookings.size() + " bookings");
                })
                .build();
    }

    private static UatJourney treesLoad() {
        return UatJourney.builder("trees-load",
                        "Tree list loads (Pillar 3 PYO)")
                .persona("Dev the picker")
                .step("List trees", ctx -> {
                    List<TreeListing> trees = ctx.call("list trees",
                            cb -> ctx.api().listTrees(cb));
                    ctx.assertNotNull("trees", trees);
                    ctx.note(trees.size() + " trees");
                    if (!trees.isEmpty()) {
                        ctx.put("treeId", trees.get(0).getTreeId());
                    }
                })
                .step("Open the first tree's detail (if any)", ctx -> {
                    String treeId = ctx.get("treeId");
                    if (treeId == null) {
                        ctx.note("no trees listed — detail untested");
                        return;
                    }
                    TreeListing detail = ctx.call("tree detail",
                            cb -> ctx.api().getTreeDetail(treeId, cb));
                    ctx.assertNotNull("tree detail", detail.getTreeId());
                })
                .build();
    }

    private static UatJourney treeSlotsLoad() {
        return UatJourney.builder("tree-slots",
                        "PYO slots list for a tree")
                .persona("Dev the picker")
                .step("List slots for the first tree (if any)", ctx -> {
                    List<TreeListing> trees = ctx.call("list trees",
                            cb -> ctx.api().listTrees(cb));
                    if (trees.isEmpty()) {
                        ctx.note("no trees listed — slots untested");
                        return;
                    }
                    List<com.gardenswap.test.api.Slot> slots =
                            ctx.call("list slots",
                                    cb -> ctx.api().listTreeSlots(
                                            trees.get(0).getTreeId(), cb));
                    ctx.assertNotNull("slots", slots);
                    ctx.note(slots.size() + " slots");
                })
                .build();
    }

    private static UatJourney harvestEventsLoad() {
        return UatJourney.builder("harvest-events",
                        "Harvest log loads for a listing (Pillar 4)")
                .persona("Maya the giver")
                .step("Load harvest events for my newest listing (if any)", ctx -> {
                    List<com.gardenswap.test.api.Listing> mine = ctx.call("my listings",
                            cb -> ctx.api().listMyListings(cb));
                    if (mine.isEmpty()) {
                        ctx.note("no listings — harvest log untested");
                        return;
                    }
                    List<com.gardenswap.test.api.HarvestEvent> events =
                            ctx.call("harvest events",
                                    cb -> ctx.api().getHarvestEvents(
                                            mine.get(0).getId(), cb));
                    ctx.assertNotNull("harvest events", events);
                })
                .build();
    }

    private static UatJourney idvStatusLoads() {
        return UatJourney.builder("idv-status",
                        "ID verification status loads")
                .persona("Rosa the sitter")
                .step("Load IDV status", ctx -> {
                    com.gardenswap.test.api.IdvStatus status = ctx.call("IDV status",
                            cb -> ctx.api().getIdvStatus(cb));
                    ctx.assertNotNull("IDV status", status);
                    ctx.note("verification: " + status);
                })
                .build();
    }

    private static UatJourney notificationPrefsRoundTrip() {
        return UatJourney.builder("notif-prefs",
                        "Notification prefs round-trip (5 categories + quiet hours)")
                .persona("Sam the planner")
                .step("Load prefs", ctx -> {
                    NotificationPrefs prefs = ctx.call("load prefs",
                            cb -> ctx.api().getNotificationPrefs(cb));
                    ctx.assertNotNull("prefs", prefs);
                    ctx.put("prefs", prefs);
                })
                .step("Save them back unchanged", ctx -> {
                    NotificationPrefs prefs = ctx.get("prefs");
                    NotificationPrefs saved = ctx.call("save prefs",
                            cb -> ctx.api().updateNotificationPrefs(prefs, cb));
                    ctx.assertNotNull("saved prefs", saved);
                })
                .build();
    }
}
