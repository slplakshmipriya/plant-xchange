package com.gardenswap.test.wantlist;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.R;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.WantItem;
import com.gardenswap.test.listings.ListingDetailActivity;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.ListingDetailLogic;

import java.util.List;

/**
 * Want-list UI (AND-030): add/remove wanted varieties (autosuggest), matched
 * listings section with match badges, and per-category push opt-in.
 *
 * <p>Push toggles persist locally in the mock phase; they wire to
 * {@code PUT /v1/users/me/notification-prefs} at the integration checkpoint.
 */
public class WantListActivity extends AppCompatActivity {

    /** Autosuggest pool for the variety field (static in the mock phase). */
    private static final String[] VARIETY_SUGGESTIONS = {
            "Cherokee Purple tomato", "Roma tomato", "Genovese basil", "Thai basil",
            "Meyer lemons", "Jalapeño pepper", "Bell pepper", "Zucchini",
            "Cucumber", "Kale", "Spinach", "Carrots", "Rosemary", "Mint",
            "Fig tree", "Peach tree", "Pomegranate",
    };

    private static final String PREFS = "wantlist_prefs";

    private LinearLayout wantBubbles;
    private LinearLayout matchRows;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.column(this, 20);

        TextView title = Ui.label(this, "My want-list");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        Ui.gap(root, this, 8);

        // Want-list strip: a scrollable row of item bubbles with a fixed
        // "+ Add" button pinned at the right end.
        root.addView(Ui.label(this, "Wanted items"));
        Ui.gap(root, this, 4);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);

        HorizontalScrollView bubbleScroll = new HorizontalScrollView(this);
        bubbleScroll.setHorizontalScrollBarEnabled(false);
        bubbleScroll.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        wantBubbles = new LinearLayout(this);
        wantBubbles.setOrientation(LinearLayout.HORIZONTAL);
        wantBubbles.setGravity(Gravity.CENTER_VERTICAL);
        bubbleScroll.addView(wantBubbles,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        strip.addView(bubbleScroll);

        Button addButton = Ui.button(this, "+ Add");
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.leftMargin = Ui.dp(this, 8);
        addButton.setLayoutParams(addParams);
        addButton.setOnClickListener(v -> showAddDialog());
        strip.addView(addButton);

        root.addView(strip);
        Ui.gap(root, this, 12);

        root.addView(Ui.label(this, "Matches near you"));
        matchRows = new LinearLayout(this);
        matchRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(matchRows);
        Ui.gap(root, this, 12);

        root.addView(Ui.label(this, "Notify me about"));
        addPushToggle(root, "pref_harvest_alerts", "Harvest alerts", true);
        addPushToggle(root, "pref_want_matches", "Want-list matches", true);
        addPushToggle(root, "pref_expiry_nudges", "Expiry nudges", false);
        Ui.gap(root, this, 8);

        statusView = Ui.status(this);
        root.addView(statusView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void addPushToggle(LinearLayout root, final String key, String label, boolean defValue) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(label);
        checkBox.setChecked(prefs.getBoolean(key, defValue));
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(key, isChecked).apply());
        root.addView(checkBox);
    }

    /** "+ Add" dialog: autosuggest variety input for a new wanted item. */
    private void showAddDialog() {
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setHint("e.g. Cherokee Purple tomato");
        input.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, VARIETY_SUGGESTIONS));
        input.setThreshold(1);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        container.setPadding(pad, Ui.dp(this, 8), pad, 0);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Add to want-list")
                .setView(container)
                .setPositiveButton("Add", (dialog, which) ->
                        addWant(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addWant(String variety) {
        if (variety.trim().isEmpty()) {
            Toast.makeText(this, "Enter a variety", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiProvider.get().addWant(variety, new GardenSwapApi.Callback<WantItem>() {
            @Override
            public void onSuccess(WantItem result) {
                refresh();
            }

            @Override
            public void onError(ApiException error) {
                statusView.setText("Could not add: " + error.getMessage());
            }
        });
    }

    private void refresh() {
        GardenSwapApi api = ApiProvider.get();
        api.getWantList(new GardenSwapApi.Callback<List<WantItem>>() {
            @Override
            public void onSuccess(List<WantItem> result) {
                renderWants(result);
            }

            @Override
            public void onError(ApiException error) {
                statusView.setText("Could not load want-list: " + error.getMessage());
            }
        });
        api.getMatches(new GardenSwapApi.Callback<List<Listing>>() {
            @Override
            public void onSuccess(List<Listing> result) {
                renderMatches(result);
            }

            @Override
            public void onError(ApiException error) {
                statusView.setText("Could not load matches: " + error.getMessage());
            }
        });
    }

    private void renderWants(List<WantItem> wants) {
        wantBubbles.removeAllViews();
        if (wants.isEmpty()) {
            wantBubbles.addView(Ui.caption(this, "Nothing wanted yet — tap + Add."));
            return;
        }
        for (WantItem want : wants) {
            wantBubbles.addView(wantBubble(want));
        }
    }

    /** One wanted item as a bubble: the variety plus a × to remove it. */
    private LinearLayout wantBubble(WantItem want) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setBackgroundResource(R.drawable.chip_bg);
        int hPad = Ui.dp(this, 12);
        int vPad = Ui.dp(this, 8);
        bubble.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = Ui.dp(this, 8);
        bubble.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setText(want.getVariety());
        name.setTextSize(14);
        bubble.addView(name);

        TextView remove = new TextView(this);
        remove.setText("  ×");
        remove.setTextSize(16);
        remove.setClickable(true);
        remove.setFocusable(true);
        remove.setPadding(Ui.dp(this, 4), 0, 0, 0);
        remove.setOnClickListener(v -> removeWant(want.getId()));
        bubble.addView(remove);
        return bubble;
    }

    private void removeWant(String wantId) {
        ApiProvider.get().removeWant(wantId, new GardenSwapApi.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                refresh();
            }

            @Override
            public void onError(ApiException error) {
                statusView.setText("Could not remove: " + error.getMessage());
            }
        });
    }

    private void renderMatches(List<Listing> matches) {
        matchRows.removeAllViews();
        if (matches.isEmpty()) {
            matchRows.addView(Ui.label(this, "No matches yet. Add varieties to get notified."));
            return;
        }
        for (Listing listing : matches) {
            Button row = new Button(this);
            String variety = listing.getVariety() == null ? "Listing" : listing.getVariety();
            row.setText("🌱 Match: " + variety + " · "
                    + listing.getCreditCost() + " cr · "
                    + ListingDetailLogic.formatCountdown(
                            listing.getExpiresAtMs(), System.currentTimeMillis()));
            row.setOnClickListener(v -> ListingDetailActivity.open(this, listing.getId()));
            matchRows.addView(row);
        }
    }
}
