package com.gardenswap.test.wallet;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.LedgerEntry;
import com.gardenswap.test.api.Wallet;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.LedgerFormatter;

/**
 * Credit wallet (AND-060).
 *
 * <p>Balance hero (server-authoritative — rendered, never computed here),
 * ledger list, expiry countdown banner, weekly-cap line, starter-credits
 * note, and a "how credits work" explainer sheet. Client-side credit math
 * stays a UI mirror only; the backend ledger (API-060) is the source of
 * truth (SEC-060).
 */
public class WalletActivity extends AppCompatActivity {

    private TextView statusText;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Wallet");
        title.setTextSize(20);
        statusText = Ui.status(this);
        content = Ui.column(this, 0);

        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(statusText);
        Ui.gap(root, this, 8);
        root.addView(content);
        setContentView(root);

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(); // balance may have changed while confirming an exchange
    }

    private void load() {
        statusText.setText("Loading wallet…");
        ApiProvider.get().getWallet(new GardenSwapApi.Callback<Wallet>() {
            @Override
            public void onSuccess(Wallet wallet) {
                render(wallet);
            }

            @Override
            public void onError(ApiException e) {
                statusText.setText("Couldn't load your wallet (" + e.getCode() + ").");
            }
        });
    }

    private void render(Wallet wallet) {
        statusText.setText("");
        content.removeAllViews();
        long now = System.currentTimeMillis();

        TextView balance = Ui.label(this, wallet.getBalance() + " credits");
        balance.setTextSize(32);
        content.addView(balance);
        Ui.gap(content, this, 4);

        String banner = LedgerFormatter.expiryBanner(wallet.getNextExpiryMs(), now);
        if (banner != null) {
            TextView bannerView = Ui.label(this, banner);
            content.addView(bannerView);
            Ui.gap(content, this, 4);
        }

        content.addView(Ui.label(this, LedgerFormatter.weeklyCapLine(wallet.getEarnedThisWeek())));
        Ui.gap(content, this, 4);
        content.addView(Ui.label(this, LedgerFormatter.starterNote()));
        Ui.gap(content, this, 8);

        android.widget.Button explainerButton = Ui.button(this, "How credits work");
        explainerButton.setOnClickListener(v -> showExplainer());
        content.addView(explainerButton);
        Ui.gap(content, this, 12);

        TextView historyTitle = Ui.label(this, "History");
        historyTitle.setTextSize(16);
        content.addView(historyTitle);
        Ui.gap(content, this, 4);

        if (wallet.getEntries().isEmpty()) {
            content.addView(Ui.label(this, "No activity yet."));
        }
        for (LedgerEntry entry : wallet.getEntries()) {
            content.addView(Ui.label(this, LedgerFormatter.formatEntry(entry)));
            Ui.gap(content, this, 2);
        }
    }

    private void showExplainer() {
        new AlertDialog.Builder(this)
                .setTitle("How credits work")
                .setMessage("Credits are the neighborhood's way of saying thanks.\n\n"
                        + "• You start with 3 credits.\n"
                        + "• Sharing seedlings or harvest earns credits when the other "
                        + "person confirms.\n"
                        + "• Credits move only after BOTH sides confirm an exchange.\n"
                        + "• You can earn up to 10 credits per week.\n"
                        + "• Credits expire seasonally — spend them, don't hoard them.\n"
                        + "• Credits can't be bought, sold, or cashed out.")
                .setPositiveButton("Got it", null)
                .show();
    }
}
