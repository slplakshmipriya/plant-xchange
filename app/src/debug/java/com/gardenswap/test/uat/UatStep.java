package com.gardenswap.test.uat;

/**
 * A single step inside a {@link UatJourney}. The body receives the shared
 * {@link UatContext} (API client, scratch data, assertions) and either
 * completes or throws {@link UatFailure} with what the user would have seen.
 */
public final class UatStep {

    /** Step logic. Throw {@link UatFailure} when the observed outcome is wrong. */
    public interface Body {
        void run(UatContext ctx) throws Exception;
    }

    private final String description;
    private final Body body;

    public UatStep(String description, Body body) {
        this.description = description;
        this.body = body;
    }

    public String description() {
        return description;
    }

    void execute(UatContext ctx) throws Exception {
        body.run(ctx);
    }
}
