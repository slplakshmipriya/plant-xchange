package com.gardenswap.app.listings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.Listing;
import com.gardenswap.app.api.ListingInput;
import com.gardenswap.app.api.ListingType;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.CreateListingValidator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Create-listing flow (AND-020): type picker → photos → variety → quantity →
 * credit stepper (1–3) → pickup window → expiry → spray disclosure (mandatory)
 * → publish. Draft autosaves to SharedPreferences.
 *
 * <p>Photos are placeholder URIs in the mock phase; real capture/upload lands
 * with the photo pipeline integration (API-022).
 */
public class CreateListingActivity extends AppCompatActivity {

    private static final String PREFS = "create_listing_draft";
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm";

    private final List<Button> typeButtons = new ArrayList<>();
    private ListingType selectedType = ListingType.SEEDLING;
    private final List<String> photoUris = new ArrayList<>();
    private TextView photoCountView;
    private EditText varietyInput;
    private EditText quantityInput;
    private EditText unitInput;
    private LinearLayout unitPresetRow;
    private TextView creditView;
    private int creditCost = 1;
    private boolean freeListing = false;
    private LinearLayout creditRow;
    private EditText pickupStartInput;
    private EditText pickupEndInput;
    private EditText expiryDaysInput;
    private EditText sprayInput;
    private TextView geoView;
    private Double geoLat;
    private Double geoLon;
    private TextView statusView;
    private android.widget.CheckBox freeToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 20);
        TextView title = Ui.label(this, "New listing");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, "Type"));
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        for (ListingType type : ListingType.values()) {
            Button button = new Button(this);
            button.setText(capitalize(type.name()));
            button.setOnClickListener(v -> selectType(type));
            typeRow.addView(button, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            typeButtons.add(button);
        }
        root.addView(typeRow);
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, "Photos (required)"));
        photoCountView = Ui.label(this, "0 photos");
        Button addPhotoButton = Ui.button(this, "Add photo");
        addPhotoButton.setOnClickListener(v -> {
            // Mock phase: record a placeholder URI. Real camera/gallery +
            // upload lands with the API-022 integration.
            photoUris.add("content://mock/photo/" + System.currentTimeMillis());
            photoCountView.setText(photoUris.size() + " photo(s)");
        });
        root.addView(photoCountView);
        root.addView(addPhotoButton);
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, "Variety"));
        varietyInput = Ui.input(this, "e.g. Cherokee Purple tomato", InputType.TYPE_CLASS_TEXT);
        root.addView(varietyInput);

        root.addView(Ui.label(this, "Quantity (optional)"));
        quantityInput = Ui.input(this, "e.g. 6",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(quantityInput);

        root.addView(Ui.label(this, "Unit (optional)"));
        unitInput = Ui.input(this, "e.g. starts, lbs, bags", InputType.TYPE_CLASS_TEXT);
        root.addView(unitInput);
        LinearLayout unitPresetRow = new LinearLayout(this);
        unitPresetRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String preset : new String[]{"lbs", "bags", "each"}) {
            Button presetButton = new Button(this);
            presetButton.setText(preset);
            presetButton.setOnClickListener(v -> unitInput.setText(preset));
            unitPresetRow.addView(presetButton, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(unitPresetRow);
        this.unitPresetRow = unitPresetRow;
        Ui.gap(root, this, 8);

        android.widget.CheckBox freeToggle = new android.widget.CheckBox(this);
        freeToggle.setText("FREE — no credits needed (prominent)");
        freeToggle.setTextSize(16);
        this.freeToggle = freeToggle;
        freeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            freeListing = isChecked;
            setCreditRowEnabled(!isChecked);
            if (isChecked) {
                creditView.setText("FREE");
            } else {
                setCredit(creditCost);
            }
        });
        root.addView(freeToggle);
        Ui.gap(root, this, 4);

        root.addView(Ui.label(this, "Credit cost (1–3)"));
        LinearLayout creditRow = new LinearLayout(this);
        creditRow.setOrientation(LinearLayout.HORIZONTAL);
        Button minusButton = new Button(this);
        minusButton.setText("−");
        minusButton.setOnClickListener(v -> setCredit(creditCost - 1));
        creditView = Ui.label(this, "1 credit");
        creditView.setGravity(Gravity.CENTER);
        Button plusButton = new Button(this);
        plusButton.setText("+");
        plusButton.setOnClickListener(v -> setCredit(creditCost + 1));
        creditRow.addView(minusButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        creditRow.addView(creditView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        creditRow.addView(plusButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(creditRow);
        this.creditRow = creditRow;
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, "Pickup window (optional, " + DATE_PATTERN + ")"));
        pickupStartInput = Ui.input(this, "Start, e.g. 2026-10-05 09:00", InputType.TYPE_CLASS_TEXT);
        pickupEndInput = Ui.input(this, "End, e.g. 2026-10-05 12:00", InputType.TYPE_CLASS_TEXT);
        root.addView(pickupStartInput);
        root.addView(pickupEndInput);
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, "Expires in (days, optional)"));
        expiryDaysInput = Ui.input(this, "e.g. 7", InputType.TYPE_CLASS_NUMBER);
        root.addView(expiryDaysInput);
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, "Spray disclosure (required)"));
        sprayInput = Ui.input(this, "Pesticides used, or \"none\"", InputType.TYPE_CLASS_TEXT);
        root.addView(sprayInput);
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, "Location"));
        geoView = Ui.label(this, "No location set");
        Button geoButton = Ui.button(this, "Use approximate location");
        geoButton.setOnClickListener(v -> {
            // Mock phase: coarse fixed coords. Real location lands with
            // the device-permission work; the backend fuzzes anyway.
            geoLat = 33.42;
            geoLon = -111.83;
            geoView.setText("Approximate location set");
        });
        root.addView(geoView);
        root.addView(geoButton);
        Ui.gap(root, this, 12);

        statusView = Ui.status(this);
        root.addView(statusView);
        Ui.gap(root, this, 4);

        Button publishButton = Ui.button(this, "Publish listing");
        publishButton.setOnClickListener(v -> publish());
        root.addView(publishButton);

        setContentView(root);
        restoreDraft();
        selectType(selectedType);
        setCredit(creditCost);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveDraft();
    }

    private void selectType(ListingType type) {
        selectedType = type;
        for (int i = 0; i < typeButtons.size(); i++) {
            typeButtons.get(i).setSelected(ListingType.values()[i] == type);
            typeButtons.get(i).setAlpha(ListingType.values()[i] == type ? 1f : 0.5f);
        }
        // Harvest listings get unit quick-picks (lbs/bags/each, AND-040).
        if (unitPresetRow != null) {
            unitPresetRow.setVisibility(
                    type == ListingType.HARVEST ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void setCreditRowEnabled(boolean enabled) {
        if (creditRow == null) {
            return;
        }
        for (int i = 0; i < creditRow.getChildCount(); i++) {
            creditRow.getChildAt(i).setEnabled(enabled);
        }
        creditRow.setAlpha(enabled ? 1f : 0.5f);
    }

    private void setCredit(int value) {
        creditCost = Math.max(1, Math.min(3, value));
        creditView.setText(creditCost + (creditCost == 1 ? " credit" : " credits"));
    }

    private void publish() {
        CreateListingValidator.Draft draft = collectDraft();
        List<String> errors = CreateListingValidator.validate(draft, System.currentTimeMillis());
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Fix these:");
            for (String error : errors) {
                message.append("\n• ").append(error);
            }
            statusView.setText(message.toString());
            return;
        }
        statusView.setText("Publishing…");
        ListingInput.Builder builder = ListingInput.builder(selectedType)
                .photos(new ArrayList<>(photoUris))
                .variety(varietyInput.getText().toString().trim())
                .unit(blankToNull(unitInput.getText().toString()))
                .creditCost(creditCost)
                .pickupWindow(draft.pickupStartMs, draft.pickupEndMs)
                .expiresAtMs(draft.expiresAtMs)
                .geo(geoLat, geoLon)
                .sprayDisclosure(sprayInput.getText().toString().trim())
                .free(freeListing);
        try {
            builder.quantity(CreateListingValidator.parseQuantity(quantityInput.getText().toString()));
        } catch (NumberFormatException e) {
            statusView.setText("Quantity must be a number.");
            return;
        }
        GardenSwapApi.Callback<Listing> callback = new GardenSwapApi.Callback<Listing>() {
            @Override
            public void onSuccess(Listing result) {
                clearDraft();
                Toast.makeText(CreateListingActivity.this,
                        "Listing published", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(ApiException error) {
                statusView.setText("Publish failed: " + error.getMessage());
            }
        };
        ApiProvider.get().createListing(builder.build(), callback);
    }

    private CreateListingValidator.Draft collectDraft() {
        CreateListingValidator.Draft draft = new CreateListingValidator.Draft();
        draft.type = selectedType.getWireValue();
        draft.photos = new ArrayList<>(photoUris);
        draft.variety = varietyInput.getText().toString();
        draft.quantityText = quantityInput.getText().toString();
        draft.unit = unitInput.getText().toString();
        draft.creditCost = creditCost;
        draft.pickupStartText = pickupStartInput.getText().toString();
        draft.pickupEndText = pickupEndInput.getText().toString();
        draft.pickupStartMs = parseDate(pickupStartInput.getText().toString());
        draft.pickupEndMs = parseDate(pickupEndInput.getText().toString());
        draft.expiresAtMs = parseExpiryDays(expiryDaysInput.getText().toString());
        draft.sprayDisclosure = sprayInput.getText().toString();
        return draft;
    }

    private Long parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN, Locale.US);
            format.setLenient(false);
            return format.parse(text.trim()).getTime();
        } catch (ParseException e) {
            return null;
        }
    }

    private Long parseExpiryDays(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            int days = Integer.parseInt(text.trim());
            return days > 0 ? System.currentTimeMillis() + days * 86_400_000L : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String capitalize(String value) {
        String lower = value.toLowerCase(Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void saveDraft() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("type", selectedType.name())
                .putString("variety", varietyInput.getText().toString())
                .putString("quantity", quantityInput.getText().toString())
                .putString("unit", unitInput.getText().toString())
                .putInt("credit", creditCost)
                .putString("pickupStart", pickupStartInput.getText().toString())
                .putString("pickupEnd", pickupEndInput.getText().toString())
                .putString("expiryDays", expiryDaysInput.getText().toString())
                .putString("spray", sprayInput.getText().toString())
                .putBoolean("free", freeToggle.isChecked())
                .apply();
    }

    private void restoreDraft() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            selectedType = ListingType.valueOf(prefs.getString("type", ListingType.SEEDLING.name()));
        } catch (IllegalArgumentException e) {
            selectedType = ListingType.SEEDLING;
        }
        varietyInput.setText(prefs.getString("variety", ""));
        quantityInput.setText(prefs.getString("quantity", ""));
        unitInput.setText(prefs.getString("unit", ""));
        setCredit(prefs.getInt("credit", 1));
        pickupStartInput.setText(prefs.getString("pickupStart", ""));
        pickupEndInput.setText(prefs.getString("pickupEnd", ""));
        expiryDaysInput.setText(prefs.getString("expiryDays", ""));
        sprayInput.setText(prefs.getString("spray", ""));
        freeToggle.setChecked(prefs.getBoolean("free", false));
    }

    private void clearDraft() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
    }
}
