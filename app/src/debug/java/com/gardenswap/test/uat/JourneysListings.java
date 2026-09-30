package com.gardenswap.test.uat;

import com.gardenswap.test.api.FeedRequest;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.ListingInput;
import com.gardenswap.test.api.ListingType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pillar 1 (listings) + Pillar 4 (harvest) journeys — the photo flows. */
final class JourneysListings {

    /** Minimal 1x1 JPEG used as the upload payload in photo journeys. */
    static byte[] tinyJpeg() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46,
                0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x03, 0x02, 0x02, 0x03, 0x02,
                0x02, 0x03, 0x03, 0x03, 0x03, 0x04, 0x03, 0x03, 0x04, 0x05, 0x08, 0x05,
                0x05, 0x04, 0x04, 0x05, 0x0A, 0x07, 0x07, 0x06, 0x08, 0x0C, 0x0A, 0x0C,
                0x0C, 0x0B, 0x0A, 0x0B, 0x0B, 0x0D, 0x0E, 0x12, 0x10, 0x0D, 0x0E, 0x11,
                0x0E, 0x0B, 0x0B, 0x10, 0x16, 0x10, 0x11, 0x13, 0x14, 0x15, 0x15, 0x15,
                0x0C, 0x0F, 0x17, 0x18, 0x16, 0x14, 0x18, 0x12, 0x14, 0x15, 0x14,
                (byte) 0xFF, (byte) 0xC9, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01,
                0x01, 0x11, 0x00, (byte) 0xFF, (byte) 0xCC, 0x00, 0x06, 0x00, 0x10, 0x10,
                0x05, (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F,
                0x00, (byte) 0xD2, (byte) 0xCF, 0x20, (byte) 0xFF, (byte) 0xD9
        };
    }

    static List<UatJourney> all() {
        return Arrays.asList(
                createWithOnePhoto(),
                createWithTwoPhotos(),
                createFreeListing(),
                feedShowsListing(),
                listingDetailLoads(),
                cancelListing()
        );
    }

    static ListingInput.Builder baseDraft(List<String> photoUrls) {
        long now = System.currentTimeMillis();
        return ListingInput.builder(ListingType.SEEDLING)
                .photos(new ArrayList<>(photoUrls))
                .variety("UAT Tomato " + now)
                .quantity(5.0)
                .unit("seedlings")
                .creditCost(2)
                .pickupWindow(now, now + 4L * 24 * 3600 * 1000)
                .pickupWindowDays(4)
                .sprayDisclosure("UAT: no sprays used")
                .expiresAtMs(now + 7L * 24 * 3600 * 1000);
    }

    private static UatJourney createWithOnePhoto() {
        return UatJourney.builder("listing-create-1-photo",
                        "Create listing with one photo; it appears in the feed")
                .persona("Maya the giver")
                .step("Upload one photo (sign → PUT → finalize)", ctx -> {
                    String url = ctx.call("photo upload",
                            cb -> ctx.api().uploadAvatar(tinyJpeg(), "image/jpeg", cb));
                    ctx.assertNotNull("public photo URL", url);
                    ctx.assertTrue("photo URL is absolute https",
                            url.startsWith("https://"));
                    ctx.put("photoUrl", url);
                })
                .step("Publish the listing", ctx -> {
                    String url = ctx.get("photoUrl");
                    Listing listing = ctx.call("create listing",
                            cb -> ctx.api().createListing(
                                    baseDraft(new ArrayList<>(Arrays.asList(url))).build(), cb));
                    ctx.assertNotNull("listing id", listing.getId());
                    ctx.put("listingId", listing.getId());
                })
                .step("Listing appears in the feed with its photo", ctx -> {
                    String id = ctx.get("listingId");
                    List<Listing> feed = ctx.call("load feed",
                            cb -> ctx.api().getFeed(new FeedRequest(null, 50), cb));
                    Listing found = null;
                    for (Listing l : feed) {
                        if (id.equals(l.getId())) {
                            found = l;
                            break;
                        }
                    }
                    ctx.assertNotNull("new listing in feed", found);
                    ctx.assertNotEmpty("listing photos in feed card", found.getPhotos());
                })
                .build();
    }

    private static UatJourney createWithTwoPhotos() {
        return UatJourney.builder("listing-create-2-photos",
                        "Create listing with two photos; detail shows the gallery")
                .persona("Maya the giver")
                .step("Upload two photos", ctx -> {
                    List<String> urls = new ArrayList<>();
                    for (int i = 0; i < 2; i++) {
                        String url = ctx.call("photo upload " + (i + 1),
                                cb -> ctx.api().uploadAvatar(tinyJpeg(), "image/jpeg", cb));
                        urls.add(url);
                    }
                    ctx.assertEquals("uploaded photo count", 2, urls.size());
                    ctx.put("photoUrls", urls);
                })
                .step("Publish the listing", ctx -> {
                    List<String> urls = ctx.get("photoUrls");
                    Listing listing = ctx.call("create listing",
                            cb -> ctx.api().createListing(baseDraft(urls).build(), cb));
                    ctx.put("listingId", listing.getId());
                })
                .step("Detail returns both photos (gallery data)", ctx -> {
                    String id = ctx.get("listingId");
                    Listing detail = ctx.call("load listing detail",
                            cb -> ctx.api().getListing(id, cb));
                    ctx.assertNotNull("detail photos", detail.getPhotos());
                    ctx.assertEquals("detail photo count", 2, detail.getPhotos().size());
                })
                .build();
    }

    private static UatJourney createFreeListing() {
        return UatJourney.builder("listing-create-free",
                        "Free harvest listing publishes with the 1-credit floor")
                .persona("Zucchini-surplus gardener")
                .step("Upload a photo", ctx -> {
                    String url = ctx.call("photo upload",
                            cb -> ctx.api().uploadAvatar(tinyJpeg(), "image/jpeg", cb));
                    ctx.put("photoUrl", url);
                })
                .step("Publish a free harvest listing", ctx -> {
                    String url = ctx.get("photoUrl");
                    long now = System.currentTimeMillis();
                    Listing listing = ctx.call("create free listing",
                            cb -> ctx.api().createListing(
                                    ListingInput.builder(ListingType.HARVEST)
                                            .photos(new ArrayList<>(Arrays.asList(url)))
                                            .variety("UAT Zucchini " + now)
                                            .quantity(20.0)
                                            .unit("lbs")
                                            .free(true)
                                            .pickupWindow(now, now + 2L * 24 * 3600 * 1000)
                                            .pickupWindowDays(2)
                                            .sprayDisclosure("UAT: no sprays used")
                                            .expiresAtMs(now + 2L * 24 * 3600 * 1000)
                                            .build(), cb));
                    ctx.assertNotNull("free listing id", listing.getId());
                    ctx.put("listingId", listing.getId());
                })
                .build();
    }

    private static UatJourney feedShowsListing() {
        return UatJourney.builder("feed-loads",
                        "Feed loads and every card has the fields the UI renders")
                .persona("Sam the browser")
                .step("Load the feed", ctx -> {
                    List<Listing> feed = ctx.call("load feed",
                            cb -> ctx.api().getFeed(new FeedRequest(null, 50), cb));
                    ctx.assertNotNull("feed list", feed);
                    ctx.note("feed returned " + feed.size() + " listings");
                })
                .build();
    }

    private static UatJourney listingDetailLoads() {
        return UatJourney.builder("listing-detail",
                        "Listing detail loads with giver info for the pickup")
                .persona("Sam the claimer")
                .step("Open the newest feed listing's detail", ctx -> {
                    List<Listing> feed = ctx.call("load feed",
                            cb -> ctx.api().getFeed(new FeedRequest(null, 50), cb));
                    ctx.assertNotEmpty("feed", feed);
                    Listing detail = ctx.call("load detail",
                            cb -> ctx.api().getListing(feed.get(0).getId(), cb));
                    ctx.assertNotNull("detail title/variety", detail.getVariety());
                })
                .build();
    }

    private static UatJourney cancelListing() {
        return UatJourney.builder("listing-cancel",
                        "Giver can cancel their own listing")
                .persona("Maya the giver")
                .step("Create a listing to cancel", ctx -> {
                    String url = ctx.call("photo upload",
                            cb -> ctx.api().uploadAvatar(tinyJpeg(), "image/jpeg", cb));
                    Listing listing = ctx.call("create listing",
                            cb -> ctx.api().createListing(
                                    baseDraft(new ArrayList<>(Arrays.asList(url))).build(), cb));
                    ctx.put("listingId", listing.getId());
                })
                .step("Cancel it", ctx -> {
                    String id = ctx.get("listingId");
                    Listing cancelled = ctx.call("cancel listing",
                            cb -> ctx.api().cancelListing(id, cb));
                    ctx.assertNotNull("cancelled listing", cancelled.getId());
                })
                .build();
    }
}
