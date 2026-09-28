package com.gardenswap.app.wallet;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.app.R;
import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.ExchangeConfirmation;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.ExchangeConfirmLogic;
import com.gardenswap.app.util.ExchangeConfirmLogic.State;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Exchange confirmation screen (AND-060), restyled per the
 * garden-swap-app-ui-design prototype (UID-016).
 *
 * <p>Both parties must confirm before credits move (PRD §8) — the server
 * enforces this; the screen just reports the state. After confirming, the
 * user waits for the other side; when both have confirmed, the ledger
 * entry is written and the wallet updates.
 *
 * <p>Decline is mock-only: the API contract exposes {@code confirmExchange}
 * but no decline endpoint, so declining updates local state and never
 * touches the network. The real confirm path is unchanged.
 */
public class ConfirmExchangeActivity extends AppCompatActivity {

    public static final String EXTRA_EXCHANGE_ID = "exchange_id";
    public static final String EXTRA_CREDIT_COST = "credit_cost";
    public static final String EXTRA_LISTING_TITLE = "listing_title";
    public static final String EXTRA_GIVER_NAME = "giver_name";
    public static final String EXTRA_RECEIVER_NAME = "receiver_name";
    public static final String EXTRA_USER_IS_GIVER = "user_is_giver";

    private TextView statusText;
    private TextView myStatusView;
    private TextView otherStatusView;
    private String myRole;
    private String otherRole;
    private Button confirmButton;
    private Button declineButton;
    private String exchangeId;
    private int creditCost;
    private State state = State.PENDING;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        exchangeId = getIntent().getStringExtra(EXTRA_EXCHANGE_ID);
        creditCost = getIntent().getIntExtra(EXTRA_CREDIT_COST, 1);
        String listingTitle = getIntent().getStringExtra(EXTRA_LISTING_TITLE);
        String giverName = getIntent().getStringExtra(EXTRA_GIVER_NAME);
        String receiverName = getIntent().getStringExtra(EXTRA_RECEIVER_NAME);
        boolean userIsGiver = getIntent().getBooleanExtra(EXTRA_USER_IS_GIVER, false);
        if (exchangeId == null) {
            exchangeId = "mock-exchange-1";
        }
        if (listingTitle == null) {
            listingTitle = "Heirloom tomato seedlings";
        }
        if (giverName == null) {
            giverName = "Maya R.";
        }
        if (receiverName == null) {
            receiverName = "You";
        }
        myRole = userIsGiver ? "Giver" : "Receiver";
        otherRole = userIsGiver ? "Receiver" : "Giver";

        LinearLayout root = Ui.column(this, 24);
        root.addView(Ui.eyebrow(this, "Confirm exchange"));
        Ui.gap(root, this, 8);
        root.addView(Ui.headline(this, "Confirm exchange"));
        Ui.gap(root, this, 16);

        // Two-party exchange card: listing, credit amount, both sides.
        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, listingTitle));
        Ui.gap(card, this, 4);
        card.addView(Ui.body(this, ExchangeConfirmLogic.creditLine(creditCost)));
        Ui.gap(card, this, 12);
        String myName = userIsGiver ? giverName : receiverName;
        String otherName = userIsGiver ? receiverName : giverName;
        myStatusView = addPartyRow(card, myName, myRole + " · you", true);
        Ui.gap(card, this, 8);
        otherStatusView = addPartyRow(card, otherName, otherRole, false);
        root.addView(card);
        Ui.gap(root, this, 16);

        root.addView(Ui.body(this,
                "Confirming means the exchange happened as described. "
                        + ExchangeConfirmLogic.creditLine(creditCost)
                        + " move only after BOTH of you confirm — "
                        + "neither side can take credits unilaterally."));
        Ui.gap(root, this, 16);

        statusText = Ui.status(this);
        root.addView(statusText);
        Ui.gap(root, this, 16);

        confirmButton = Ui.primaryButton(this, "Confirm exchange");
        confirmButton.setOnClickListener(v -> confirm());
        root.addView(confirmButton);
        Ui.gap(root, this, 12);
        declineButton = Ui.secondaryButton(this, "Decline");
        declineButton.setOnClickListener(v -> decline());
        root.addView(declineButton);

        setContentView(root);
    }

    /**
     * One side of the exchange: initial badge, name, and a status line that
     * is updated as the confirmation progresses. Returns the status view.
     */
    private TextView addPartyRow(LinearLayout card, String name, String role, boolean isYou) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView badge = new TextView(this);
        badge.setText(initialOf(name));
        badge.setGravity(Gravity.CENTER);
        int size = Ui.dp(this, 44);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(size, size);
        badgeParams.setMarginEnd(Ui.dp(this, 12));
        badge.setLayoutParams(badgeParams);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_acid, getTheme()));
        badge.setBackground(badgeBg);
        badge.setTextColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_ink, getTheme()));
        badge.setTextSize(18);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(Ui.body(this, name + (isYou ? " (you)" : "")));
        TextView statusView = Ui.caption(this, role + " · waiting");
        col.addView(statusView);

        row.addView(badge);
        row.addView(col);
        card.addView(row);
        return statusView;
    }

    private static String initialOf(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.substring(0, 1).toUpperCase();
    }

    private void setActionsEnabled(boolean enabled) {
        confirmButton.setEnabled(enabled);
        declineButton.setEnabled(enabled);
    }

    private void confirm() {
        if (!ExchangeConfirmLogic.canAct(state)) {
            return;
        }
        state = ExchangeConfirmLogic.onConfirmStarted(state);
        setActionsEnabled(false);
        statusText.setText(ExchangeConfirmLogic.statusMessage(state, creditCost, false));
        ApiProvider.get().confirmExchange(exchangeId,
                new GardenSwapApi.Callback<ExchangeConfirmation>() {
                    @Override
                    public void onSuccess(ExchangeConfirmation result) {
                        state = ExchangeConfirmLogic.onConfirmSucceeded(state);
                        render(result);
                    }

                    @Override
                    public void onError(ApiException e) {
                        state = ExchangeConfirmLogic.onConfirmError(state);
                        setActionsEnabled(true);
                        statusText.setText("Couldn't confirm (" + e.getCode() + "). Try again.");
                    }
                });
    }

    /**
     * Mock-only decline: no decline endpoint exists in the API contract, so
     * this only flips local state. The real confirm path above is untouched.
     */
    private void decline() {
        if (!ExchangeConfirmLogic.canAct(state)) {
            return;
        }
        state = ExchangeConfirmLogic.onDecline(state);
        myStatusView.setText(myRole + " · you · declined");
        confirmButton.setVisibility(View.GONE);
        declineButton.setVisibility(View.GONE);
        statusText.setText(ExchangeConfirmLogic.statusMessage(state, creditCost, false));
    }

    private void render(ExchangeConfirmation result) {
        boolean otherConfirmed = result.isOtherConfirmed() || result.isCreditsMoved();
        if (!result.isMyConfirmed()) {
            statusText.setText("Confirmation recorded.");
        } else {
            statusText.setText(ExchangeConfirmLogic.statusMessage(
                    State.CONFIRMED, result.getCreditCost(), otherConfirmed));
        }
        myStatusView.setText(myRole + " · you · confirmed");
        otherStatusView.setText(otherRole + " · "
                + (otherConfirmed ? "confirmed" : "waiting"));
        confirmButton.setVisibility(View.GONE);
        declineButton.setVisibility(View.GONE);
        if (result.isCreditsMoved()) {
            FirebaseAnalytics.getInstance(this).logEvent("credit_spent", null);
        }
    }
}
