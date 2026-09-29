package com.gardenswap.test.uat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One simulated user journey: an ordered list of steps executed against the
 * real {@link com.gardenswap.test.api.GardenSwapApi} (headless, API-level).
 *
 * <p>Journeys are the UAT catalog. Each journey drives the same code paths
 * the UI uses — API client, JSON parsing, validators — and asserts the
 * outcomes a user would observe on screen. They run in debug builds only,
 * via {@link UatRunner} (triggered from {@link UatRunnerActivity} or by a
 * remote {@code uat_run} FCM data message).
 */
public final class UatJourney {

    private final String id;
    private final String title;
    private final String persona;
    private final List<UatStep> steps;

    private UatJourney(Builder b) {
        this.id = b.id;
        this.title = b.title;
        this.persona = b.persona;
        this.steps = Collections.unmodifiableList(new ArrayList<>(b.steps));
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** The PRD persona this journey is written for (e.g. "Maya the giver"). */
    public String persona() {
        return persona;
    }

    public List<UatStep> steps() {
        return steps;
    }

    public static Builder builder(String id, String title) {
        return new Builder(id, title);
    }

    public static final class Builder {
        private final String id;
        private final String title;
        private String persona = "";
        private final List<UatStep> steps = new ArrayList<>();

        private Builder(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public Builder persona(String persona) {
            this.persona = persona;
            return this;
        }

        public Builder step(String description, UatStep.Body body) {
            steps.add(new UatStep(description, body));
            return this;
        }

        public UatJourney build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("journey " + id + " has no steps");
            }
            return new UatJourney(this);
        }
    }
}
