package com.gardenswap.test.wallet;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.ExchangeConfirmation;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.ui.Ui;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Exchange confirmation screen (AND-060).
 *
 * <p>Both parties must confirm before credits move (PRD §8) — the server
 * enforces this; the screen just reports the state. After confirming, the
 * user waits for the other side; when both have confirmed, the ledger
 * entry is written and the wallet updates.
 */
public class ConfirmExchangeActivity extends AppCompatActivity {

    public static final String EXTRA_EXCHANGE_ID = "exchange_id";
    public static final String EXTRA_CREDIT_COST = "credit_cost";

    private TextView statusText;
    private Button confirmButton;
    private String exchangeId;
    private boolean confirming;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        exchangeId = getIntent().getStringExtra(EXTRA_EXCHANGE_ID);
        int cost = getIntent().getIntExtra(EXTRA_CREDIT_COST, 1);
        if (exchangeId == null) {
            exchangeId = "mock-exchange-1";
        }

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Confirm exchange");
        title.setTextSize(20);
        TextView expl = Ui.label(this,
                "Confirming means the exchange happened as described. "
                        + cost + " credit" + (cost == 1 ? "" : "s")
                        + " move only after BOTH of you confirm — "
                        + "neither side can take credits unilaterally.");
        statusText = Ui.status(this);
        confirmButton = Ui.button(this, "Confirm exchange");
        confirmButton.setOnClickListener(v -> confirm());

        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(expl);
        Ui.gap(root, this, 16);
        root.addView(statusText);
        Ui.gap(root, this, 16);
        root.addView(confirmButton);
        setContentView(root);
    }

    private void confirm() {
        if (confirming) {
            return;
        }
        confirming = true;
        confirmButton.setEnabled(false);
        statusText.setText("Confirming…");
        ApiProvider.get().confirmExchange(exchangeId,
                new GardenSwapApi.Callback<ExchangeConfirmation>() {
                    @Override
                    public void onSuccess(ExchangeConfirmation result) {
                        confirming = false;
                        render(result);
                    }

                    @Override
                    public void onError(ApiException e) {
                        confirming = false;
                        confirmButton.setEnabled(true);
                        statusText.setText("Couldn't confirm (" + e.getCode() + "). Try again.");
                    }
                });
    }

    private void render(ExchangeConfirmation result) {
        if (result.isCreditsMoved()) {
            statusText.setText("Done! " + result.getCreditCost()
                    + " credits moved — both sides confirmed.");
            confirmButton.setVisibility(View.GONE);
            FirebaseAnalytics.getInstance(this).logEvent("credit_spent", null);
        } else if (result.isMyConfirmed() && !result.isOtherConfirmed()) {
            statusText.setText("You've confirmed. Waiting for the other person — "
                    + "credits move when they confirm too.");
            confirmButton.setVisibility(View.GONE);
        } else {
            statusText.setText("Confirmation recorded.");
            confirmButton.setVisibility(View.GONE);
        }
    }
}
