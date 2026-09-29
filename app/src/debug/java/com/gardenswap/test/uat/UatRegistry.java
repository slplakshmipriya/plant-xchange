package com.gardenswap.test.uat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The exhaustive UAT journey catalog, organized by PRD pillar.
 *
 * <p>Pillar 1 — Seedling swap: listing create (1/2 photos), feed, detail,
 * cancel, claim guards, swap history.
 * <p>Pillar 2 — Plant sitting: sitter browse/detail, bookings.
 * <p>Pillar 3 — Pick-your-own: trees, slots.
 * <p>Pillar 4 — Harvest swap: free listings, harvest log.
 * <p>Cross-cutting: credits/wallet + expiry, want-list + matches, chat
 * (text + photo), profile + avatar, IDV, notification prefs.
 *
 * <p>Two-party flows (giver accepts a claimer's claim, both confirm a swap)
 * need a second account and are marked in their journey notes; the runner
 * executes everything else with the current session.
 */
public final class UatRegistry {

    private UatRegistry() {
    }

    public static List<UatJourney> all() {
        List<UatJourney> journeys = new ArrayList<>();
        journeys.addAll(JourneysListings.all());
        journeys.addAll(JourneysClaims.all());
        journeys.addAll(JourneysSocial.all());
        journeys.addAll(JourneysMoney.all());
        return Collections.unmodifiableList(journeys);
    }

    /** Journeys covering the code changed most recently (photo pipeline). */
    public static List<UatJourney> photoRegression() {
        List<UatJourney> journeys = new ArrayList<>();
        for (UatJourney j : all()) {
            String id = j.id();
            if (id.startsWith("listing-create") || id.equals("avatar-upload")
                    || id.equals("chat-send-photo") || id.equals("feed-loads")
                    || id.equals("listing-detail")) {
                journeys.add(j);
            }
        }
        return journeys;
    }
}
