package com.gardenswap.test.uat;

import com.gardenswap.test.api.ChatMessage;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.WantItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Negative journeys: missing fields and irrelevant/garbage data.
 *
 * <p>Every journey asserts the app <em>rejects</em> the bad input with a clear,
 * user-safe error code — either client-side fail-fast (the user sees a
 * friendly message, nothing hits the network) or a clean backend 4xx (no
 * crash, no silent corruption, no half-written listing).
 *
 * <p>Nothing here writes data: all inputs are invalid, so every journey ends
 * with zero side effects on the account.
 */
final class JourneysValidation {

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    /** A draft that is valid in every respect except what the journey mutates. */
    private static com.gardenswap.test.api.ListingInput.Builder validDraft() {
        long now = System.currentTimeMillis();
        return JourneysListings.baseDraft(
                        new ArrayList<>(Collections.singletonList(
                                "https://example.com/uat-photo.jpg")))
                .sprayDisclosure("UAT: no sprays used")
                .pickupWindow(now + 24L * 3600 * 1000, now + 2L * 24 * 3600 * 1000)
                .expiresAtMs(now + 7L * 24 * 3600 * 1000);
    }

    static List<UatJourney> all() {
        return Arrays.asList(
                noPhotos(),
                blankSpray(),
                invertedWindow(),
                pastExpiry(),
                badPhotoUrl(),
                zeroQuantity(),
                creditTooHigh(),
                varietyTooLong(),
                sprayTooLong(),
                blankWant(),
                blankChatMessage(),
                ghostClaim());
    }

    // ---- missing fields (client fail-fast) ----

    private static UatJourney noPhotos() {
        return UatJourney.builder("listing-neg-no-photos",
                        "Listing with no photos is rejected up front")
                .persona("Ruth the fuzzer")
                .step("Create with an empty photo list", ctx -> {
                    ctx.<Listing>expectError("create without photos", "photo_required",
                            cb -> ctx.api().createListing(
                                    JourneysListings.baseDraft(new ArrayList<>()).build(), cb));
                })
                .build();
    }

    private static UatJourney blankSpray() {
        return UatJourney.builder("listing-neg-blank-spray",
                        "Whitespace-only spray disclosure is rejected (trimmed, not just empty)")
                .persona("Ruth the fuzzer")
                .step("Create with a spaces-only disclosure", ctx -> {
                    ctx.<Listing>expectError("create with blank spray", "spray_disclosure_required",
                            cb -> ctx.api().createListing(
                                    validDraft().sprayDisclosure("   ").build(), cb));
                })
                .build();
    }

    private static UatJourney blankWant() {
        return UatJourney.builder("want-neg-blank",
                        "Blank want-list entry is rejected up front")
                .persona("Ruth the fuzzer")
                .step("Add a spaces-only variety", ctx -> {
                    ctx.<WantItem>expectError("add blank want", "invalid_variety",
                            cb -> ctx.api().addWant("   ", cb));
                })
                .build();
    }

    private static UatJourney blankChatMessage() {
        return UatJourney.builder("chat-neg-blank-text",
                        "Blank chat message is rejected up front (no thread needed)")
                .persona("Ruth the fuzzer")
                .step("Send a spaces-only message", ctx -> {
                    ctx.<ChatMessage>expectError("send blank message", "empty_message",
                            cb -> ctx.api().sendMessage("thread-id-irrelevant", "  ", cb));
                })
                .build();
    }

    // ---- irrelevant / out-of-range data (backend 4xx) ----

    private static UatJourney invertedWindow() {
        return UatJourney.builder("listing-neg-inverted-window",
                        "Pickup window ending before it starts is rejected")
                .persona("Ruth the fuzzer")
                .step("Create with end < start", ctx -> {
                    long now = System.currentTimeMillis();
                    ctx.<Listing>expectError("create with inverted window", "invalid_window",
                            cb -> ctx.api().createListing(
                                    validDraft()
                                            .pickupWindow(now + 2L * 24 * 3600 * 1000,
                                                    now + 24L * 3600 * 1000)
                                            .build(),
                                    cb));
                })
                .build();
    }

    private static UatJourney pastExpiry() {
        return UatJourney.builder("listing-neg-past-expiry",
                        "Expiry in the past is rejected")
                .persona("Ruth the fuzzer")
                .step("Create already expired", ctx -> {
                    long now = System.currentTimeMillis();
                    ctx.<Listing>expectError("create with past expiry", "invalid_expiry",
                            cb -> ctx.api().createListing(
                                    validDraft().expiresAtMs(now - 3600 * 1000).build(),
                                    cb));
                })
                .build();
    }

    private static UatJourney badPhotoUrl() {
        return UatJourney.builder("listing-neg-bad-photo-url",
                        "Non-http(s) photo URL is rejected")
                .persona("Ruth the fuzzer")
                .step("Create with a garbage photo URL", ctx -> {
                    ctx.<Listing>expectError("create with bad photo URL", "invalid_photo_url",
                            cb -> ctx.api().createListing(
                                    validDraft()
                                            .photos(new ArrayList<>(Collections.singletonList(
                                                    "not-a-url")))
                                            .build(),
                                    cb));
                })
                .build();
    }

    private static UatJourney zeroQuantity() {
        return UatJourney.builder("listing-neg-zero-quantity",
                        "Zero quantity is rejected by the API")
                .persona("Ruth the fuzzer")
                .step("Create with quantity 0", ctx -> {
                    ctx.<Listing>expectError("create with zero quantity", "http_422",
                            cb -> ctx.api().createListing(
                                    validDraft().quantity(0.0).build(), cb));
                })
                .build();
    }

    private static UatJourney creditTooHigh() {
        return UatJourney.builder("listing-neg-credit-too-high",
                        "Credit cost above 3 is rejected by the API")
                .persona("Ruth the fuzzer")
                .step("Create with 9 credits", ctx -> {
                    ctx.<Listing>expectError("create with 9 credits", "http_422",
                            cb -> ctx.api().createListing(
                                    validDraft().creditCost(9).build(), cb));
                })
                .build();
    }

    private static UatJourney varietyTooLong() {
        return UatJourney.builder("listing-neg-variety-too-long",
                        "200-char variety name is rejected by the API (max 120)")
                .persona("Ruth the fuzzer")
                .step("Create with an overlong variety", ctx -> {
                    ctx.<Listing>expectError("create with long variety", "http_422",
                            cb -> ctx.api().createListing(
                                    validDraft().variety(repeat('V', 200)).build(), cb));
                })
                .build();
    }

    private static UatJourney sprayTooLong() {
        return UatJourney.builder("listing-neg-spray-too-long",
                        "2500-char spray disclosure is rejected by the API (max 2000)")
                .persona("Ruth the fuzzer")
                .step("Create with an overlong disclosure", ctx -> {
                    ctx.<Listing>expectError("create with long spray", "http_422",
                            cb -> ctx.api().createListing(
                                    validDraft().sprayDisclosure(repeat('S', 2500)).build(),
                                    cb));
                })
                .build();
    }

    private static UatJourney ghostClaim() {
        return UatJourney.builder("claim-neg-ghost-listing",
                        "Claiming a listing that doesn't exist is a clean 404")
                .persona("Ruth the fuzzer")
                .step("Claim a non-UUID id (never reaches the claim logic)", ctx -> {
                    ctx.<Listing>expectError("claim non-uuid id", "not_found",
                            cb -> ctx.api().claimListing("listing-that-does-not-exist", cb));
                })
                .step("Claim a well-formed but nonexistent UUID", ctx -> {
                    ctx.<Listing>expectError("claim ghost uuid", "listing_not_found",
                            cb -> ctx.api().claimListing(
                                    "00000000-0000-0000-0000-000000000000", cb));
                })
                .build();
    }

}
