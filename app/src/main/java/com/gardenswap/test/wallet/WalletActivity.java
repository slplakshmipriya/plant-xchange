package com.gardenswap.test.wallet;

import android.app.AlertDialog;
import android.graphics.Typeface;
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

import com.gardenswap.test.R;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.CreditExpiry;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.LedgerEntry;
import com.gardenswap.test.api.Wallet;
import com.gardenswap.test.ui.LedgerAdapter;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.LedgerFormatter;
import com.gardenswap.test.util.WalletLogic;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private LinearLayout expirySection;

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
        loadCreditExpiry(now);
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
        TextView balance = Ui.display(this, String.valueOf(wallet.getBalance()));
        Ui.textColor(this, balance, R.color.garden_turquoise);
        card.addView(balance);
        TextView creditsEyebrow = Ui.eyebrow(this, "credits");
        Ui.textColor(this, creditsEyebrow, R.color.garden_turquoise);
        card.addView(creditsEyebrow);
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

        // Credit expiry section (r2 contract: GET /v1/users/me/credit-expiry).
        // Empty placeholder until the call succeeds; on error it stays empty,
        // so the section is hidden silently (backend may 404 before it lands).
        expirySection = Ui.column(this, 0);
        header.addView(expirySection);

        header.addView(Ui.caption(this,
                LedgerFormatter.weeklyCapLine(wallet.getEarnedThisWeek())));
        Ui.gap(header, this, 4);
        header.addView(Ui.caption(this, LedgerFormatter.starterNote()));
        Ui.gap(header, this, 4);
        header.addView(Ui.caption(this, "You can earn up to 10 credits per week."));
        Ui.gap(header, this, 4);
        header.addView(Ui.caption(this,
                "New accounts (under 14 days) can claim up to 5 listings per week."));
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

    /**
     * Fills the "Credit expiry" section from GET /v1/users/me/credit-expiry.
     * The endpoint 404s until the backend lands (PRD parity, r2 contract) —
     * on error the section is hidden silently and nothing else changes.
     */
    private void loadCreditExpiry(long nowMs) {
        final LinearLayout section = expirySection;
        if (section == null) {
            return;
        }
        ApiProvider.get().getCreditExpiry(new GardenSwapApi.Callback<CreditExpiry>() {
            @Override
            public void onSuccess(CreditExpiry expiry) {
                if (expiry != null) {
                    renderCreditExpiry(section, expiry, nowMs);
                }
            }

            @Override
            public void onError(ApiException e) {
                // Silently hide: the placeholder stays empty.
            }
        });
    }

    /** Renders one line per expiring chunk plus the season end date. */
    private void renderCreditExpiry(LinearLayout section, CreditExpiry expiry, long nowMs) {
        section.addView(Ui.headline(this, "Credit expiry"));
        Ui.gap(section, this, 8);
        List<CreditExpiry.ExpiringChunk> chunks = expiry.getExpiring();
        if (chunks != null) {
            for (CreditExpiry.ExpiringChunk chunk : chunks) {
                int credits = chunk.getCredits();
                int days = Math.max(0, (int) ((chunk.getExpiresAtMs() - nowMs) / DAY_MS));
                String noun = credits == 1 ? "credit expires" : "credits expire";
                String line = credits + " " + noun
                        + (days == 0 ? " today" : " in " + days + " days");
                TextView row = Ui.body(this, line);
                if (days <= 7) {
                    // No warning text style in Ui — emphasize with bold + red.
                    row.setTypeface(Typeface.DEFAULT_BOLD);
                    Ui.textColor(this, row, R.color.garden_red);
                }
                section.addView(row);
                Ui.gap(section, this, 4);
            }
        }
        if (expiry.getSeasonEndMs() > 0) {
            String seasonEnd = new SimpleDateFormat("MMM d", Locale.getDefault())
                    .format(new Date(expiry.getSeasonEndMs()));
            section.addView(Ui.caption(this, "Season ends " + seasonEnd));
        }
        Ui.gap(section, this, 12);
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
