package com.gardenswap.test.wantlist;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.WantItem;
import com.gardenswap.test.listings.ListingDetailActivity;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.ListingDetailLogic;

import java.util.ArrayList;
import java.util.List;

/**
 * Want-list UI (AND-030): add/remove wanted varieties (autosuggest), matched
 * listings section with match badges, and per-category push opt-in.
 *
 * <p>Push toggles persist locally in the mock phase; they wire to
 * {@code PUT /v1/users/me/notification-prefs} at the integration checkpoint.
 */
public class WantListActivity extends AppCompatActivity {

    private static final String PREFS = "wantlist_prefs";

    private WantStripView wantStrip;
    private LinearLayout matchRows;
    private TextView statusView;
    /** Last-loaded want-list; passed to the add dialog for duplicate filtering. */
    private List<WantItem> currentWants = new ArrayList<>();

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
        wantStrip = new WantStripView(this);
        wantStrip.setOnAddClickListener(v ->
                WantDialogs.showAddDialog(this, currentWants, this::refresh));
        wantStrip.setOnRemoveListener(this::removeWant);
        root.addView(wantStrip);
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

    private void refresh() {
        GardenSwapApi api = ApiProvider.get();
        api.getWantList(new GardenSwapApi.Callback<List<WantItem>>() {
            @Override
            public void onSuccess(List<WantItem> result) {
                currentWants = result != null ? result : new ArrayList<>();
                wantStrip.setWants(result);
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
            String variety = listing.getVariety() == null ? "Listing" : listing.getVariety();
            Button row = Ui.primaryButton(this,
                    "🌱 Match: " + variety + " · "
                    + listing.getCreditCost() + " cr · "
                    + ListingDetailLogic.formatCountdown(
                            listing.getExpiresAtMs(), System.currentTimeMillis()));
            row.setOnClickListener(v -> ListingDetailActivity.open(this, listing.getId()));
            matchRows.addView(row);
        }
    }
}
