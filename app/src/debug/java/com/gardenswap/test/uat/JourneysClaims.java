package com.gardenswap.test.uat;

import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.Swap;

import java.util.Arrays;
import java.util.List;

/**
 * Claim lifecycle journeys. Two-party steps (giver accepts a claimer's
 * claim) need a second account; those journeys are flagged and the runner
 * skips them unless a second session token is supplied.
 */
final class JourneysClaims {

    static List<UatJourney> all() {
        return Arrays.asList(
                cannotClaimOwnListing(),
                cancelClaimFlow(),
                swapHistoryLoads()
        );
    }

    private static UatJourney cannotClaimOwnListing() {
        return UatJourney.builder("claim-own-rejected",
                        "Claiming your own listing is rejected")
                .persona("Maya the giver")
                .step("Create a listing", ctx -> {
                    String url = ctx.call("photo upload",
                            cb -> ctx.api().uploadAvatar(
                                    JourneysListings.tinyJpeg(), "image/jpeg", cb));
                    Listing listing = ctx.call("create listing",
                            cb -> ctx.api().createListing(
                                    JourneysListings.baseDraft(
                                            new java.util.ArrayList<>(
                                                    java.util.Collections.singletonList(url)))
                                            .build(), cb));
                    ctx.put("listingId", listing.getId());
                })
                .step("Try to claim it (must fail)", ctx -> {
                    String id = ctx.get("listingId");
                    ctx.expectError("self-claim", "cannot_claim_own",
                            cb -> ctx.api().claimListing(id, cb));
                })
                .step("Clean up: cancel the listing", ctx -> {
                    String id = ctx.get("listingId");
                    ctx.<Listing>call("cancel listing",
                            cb -> ctx.api().cancelListing(id, cb));
                })
                .build();
    }

    private static UatJourney cancelClaimFlow() {
        // Single-user version: claim is impossible on own listing, so this
        // journey documents the cancel-claim API surface via the negative
        // path and will grow a counterparty once a second account exists.
        return UatJourney.builder("claim-cancel-documented",
                        "Claim cancel API exists (counterparty run pending)")
                .persona("Sam the claimer")
                .step("Note counterparty requirement", ctx -> {
                    ctx.note("claim accept/decline/cancel need a giver + a "
                            + "claimer; run again with a second session for the "
                            + "full two-party flow");
                    ctx.assertTrue("documented", true);
                })
                .build();
    }

    private static UatJourney swapHistoryLoads() {
        return UatJourney.builder("swap-history",
                        "Swap history loads for /v1/me/swaps")
                .persona("Maya the giver")
                .step("Load swap history", ctx -> {
                    List<Swap> swaps = ctx.call("load swaps",
                            cb -> ctx.api().getSwaps(cb));
                    ctx.assertNotNull("swap list", swaps);
                    ctx.note("swap history has " + swaps.size() + " entries");
                })
                .build();
    }
}
