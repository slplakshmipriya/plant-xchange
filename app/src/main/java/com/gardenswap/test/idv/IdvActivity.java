package com.gardenswap.test.idv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.IdvSession;
import com.gardenswap.test.api.IdvStatus;
import com.gardenswap.test.api.MockGardenSwapApi;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.ui.VerifiedBadgeView;
import com.gardenswap.test.util.IdvStatusMapper;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * ID verification screen (AND-011).
 *
 * <p>Flow: check current status → "Start verification" creates a session via
 * {@link GardenSwapApi#createIdvSession} → {@link IdvProvider} runs the
 * provider flow (currently {@link StubIdvProvider}) → badge + status render.
 * Failure shows a retry path; cancel returns to the start state.
 *
 * <p>UID-022 restyles this screen against the prototype: eyebrow + headline,
 * a "What to expect" card, the verification status in a card, and a primary
 * pill Continue button. The stub journey and all state transitions are
 * unchanged — only visuals moved.
 */
public class IdvActivity extends AppCompatActivity {

    /** Swap this line for the real provider SDK; nothing else changes. */
    private final IdvProvider idvProvider = new StubIdvProvider();

    private enum Action {
        START_VERIFICATION,
        REFRESH_STATUS
    }

    private VerifiedBadgeView badgeView;
    private TextView statusText;
    private Button actionButton;
    private Action pendingAction = Action.START_VERIFICATION;
    private boolean verifying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Ui.column(this, 24);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setBackgroundColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_bg, getTheme()));

        // Explainer header.
        root.addView(Ui.eyebrow(this, "Trust & safety"));
        Ui.gap(root, this, 4);
        root.addView(Ui.headline(this, "Verify your identity"));
        Ui.gap(root, this, 8);
        root.addView(Ui.body(this,
                "ID verification unlocks plant sitting and solo pickups, "
                        + "so neighbors know exactly who they're sharing with."));
        Ui.gap(root, this, 16);

        // What-to-expect card.
        LinearLayout expectCard = Ui.card(this);
        expectCard.addView(Ui.title(this, "What to expect"));
        Ui.gap(expectCard, this, 8);
        expectCard.addView(stepRow(this, "1", "Snap a photo of your ID"));
        Ui.gap(expectCard, this, 8);
        expectCard.addView(stepRow(this, "2", "Take a quick selfie to match it"));
        Ui.gap(expectCard, this, 8);
        expectCard.addView(stepRow(this, "3", "Get verified — usually within minutes"));
        root.addView(expectCard);
        Ui.gap(root, this, 16);

        // Verification status card.
        LinearLayout statusCard = Ui.card(this);
        badgeView = new VerifiedBadgeView(this);
        statusText = Ui.body(this, "");
        statusCard.addView(badgeView);
        Ui.gap(statusCard, this, 8);
        statusCard.addView(statusText);
        root.addView(statusCard);
        Ui.gap(root, this, 16);

        // Primary Continue action. Text/visibility are driven by renderStatus,
        // exactly as before — only the styling is new.
        actionButton = Ui.primaryButton(this, "Start verification");
        actionButton.setOnClickListener(v -> {
            if (pendingAction == Action.START_VERIFICATION) {
                startVerification();
            } else {
                refreshStatus();
            }
        });
        root.addView(actionButton);
        Ui.gap(root, this, 12);
        root.addView(Ui.caption(this,
                "Your documents stay with the verification provider — "
                        + "we only store the result."));

        scroll.addView(root);
        setContentView(scroll);

        refreshStatus();
    }

    /** Numbered step row: acid dot + body copy. */
    private static LinearLayout stepRow(Context context, String number, String text) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = new TextView(context);
        dot.setText(number);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_acid, context.getTheme()));
        int size = Ui.dp(context, 28);
        dot.setBackground(dotBg);
        dot.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        dot.setGravity(Gravity.CENTER);
        dot.setTextSize(14);
        dot.setTypeface(
                ResourcesCompat.getFont(context, R.font.dm_sans), Typeface.BOLD);
        dot.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_leaf_dark, context.getTheme()));

        TextView label = Ui.body(context, text);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.setMarginStart(Ui.dp(context, 12));
        label.setLayoutParams(labelParams);

        row.addView(dot);
        row.addView(label);
        return row;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rotation (or backgrounding) during the provider flow strands the
        // in-flight listener on the destroyed instance; re-reading status
        // here keeps the badge truthful in all cases.
        if (!verifying) {
            refreshStatus();
        }
    }

    private void refreshStatus() {
        statusText.setText("Checking verification status…");
        actionButton.setEnabled(false);
        ApiProvider.get().getIdvStatus(new GardenSwapApi.Callback<IdvStatus>() {
            @Override
            public void onSuccess(IdvStatus status) {
                renderStatus(status);
            }

            @Override
            public void onError(ApiException e) {
                statusText.setText("Couldn't load status (" + e.getCode() + ").");
                actionButton.setText("Retry");
                pendingAction = Action.REFRESH_STATUS;
                actionButton.setEnabled(true);
                actionButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void renderStatus(IdvStatus status) {
        badgeView.setState(IdvStatusMapper.map(status));
        actionButton.setVisibility(View.VISIBLE);
        actionButton.setEnabled(true);
        // Default body-ink text; FAILED gets the red treatment so the failure
        // reads at a glance. The badge mapping itself is unchanged (FAILED →
        // UNVERIFIED badge) so IdvStatusMapperTest still holds.
        statusText.setTextColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_ink, getTheme()));
        switch (status) {
            case VERIFIED:
                statusText.setText("You're ID-verified.");
                actionButton.setVisibility(View.GONE);
                break;
            case PENDING:
                statusText.setText("Verification is pending review — check back soon.");
                actionButton.setText("Check again");
                pendingAction = Action.REFRESH_STATUS;
                break;
            case FAILED:
                statusText.setText("Verification didn't go through. Please try again.");
                statusText.setTextColor(ResourcesCompat.getColor(
                        getResources(), R.color.garden_red, getTheme()));
                actionButton.setText("Try again");
                pendingAction = Action.START_VERIFICATION;
                break;
            case UNVERIFIED:
            default:
                statusText.setText("Not verified yet.");
                actionButton.setText("Start verification");
                pendingAction = Action.START_VERIFICATION;
                break;
        }
    }

    private void startVerification() {
        if (verifying) {
            return;
        }
        verifying = true;
        statusText.setText("Creating verification session…");
        actionButton.setEnabled(false);
        ApiProvider.get().createIdvSession(new GardenSwapApi.Callback<IdvSession>() {
            @Override
            public void onSuccess(IdvSession session) {
                statusText.setText("Complete verification in the provider flow…");
                idvProvider.start(IdvActivity.this, session.getSessionId(),
                        new IdvProvider.Listener() {
                            @Override
                            public void onResult(IdvStatus status) {
                                verifying = false;
                                FirebaseAnalytics.getInstance(IdvActivity.this).logEvent(
                                        status == IdvStatus.VERIFIED
                                                ? "idv_verified" : "idv_failed",
                                        null);
                                syncMockStatus(status);
                                renderStatus(status);
                            }

                            @Override
                            public void onCanceled() {
                                verifying = false;
                                refreshStatus();
                            }
                        });
            }

            @Override
            public void onError(ApiException e) {
                verifying = false;
                statusText.setText(
                        "Couldn't start verification (" + e.getCode() + "). Try again.");
                actionButton.setEnabled(true);
            }
        });
    }

    /**
     * Mock-phase consistency: the stub provider reports the outcome to us, so
     * mirror it into the mock backend's status. The real backend derives this
     * server-side from the provider webhook (API-012) — this goes away with
     * the mock.
     */
    private void syncMockStatus(IdvStatus status) {
        GardenSwapApi api = ApiProvider.get();
        if (api instanceof MockGardenSwapApi) {
            ((MockGardenSwapApi) api).setMockIdvStatus(status);
        }
    }
}
