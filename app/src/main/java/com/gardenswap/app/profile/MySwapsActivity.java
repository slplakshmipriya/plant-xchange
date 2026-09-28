package com.gardenswap.app.profile;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.Swap;
import com.gardenswap.app.ui.Nav;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.NavRouter;
import com.gardenswap.app.util.SwapLogic;

import java.util.List;
import java.util.Locale;

/**
 * My swaps screen (UID-023).
 *
 * <p>Active swaps first, then completed/history — each row shows the
 * counterparty, the listing title, and a status chip. Data is
 * {@link SwapSamples} (placeholder) until the swap-history API lands.
 */
public class MySwapsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sticky brand bar, same as Explore; the screen title scrolls below.
        LinearLayout header = Ui.column(this, 24);
        header.addView(Ui.appTitleRow(this));
        int pad = Ui.dp(this, 24);
        header.setPadding(pad, pad, pad, 0);

        LinearLayout root = Ui.column(this, 24);
        root.setPadding(pad, 0, pad, pad);
        root.addView(Ui.headline(this, "My swaps"));
        Ui.gap(root, this, 16);

        SwapLogic.Partition partition =
                SwapLogic.partition(SwapSamples.swaps());
        addSection(root, "Active", partition.active(), true);
        Ui.gap(root, this, 16);
        addSection(root, "Completed", partition.completed(), false);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(Ui.stickyHeaderScreen(this, header, scroll));
        Nav.attach(this, NavRouter.Tab.SWAPS);
    }

    private void addSection(LinearLayout root, String heading,
                            List<Swap> swaps, boolean active) {
        root.addView(Ui.eyebrow(this, heading));
        Ui.gap(root, this, 8);
        if (swaps.isEmpty()) {
            root.addView(Ui.body(this, active
                    ? "No active swaps yet."
                    : "Nothing here yet — completed swaps will appear here."));
            return;
        }
        for (Swap swap : swaps) {
            root.addView(swapRow(swap, active));
            Ui.gap(root, this, 8);
        }
    }

    private LinearLayout swapRow(Swap swap, boolean active) {
        LinearLayout card = Ui.card(this);
        if (!active) {
            card.setAlpha(0.65f); // muted history treatment
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(textParams);
        String title = swap.getListingTitle() == null
                ? "Swap" : swap.getListingTitle();
        text.addView(Ui.title(this, title));
        String counterparty = swap.getCounterparty() == null
                ? "Unknown gardener" : swap.getCounterparty();
        text.addView(Ui.caption(this, "with " + counterparty));
        row.addView(text);

        TextView chip = Ui.chip(this, statusLabel(swap));
        Ui.setChipSelected(this, chip, active);
        row.addView(chip);

        card.addView(row);
        return card;
    }

    private String statusLabel(Swap swap) {
        if (swap.getStatus() == null) {
            return "Requested";
        }
        String label = swap.getStatus().name().toLowerCase(Locale.US)
                .replace('_', ' ');
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
