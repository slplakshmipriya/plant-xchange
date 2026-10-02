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

/**
 * Shared "+ Add" dialog for the want-list: an autosuggest variety input.
 * Used by the want-list screen and the Explore tab.
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

    /** Shows the dialog; {@code onAdded} runs after a successful add. */
    public static void showAddDialog(Activity activity, Runnable onAdded) {
        AutoCompleteTextView input = new AutoCompleteTextView(activity);
        input.setHint("e.g. Cherokee Purple tomato");
        input.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, VARIETY_SUGGESTIONS));
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
                        addWant(activity, input.getText().toString(), onAdded))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void addWant(Activity activity, String variety, Runnable onAdded) {
        if (variety.trim().isEmpty()) {
            Toast.makeText(activity, "Enter a variety", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiProvider.get().addWant(variety, new GardenSwapApi.Callback<WantItem>() {
            @Override
            public void onSuccess(WantItem result) {
                onAdded.run();
            }

            @Override
            public void onError(ApiException error) {
                Toast.makeText(activity, "Could not add: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
