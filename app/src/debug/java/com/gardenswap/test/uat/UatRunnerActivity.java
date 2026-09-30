package com.gardenswap.test.uat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gardenswap.test.MainActivity;
import com.gardenswap.test.ui.Ui;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

/**
 * Debug-only UAT console. Lists every journey in {@link UatRegistry} with
 * a per-journey "Run" button plus "Run all" and "Run photo regression".
 * Results render inline; the full text report is also copied to the log
 * so it can be pasted back for review.
 *
 * <p>Debug builds only — never ships in release.
 */
public class UatRunnerActivity extends Activity {

    private LinearLayout resultsBox;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String lastReport = null;
    private TextView authBanner;
    private Button loginButton;

    @Override
    protected void onResume() {
        super.onResume();
        checkAuth();
    }

    private void checkAuth() {
        com.google.firebase.auth.FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();
        boolean signedIn = user != null;
        authBanner.setVisibility(android.view.View.VISIBLE);
        loginButton.setVisibility(signedIn ? android.view.View.GONE : android.view.View.VISIBLE);
        if (signedIn) {
            String who = user.getEmail() != null ? user.getEmail()
                    : (user.getPhoneNumber() != null ? user.getPhoneNumber() : user.getUid());
            authBanner.setText("Signed in as " + who
                    + " — journeys run against this account.");
        } else {
            authBanner.setText("Not signed in — journeys will fail. "
                    + "Tap Log in, sign in with the main app, then return here.");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.column(this, 12);
        int pad = Ui.dp(this, 16);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page);
        setContentView(scroll);

        TextView title = Ui.title(this, "UAT Console (debug)");
        page.addView(title);
        page.addView(Ui.caption(this,
                UatRegistry.all().size() + " journeys. Each drives the real API "
                        + "with your current session."));

        authBanner = Ui.caption(this, "");
        authBanner.setVisibility(android.view.View.GONE);
        Button loginButton = Ui.primaryButton(this, "Log in");
        loginButton.setVisibility(android.view.View.GONE);
        loginButton.setOnClickListener(v -> startActivity(
                new Intent(this, MainActivity.class)));
        page.addView(authBanner);
        page.addView(loginButton);
        Ui.gap(page, this, 8);
        this.loginButton = loginButton;

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button runAll = Ui.rowButton(this, "Run all", true);
        runAll.setOnClickListener(v -> runJourneys(UatRegistry.all()));
        actions.addView(runAll);
        Button runPhotos = Ui.rowButton(this, "Photo regression", false);
        runPhotos.setOnClickListener(v -> runJourneys(UatRegistry.photoRegression()));
        actions.addView(runPhotos);
        Button copyReport = Ui.rowButton(this, "Copy report", false);
        copyReport.setOnClickListener(v -> {
            if (lastReport == null) {
                return;
            }
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText(
                    "UAT report", lastReport));
        });
        actions.addView(copyReport);
        page.addView(actions);
        Ui.gap(page, this, 8);

        // Per-journey buttons.
        for (UatJourney journey : UatRegistry.all()) {
            Button b = Ui.secondaryButton(this, journey.id());
            b.setOnClickListener(v -> runJourneys(
                    java.util.Collections.singletonList(journey)));
            page.addView(b);
        }
        Ui.gap(page, this, 8);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(resultsBox);

        // Remote trigger: started from an FCM uat_run command.
        String remoteJourney = getIntent().getStringExtra("uat_journey");
        if (remoteJourney != null) {
            if ("all".equals(remoteJourney)) {
                runJourneys(UatRegistry.all());
            } else if ("photo-regression".equals(remoteJourney)) {
                runJourneys(UatRegistry.photoRegression());
            } else {
                for (UatJourney j : UatRegistry.all()) {
                    if (j.id().equals(remoteJourney)) {
                        runJourneys(java.util.Collections.singletonList(j));
                        break;
                    }
                }
            }
        }
    }

    private void runJourneys(List<UatJourney> journeys) {
        resultsBox.removeAllViews();
        TextView running = Ui.body(this, "Running " + journeys.size()
                + " journey(s)… (writes to your account — use a test account)");
        resultsBox.addView(running);

        new UatRunner(new UatRunner.Listener() {
            @Override
            public void onJourneyStarted(UatJourney journey) {
                main.post(() -> resultsBox.addView(
                        Ui.body(UatRunnerActivity.this, "▶ " + journey.id())));
            }

            @Override
            public void onJourneyFinished(UatRunner.JourneyResult result) {
                main.post(() -> {
                    TextView row = Ui.body(UatRunnerActivity.this,
                            (result.passed ? "PASS " : "FAIL ") + result.journey.id());
                    resultsBox.addView(row);
                    if (!result.passed) {
                        for (UatRunner.StepResult s : result.steps) {
                            if (!s.passed) {
                                TextView detail = Ui.caption(UatRunnerActivity.this,
                                        "  ✗ " + s.description + "\n  " + s.detail);
                                resultsBox.addView(detail);
                                break;
                            }
                        }
                    }
                });
            }

            @Override
            public void onRunFinished(List<UatRunner.JourneyResult> results) {
                final String report = UatRunner.renderReport(results);
                lastReport = report;
                android.util.Log.i("UAT", "\n" + report);
                UatReporter.saveLocal(UatRunnerActivity.this, report);
                main.post(() -> {
                    TextView done = Ui.title(UatRunnerActivity.this, "Done");
                    resultsBox.addView(done);
                    TextView summary = Ui.body(UatRunnerActivity.this,
                            report.split("\n")[0]
                                    + "\nFull report in logcat (tag UAT) and "
                                    + UatReporter.localPath());
                    resultsBox.addView(summary);
                });
            }
        }).run(journeys);
    }
}
