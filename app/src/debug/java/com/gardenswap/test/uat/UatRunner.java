package com.gardenswap.test.uat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Executes journeys and collects a structured report. A run executes on a
 * background thread; each step is timed, and the first failure stops the
 * journey (later steps would cascade).
 */
public final class UatRunner {

    public static final class StepResult {
        public final String description;
        public final boolean passed;
        public final String detail;
        public final long durationMs;

        StepResult(String description, boolean passed, String detail, long durationMs) {
            this.description = description;
            this.passed = passed;
            this.detail = detail;
            this.durationMs = durationMs;
        }
    }

    public static final class JourneyResult {
        public final UatJourney journey;
        public final List<StepResult> steps;
        public final boolean passed;

        JourneyResult(UatJourney journey, List<StepResult> steps) {
            this.journey = journey;
            this.steps = Collections.unmodifiableList(steps);
            boolean ok = true;
            for (StepResult s : steps) {
                if (!s.passed) {
                    ok = false;
                    break;
                }
            }
            this.passed = ok;
        }
    }

    public interface Listener {
        void onJourneyStarted(UatJourney journey);
        void onJourneyFinished(JourneyResult result);
        void onRunFinished(List<JourneyResult> results);
    }

    private final Listener listener;

    public UatRunner(Listener listener) {
        this.listener = listener;
    }

    /** Runs the given journeys sequentially on a background thread. */
    public void run(final List<UatJourney> journeys) {
        new Thread(() -> {
            List<JourneyResult> results = new ArrayList<>();
            for (UatJourney journey : journeys) {
                listener.onJourneyStarted(journey);
                JourneyResult result = runOne(journey);
                results.add(result);
                listener.onJourneyFinished(result);
            }
            listener.onRunFinished(results);
        }, "uat-runner").start();
    }

    private JourneyResult runOne(UatJourney journey) {
        UatContext ctx = new UatContext();
        List<StepResult> stepResults = new ArrayList<>();
        for (UatStep step : journey.steps()) {
            long start = System.currentTimeMillis();
            try {
                step.execute(ctx);
                stepResults.add(new StepResult(step.description(), true,
                        ctx.log(), System.currentTimeMillis() - start));
            } catch (Exception e) {
                String detail = e.getMessage() != null ? e.getMessage() : e.toString();
                stepResults.add(new StepResult(step.description(), false,
                        detail, System.currentTimeMillis() - start));
                break;
            }
        }
        return new JourneyResult(journey, stepResults);
    }

    /** Renders a plain-text report (also the payload posted by UatReporter). */
    public static String renderReport(List<JourneyResult> results) {
        StringBuilder sb = new StringBuilder();
        int passed = 0;
        for (JourneyResult r : results) {
            if (r.passed) {
                passed++;
            }
        }
        sb.append("UAT report: ").append(passed).append('/')
                .append(results.size()).append(" journeys passed\n\n");
        for (JourneyResult r : results) {
            sb.append(r.passed ? "PASS " : "FAIL ")
                    .append(r.journey.id()).append(" — ")
                    .append(r.journey.title()).append('\n');
            for (StepResult s : r.steps) {
                sb.append("  ").append(s.passed ? "✓ " : "✗ ")
                        .append(s.description());
                if (!s.passed) {
                    sb.append("  << ").append(s.detail);
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
