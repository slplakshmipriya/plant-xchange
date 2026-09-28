package com.gardenswap.app.wallet;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gardenswap.app.R;
import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.LedgerEntry;
import com.gardenswap.app.api.Wallet;
import com.gardenswap.app.ui.LedgerAdapter;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.LedgerFormatter;
import com.gardenswap.app.util.WalletLogic;

import java.util.Collections;
import java.util.List;

/**
 * Credit wallet (AND-060), restyled to the garden-swap-app-ui-design
 * prototype (UID-015).
 *
 * <p>Balance hero card with an acid highlight strip (server-authoritative —
 * rendered, never computed here), expiry cue, weekly-cap line,
 * starter-credits note, "how credits work" explainer sheet, and the ledger
 * as a RecyclerView. Client-side credit math stays a UI mirror only; the
 * backend ledger (API-060) is the source of truth (SEC-060).
 */
public class WalletActivity extends AppCompatActivity {

    private static final long DAY_MS = 24 * 3_600_000L;

    private TextView statusText;
    private LedgerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 16);
        TextView title = Ui.headline(this, "Wallet");
        statusText = Ui.status(this);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LedgerAdapter();
        list.setAdapter(adapter);
        list.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(title);
        Ui.gap(root, this, 4);
        root.addView(statusText);
        Ui.gap(root, this, 8);
        root.addView(list);
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
        if (wallet == null) {
            statusText.setText("Couldn't load your wallet.");
            return;
        }
        statusText.setText("");
        long now = System.currentTimeMillis();
        adapter.setHeader(buildHeader(wallet, now));
        List<LedgerEntry> entries = wallet.getEntries();
        adapter.setEntries(entries == null ? Collections.emptyList() : entries);
    }

    /** Header: balance card, expiry cue, cap lines, explainer, history title. */
    private View buildHeader(Wallet wallet, long nowMs) {
        LinearLayout header = Ui.column(this, 0);

        // Balance hero card with acid highlight strip.
        LinearLayout card = Ui.card(this);
        View strip = new View(this);
        strip.setBackgroundColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_acid, getTheme()));
        strip.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6)));
        card.addView(strip);
        Ui.gap(card, this, 12);
        card.addView(Ui.display(this, String.valueOf(wallet.getBalance())));
        card.addView(Ui.eyebrow(this, "credits"));
        header.addView(card);
        Ui.gap(header, this, 12);

        // Expiry cue: new WalletLogic rule first, legacy banner as fallback
        // (covers the already-expired case LedgerFormatter owns).
        String cue = WalletLogic.expiryCue(daysUntilExpiry(wallet, nowMs));
        if (cue == null) {
            cue = LedgerFormatter.expiryBanner(wallet.getNextExpiryMs(), nowMs);
        }
        if (cue != null) {
            header.addView(Ui.body(this, cue));
            Ui.gap(header, this, 8);
        }

        header.addView(Ui.caption(this,
                LedgerFormatter.weeklyCapLine(wallet.getEarnedThisWeek())));
        Ui.gap(header, this, 4);
        header.addView(Ui.caption(this, LedgerFormatter.starterNote()));
        Ui.gap(header, this, 12);

        Button explainerButton = Ui.secondaryButton(this, "How credits work");
        explainerButton.setOnClickListener(v -> showExplainer());
        header.addView(explainerButton);
        Ui.gap(header, this, 16);

        List<LedgerEntry> entries = wallet.getEntries();
        if (entries == null || entries.isEmpty()) {
            header.addView(Ui.body(this, "No activity yet."));
        } else {
            header.addView(Ui.headline(this, "History"));
            Ui.gap(header, this, 8);
        }
        return header;
    }

    /** Whole days until the soonest expiry, or null when nothing is expiring. */
    private static Integer daysUntilExpiry(Wallet wallet, long nowMs) {
        Long nextExpiryMs = wallet.getNextExpiryMs();
        if (nextExpiryMs == null) {
            return null;
        }
        return (int) ((nextExpiryMs - nowMs) / DAY_MS);
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
