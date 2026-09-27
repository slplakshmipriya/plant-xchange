package com.gardenswap.app.wantlist;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.Listing;
import com.gardenswap.app.api.WantItem;
import com.gardenswap.app.listings.ListingDetailActivity;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.ListingDetailLogic;

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

    private LinearLayout wantRows;
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

        root.addView(Ui.label(this, "Add a variety you want"));
        AutoCompleteTextView varietyInput = new AutoCompleteTextView(this);
        varietyInput.setHint("e.g. Cherokee Purple tomato");
        varietyInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, VARIETY_SUGGESTIONS));
        varietyInput.setThreshold(1);
        varietyInput.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        Button addButton = Ui.button(this, "Add to want-list");
        addButton.setOnClickListener(v -> addWant(varietyInput.getText().toString(), varietyInput));
        root.addView(varietyInput);
        root.addView(addButton);
        Ui.gap(root, this, 12);

        root.addView(Ui.label(this, "Wanted varieties"));
        wantRows = new LinearLayout(this);
        wantRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(wantRows);
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

        setContentView(root);
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

    private void addWant(String variety, AutoCompleteTextView input) {
        if (variety.trim().isEmpty()) {
            Toast.makeText(this, "Enter a variety", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiProvider.get().addWant(variety, new GardenSwapApi.Callback<WantItem>() {
            @Override
            public void onSuccess(WantItem result) {
                input.setText("");
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
        wantRows.removeAllViews();
        if (wants.isEmpty()) {
            wantRows.addView(Ui.label(this, "Nothing wanted yet — add varieties above."));
            return;
        }
        for (WantItem want : wants) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = Ui.label(this, want.getVariety());
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button remove = new Button(this);
            remove.setText("Remove");
            remove.setOnClickListener(v -> removeWant(want.getId()));
            row.addView(name);
            row.addView(remove);
            wantRows.addView(row);
        }
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
