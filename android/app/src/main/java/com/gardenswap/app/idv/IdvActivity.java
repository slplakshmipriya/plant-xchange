package com.gardenswap.app.idv;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.IdvSession;
import com.gardenswap.app.api.IdvStatus;
import com.gardenswap.app.api.MockGardenSwapApi;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.ui.VerifiedBadgeView;
import com.gardenswap.app.util.IdvStatusMapper;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * ID verification screen (AND-011).
 *
 * <p>Flow: check current status → "Start verification" creates a session via
 * {@link GardenSwapApi#createIdvSession} → {@link IdvProvider} runs the
 * provider flow (currently {@link StubIdvProvider}) → badge + status render.
 * Failure shows a retry path; cancel returns to the start state.
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

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Verify your identity");
        title.setTextSize(20);
        TextView expl = Ui.label(this,
                "ID verification unlocks plant sitting and solo pickups. "
                        + "Your documents stay with the verification provider — "
                        + "we only store the result.");
        badgeView = new VerifiedBadgeView(this);
        statusText = Ui.status(this);
        actionButton = Ui.button(this, "Start verification");
        actionButton.setOnClickListener(v -> {
            if (pendingAction == Action.START_VERIFICATION) {
                startVerification();
            } else {
                refreshStatus();
            }
        });

        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(expl);
        Ui.gap(root, this, 16);
        root.addView(badgeView);
        Ui.gap(root, this, 8);
        root.addView(statusText);
        Ui.gap(root, this, 16);
        root.addView(actionButton);
        setContentView(root);

        refreshStatus();
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
