package com.gardenswap.test.wantlist;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.WantItem;
import com.gardenswap.test.ui.Ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared "+ Add" dialog for the want-list: an autosuggest variety input.
 * Used by the want-list screen and the Explore tab.
 *
 * <p>Duplicates are suppressed at three levels: varieties already in the
 * user's want-list are hidden from the suggestions, the typed input is
 * checked before the API call, and a 409 from the backend (race) shows the
 * same "already in your want list" message instead of a raw error.
 */
public final class WantDialogs {

    private static final String[] VARIETY_SUGGESTIONS = {
            "Cherokee Purple tomato", "Roma tomato", "Genovese basil", "Thai basil",
            "Meyer lemons", "Jalapeño pepper", "Bell pepper", "Zucchini",
            "Cucumber", "Kale", "Spinach", "Carrots", "Rosemary", "Mint",
            "Fig tree", "Peach tree", "Pomegranate",
    };

    private WantDialogs() {
        // utility class
    }

    /** Normalizes a variety for duplicate comparison (matches backend). */
    static String normalize(String variety) {
        return variety == null ? "" : variety.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Shows the dialog; {@code existing} is the user's current want-list
     * (may be null/empty on first load), {@code onAdded} runs after a
     * successful add.
     */
    public static void showAddDialog(
            Activity activity, List<WantItem> existing, Runnable onAdded) {
        Set<String> owned = new HashSet<>();
        if (existing != null) {
            for (WantItem item : existing) {
                owned.add(normalize(item.getVariety()));
            }
        }

        // Hide suggestions the user already wants.
        List<String> suggestions = new ArrayList<>();
        for (String s : VARIETY_SUGGESTIONS) {
            if (!owned.contains(normalize(s))) {
                suggestions.add(s);
            }
        }

        AutoCompleteTextView input = new AutoCompleteTextView(activity);
        input.setHint("e.g. Cherokee Purple tomato");
        input.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, suggestions));
        input.setThreshold(1);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 20);
        container.setPadding(pad, Ui.dp(activity, 8), pad, 0);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(activity)
                .setTitle("Add to want-list")
                .setView(container)
                .setPositiveButton("Add", (dialog, which) ->
                        addWant(activity, input.getText().toString(), owned, onAdded))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void addWant(
            Activity activity, String variety, Set<String> owned, Runnable onAdded) {
        if (variety.trim().isEmpty()) {
            Toast.makeText(activity, "Enter a variety", Toast.LENGTH_SHORT).show();
            return;
        }
        if (owned.contains(normalize(variety))) {
            Toast.makeText(activity, "Already in your want list", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiProvider.get().addWant(variety, new GardenSwapApi.Callback<WantItem>() {
            @Override
            public void onSuccess(WantItem result) {
                onAdded.run();
            }

            @Override
            public void onError(ApiException error) {
                if ("want_duplicate".equals(error.getCode())) {
                    Toast.makeText(activity, "Already in your want list",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Could not add: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
