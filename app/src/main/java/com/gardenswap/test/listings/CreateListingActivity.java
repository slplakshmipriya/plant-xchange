package com.gardenswap.test.listings;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.ListingInput;
import com.gardenswap.test.api.ListingType;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.CreateListingValidator;
import com.gardenswap.test.util.CreditStepperLogic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Create-listing flow (AND-020), restyled for UID-014: type picker → photos →
 * variety → quantity → credit stepper (1–3) → pickup window → expiry → spray
 * disclosure (mandatory) → visit rules → publish. Draft autosaves to
 * SharedPreferences.
 *
 * <p>Photos are picked from the gallery and uploaded through
 * {@code /v1/uploads} (sign → PUT → finalize); {@link #photoUris} holds the
 * returned absolute public URLs, which is what the backend expects.
 * Validation is owned entirely by {@link CreateListingValidator}; this
 * activity only collects input.
 */
public class CreateListingActivity extends AppCompatActivity {

    private static final String PREFS = "create_listing_draft";
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm";

    /** Variety autosuggest pool, shared with the want-list (AND-124). */
    private static final String[] VARIETY_SUGGESTIONS = {
            "Cherokee Purple tomato", "Roma tomato", "Genovese basil", "Thai basil",
            "Meyer lemons", "Jalapeño pepper", "Bell pepper", "Zucchini",
            "Cucumber", "Kale", "Spinach", "Carrots", "Rosemary", "Mint",
            "Fig tree", "Peach tree", "Pomegranate",
    };

    private final List<TextView> typeChips = new ArrayList<>();
    private ListingType selectedType = ListingType.SEEDLING;
    private final List<String> photoUris = new ArrayList<>();
    private TextView photoCountView;
    /** Gallery picker (multi-select); each pick is uploaded, see {@link #uploadPhotos}. */
    private ActivityResultLauncher<String> photoPicker;
    /** In-flight uploads; publish is blocked while this is non-zero. */
    private int photosUploading = 0;
    private EditText varietyInput;
    private EditText quantityInput;
    private EditText unitInput;
    private LinearLayout unitPresetRow;
    private LinearLayout potAgeSection;
    private EditText potSizeInput;
    private EditText plantAgeInput;
    private TextView creditView;
    private int creditCost = 1;
    private boolean freeListing = false;
    private TextView freeChip;
    private LinearLayout creditRow;
    private EditText pickupStartInput;
    private EditText pickupEndInput;
    private EditText expiryDaysInput;
    private TextView sprayNoneChip;
    private TextView sprayUsedChip;
    private EditText sprayInput;
    private EditText visitRulesInput;
    private TextView geoView;
    private Double geoLat;
    private Double geoLon;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 20);
        TextView title = Ui.headline(this, "New listing");
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        root.addView(Ui.caption(this, "Share seedlings, harvest, or pick-your-own with neighbors."));
        Ui.gap(root, this, 8);

        // ---- Type ----
        root.addView(Ui.eyebrow(this, "Type"));
        Ui.gap(root, this, 4);
        LinearLayout typeCard = Ui.card(this);
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        for (ListingType type : ListingType.values()) {
            TextView chip = Ui.chip(this, capitalize(type.name()));
            chip.setOnClickListener(v -> selectType(type));
            typeRow.addView(chip, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            typeChips.add(chip);
        }
        typeCard.addView(typeRow);
        root.addView(typeCard);
        Ui.gap(root, this, 12);

        // ---- Photos (014-T1) ----
        // Real gallery picker (multi-select). Each picked photo is uploaded
        // through /v1/uploads and only the returned absolute public URL is
        // stored in photoUris — the backend rejects anything that isn't http(s).
        photoPicker = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        uploadPhotos(uris);
                    }
                });
        root.addView(Ui.eyebrow(this, "Photos · required"));
        Ui.gap(root, this, 4);
        LinearLayout photoCard = Ui.card(this);
        photoCountView = Ui.caption(this, "No photos yet — add at least one.");
        photoCard.addView(photoCountView);
        Ui.gap(photoCard, this, 8);
        Button addPhotoButton = Ui.secondaryButton(this, "Add photos");
        addPhotoButton.setOnClickListener(v -> photoPicker.launch("image/*"));
        photoCard.addView(addPhotoButton);
        root.addView(photoCard);
        Ui.gap(root, this, 12);

        // ---- Variety + quantity/unit (014-T2) ----
        // Autosuggest (AND-124): same pool as the want-list, threshold 2.
        // Free text is still allowed — suggestions are a convenience.
        root.addView(Ui.eyebrow(this, "Variety"));
        Ui.gap(root, this, 4);
        AutoCompleteTextView varietyAuto = new AutoCompleteTextView(this);
        varietyAuto.setHint("e.g. Cherokee Purple tomato");
        varietyAuto.setInputType(InputType.TYPE_CLASS_TEXT);
        varietyAuto.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        varietyAuto.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, VARIETY_SUGGESTIONS));
        varietyAuto.setThreshold(2);
        varietyInput = varietyAuto;
        root.addView(varietyInput);
        Ui.gap(root, this, 8);

        root.addView(Ui.eyebrow(this, "Quantity · optional"));
        Ui.gap(root, this, 4);
        quantityInput = Ui.input(this, "e.g. 6",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(quantityInput);
        Ui.gap(root, this, 8);

        root.addView(Ui.eyebrow(this, "Unit · optional"));
        Ui.gap(root, this, 4);
        unitInput = Ui.input(this, "e.g. starts, lbs, bags", InputType.TYPE_CLASS_TEXT);
        root.addView(unitInput);
        Ui.gap(root, this, 4);
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String preset : new String[]{"lbs", "bags", "each"}) {
            TextView presetChip = Ui.chip(this, preset);
            presetChip.setOnClickListener(v -> unitInput.setText(preset));
            presetRow.addView(presetChip);
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) presetChip.getLayoutParams();
            params.setMargins(0, 0, Ui.dp(this, 8), 0);
            presetChip.setLayoutParams(params);
        }
        root.addView(presetRow);
        this.unitPresetRow = presetRow;
        Ui.gap(root, this, 12);

        // ---- Pot size / plant age (AND-125): seedlings only ----
        potAgeSection = new LinearLayout(this);
        potAgeSection.setOrientation(LinearLayout.VERTICAL);
        potAgeSection.addView(Ui.eyebrow(this, "Pot size · optional"));
        Ui.gap(potAgeSection, this, 4);
        potSizeInput = Ui.input(this, "e.g. 4 in nursery pot", InputType.TYPE_CLASS_TEXT);
        potAgeSection.addView(potSizeInput);
        Ui.gap(potAgeSection, this, 8);
        potAgeSection.addView(Ui.eyebrow(this, "Plant age · optional"));
        Ui.gap(potAgeSection, this, 4);
        plantAgeInput = Ui.input(this, "e.g. 6 weeks", InputType.TYPE_CLASS_TEXT);
        potAgeSection.addView(plantAgeInput);
        root.addView(potAgeSection);
        Ui.gap(root, this, 12);

        // ---- Credit cost stepper (014-T3) ----
        root.addView(Ui.eyebrow(this, "Credit cost"));
        Ui.gap(root, this, 4);
        LinearLayout creditCard = Ui.card(this);
        LinearLayout stepperRow = new LinearLayout(this);
        stepperRow.setOrientation(LinearLayout.HORIZONTAL);
        stepperRow.setGravity(Gravity.CENTER_VERTICAL);
        Button minusButton = Ui.secondaryButton(this, "−");
        minusButton.setOnClickListener(v -> setCredit(
                CreditStepperLogic.decrement(creditCost,
                        CreditStepperLogic.MIN_CREDIT_COST,
                        CreditStepperLogic.MAX_CREDIT_COST)));
        creditView = Ui.title(this, "1 credit");
        creditView.setGravity(Gravity.CENTER);
        Button plusButton = Ui.secondaryButton(this, "+");
        plusButton.setOnClickListener(v -> setCredit(
                CreditStepperLogic.increment(creditCost,
                        CreditStepperLogic.MIN_CREDIT_COST,
                        CreditStepperLogic.MAX_CREDIT_COST)));
        stepperRow.addView(minusButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        stepperRow.addView(creditView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        stepperRow.addView(plusButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        creditCard.addView(stepperRow);
        Ui.gap(creditCard, this, 8);
        freeChip = Ui.chip(this, "Free listing — no credits needed");
        freeChip.setOnClickListener(v -> {
            freeListing = !freeListing;
            Ui.setChipSelected(this, freeChip, freeListing);
            setCreditRowEnabled(!freeListing);
            updateCreditLabel();
        });
        creditCard.addView(freeChip);
        creditCard.addView(Ui.caption(this, "Free listings sit on the 1-credit floor."));
        root.addView(creditCard);
        this.creditRow = stepperRow;
        Ui.gap(root, this, 12);

        // ---- Pickup window ----
        root.addView(Ui.eyebrow(this, "Pickup window · optional"));
        Ui.gap(root, this, 4);
        pickupStartInput = Ui.input(this, "Start, e.g. 2026-10-05 09:00", InputType.TYPE_CLASS_TEXT);
        pickupEndInput = Ui.input(this, "End, e.g. 2026-10-05 12:00", InputType.TYPE_CLASS_TEXT);
        root.addView(dateTimeRow(pickupStartInput));
        Ui.gap(root, this, 4);
        root.addView(dateTimeRow(pickupEndInput));
        Ui.gap(root, this, 12);

        // ---- Expiry ----
        root.addView(Ui.eyebrow(this, "Expires in · days, optional"));
        Ui.gap(root, this, 4);
        expiryDaysInput = Ui.input(this, "e.g. 7", InputType.TYPE_CLASS_NUMBER);
        root.addView(expiryDaysInput);
        Ui.gap(root, this, 12);

        // ---- Spray disclosure toggle (014-T4) ----
        root.addView(Ui.eyebrow(this, "Spray disclosure · required"));
        Ui.gap(root, this, 4);
        LinearLayout sprayCard = Ui.card(this);
        LinearLayout sprayRow = new LinearLayout(this);
        sprayRow.setOrientation(LinearLayout.HORIZONTAL);
        sprayNoneChip = Ui.chip(this, "No sprays used");
        sprayNoneChip.setOnClickListener(v -> selectSprayMode(true));
        sprayUsedChip = Ui.chip(this, "Sprays used");
        sprayUsedChip.setOnClickListener(v -> selectSprayMode(false));
        sprayRow.addView(sprayNoneChip);
        sprayRow.addView(sprayUsedChip);
        LinearLayout.LayoutParams sprayParams =
                (LinearLayout.LayoutParams) sprayNoneChip.getLayoutParams();
        if (sprayParams != null) {
            sprayParams.setMargins(0, 0, Ui.dp(this, 8), 0);
            sprayNoneChip.setLayoutParams(sprayParams);
        }
        sprayCard.addView(sprayRow);
        Ui.gap(sprayCard, this, 8);
        sprayInput = Ui.input(this, "Pesticides used, or \"none\"", InputType.TYPE_CLASS_TEXT);
        sprayCard.addView(sprayInput);
        root.addView(sprayCard);
        Ui.gap(root, this, 12);

        // ---- Visit rules (014-T5) ----
        root.addView(Ui.eyebrow(this, "Visit rules · optional"));
        Ui.gap(root, this, 4);
        visitRulesInput = Ui.input(this, "e.g. porch pickup only, weekends",
                InputType.TYPE_CLASS_TEXT);
        root.addView(visitRulesInput);
        Ui.gap(root, this, 12);

        // ---- Location ----
        root.addView(Ui.eyebrow(this, "Location"));
        Ui.gap(root, this, 4);
        LinearLayout geoCard = Ui.card(this);
        geoView = Ui.body(this, "No location set");
        geoCard.addView(geoView);
        Ui.gap(geoCard, this, 8);
        Button geoButton = Ui.secondaryButton(this, "Use approximate location");
        geoButton.setOnClickListener(v -> {
            // Mock phase: coarse fixed coords. Real location lands with
            // the device-permission work; the backend fuzzes anyway.
            geoLat = 33.42;
            geoLon = -111.83;
            geoView.setText("Approximate location set");
        });
        geoCard.addView(geoButton);
        root.addView(geoCard);
        Ui.gap(root, this, 12);

        statusView = Ui.status(this);
        root.addView(statusView);
        Ui.gap(root, this, 4);

        // ---- Publish (014-T6) ----
        Button publishButton = Ui.primaryButton(this, "Publish listing");
        publishButton.setOnClickListener(v -> publish());
        root.addView(publishButton);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.addView(root);
        setContentView(scroll);

        restoreDraft();
        selectType(selectedType);
        setCredit(creditCost);
        updatePhotoCount();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveDraft();
    }

    private void selectType(ListingType type) {
        selectedType = type;
        for (int i = 0; i < typeChips.size(); i++) {
            Ui.setChipSelected(this, typeChips.get(i), ListingType.values()[i] == type);
        }
        // Harvest listings get unit quick-picks (lbs/bags/each, AND-040).
        if (unitPresetRow != null) {
            unitPresetRow.setVisibility(
                    type == ListingType.HARVEST ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        // Pot size / plant age are seedling-relevant (AND-125).
        if (potAgeSection != null) {
            potAgeSection.setVisibility(
                    type == ListingType.SEEDLING ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void updatePhotoCount() {
        StringBuilder text = new StringBuilder();
        if (photoUris.isEmpty()) {
            text.append("No photos yet — add at least one.");
        } else {
            text.append(photoUris.size())
                    .append(photoUris.size() == 1 ? " photo added." : " photos added.");
        }
        if (photosUploading > 0) {
            text.append(" Uploading ")
                    .append(photosUploading)
                    .append(photosUploading == 1 ? " photo..." : " photos...");
        }
        photoCountView.setText(text.toString());
    }

    /**
     * Uploads each picked gallery photo through {@code /v1/uploads}
     * (sign → PUT → finalize) and stores the returned absolute public URL in
     * {@link #photoUris}. Only successfully-uploaded URLs are stored, so the
     * "at least one photo" validation always sees real http(s) URLs. Failures
     * show a message and add nothing.
     */
    private void uploadPhotos(List<Uri> uris) {
        for (Uri uri : uris) {
            photosUploading++;
            updatePhotoCount();
            new Thread(() -> {
                try {
                    byte[] bytes;
                    String contentType;
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        if (in == null) {
                            throw new IOException("Couldn't open the photo.");
                        }
                        contentType = getContentResolver().getType(uri);
                        if (contentType == null) {
                            contentType = "image/jpeg";
                        }
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                        }
                        bytes = out.toByteArray();
                    }
                    if (bytes.length > 8 * 1024 * 1024) {
                        runOnUiThread(() -> {
                            photosUploading--;
                            updatePhotoCount();
                            statusView.setText(
                                    "That photo is too large (8 MB max). Pick a smaller one.");
                        });
                        return;
                    }
                    final String ct = contentType;
                    runOnUiThread(() -> ApiProvider.get().uploadAvatar(bytes, ct,
                            new GardenSwapApi.Callback<String>() {
                                @Override
                                public void onSuccess(String publicUrl) {
                                    photoUris.add(publicUrl);
                                    photosUploading--;
                                    updatePhotoCount();
                                }

                                @Override
                                public void onError(ApiException e) {
                                    photosUploading--;
                                    updatePhotoCount();
                                    statusView.setText("Couldn't upload your photo ("
                                            + e.getCode() + "). Try again.");
                                }
                            }));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        photosUploading--;
                        updatePhotoCount();
                        statusView.setText("Couldn't read your photo. Try again.");
                    });
                }
            }).start();
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
        creditCost = CreditStepperLogic.clamp(value,
                CreditStepperLogic.MIN_CREDIT_COST,
                CreditStepperLogic.MAX_CREDIT_COST);
        updateCreditLabel();
    }

    private void updateCreditLabel() {
        if (freeListing) {
            creditView.setText("FREE");
        } else {
            creditView.setText(creditCost + (creditCost == 1 ? " credit" : " credits"));
        }
    }

    private void selectSprayMode(boolean none) {
        Ui.setChipSelected(this, sprayNoneChip, none);
        Ui.setChipSelected(this, sprayUsedChip, !none);
        if (none) {
            sprayInput.setText("none");
            sprayInput.setEnabled(false);
            sprayInput.setAlpha(0.5f);
        } else {
            if ("none".equals(sprayInput.getText().toString().trim())) {
                sprayInput.setText("");
            }
            sprayInput.setEnabled(true);
            sprayInput.setAlpha(1f);
        }
    }

    /**
     * Horizontal row: editable text field (weight 1, free typing still works)
     * + a "Pick" button that opens the date then time picker and writes the
     * result back in {@link #DATE_PATTERN} format.
     */
    private LinearLayout dateTimeRow(EditText field) {
        field.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button pick = Ui.rowButton(this, "Pick", false);
        pick.setOnClickListener(v -> showDateTimePicker(field));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(field);
        row.addView(pick);
        return row;
    }

    /** Date picker followed by time picker; writes "yyyy-MM-dd HH:mm". */
    private void showDateTimePicker(EditText target) {
        Calendar cal = Calendar.getInstance();
        try {
            Date existing = new SimpleDateFormat(DATE_PATTERN, Locale.US)
                    .parse(target.getText().toString().trim());
            if (existing != null) {
                cal.setTime(existing);
            }
        } catch (Exception ignored) {
            // Unparseable text — fall back to now.
        }
        new DatePickerDialog(this,
                (dateView, year, month, day) -> {
                    cal.set(year, month, day);
                    new TimePickerDialog(this,
                            (timeView, hour, minute) -> {
                                cal.set(Calendar.HOUR_OF_DAY, hour);
                                cal.set(Calendar.MINUTE, minute);
                                target.setText(new SimpleDateFormat(DATE_PATTERN, Locale.US)
                                        .format(cal.getTime()));
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE), true).show();
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    /**
     * Pickup window default (AND-126): when both fields are blank, pre-fill a
     * 4-day window starting now so the user sees (and can edit) the default.
     */
    private void applyPickupWindowDefault() {
        boolean startBlank = pickupStartInput.getText().toString().trim().isEmpty();
        boolean endBlank = pickupEndInput.getText().toString().trim().isEmpty();
        if (startBlank && endBlank) {
            SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN, Locale.US);
            long now = System.currentTimeMillis();
            pickupStartInput.setText(format.format(new Date(now)));
            pickupEndInput.setText(format.format(new Date(now + 4 * 86_400_000L)));
        }
    }

    /**
     * Expiry default (AND-127/128): harvest listings default to 2 days, all
     * others to 7. The hard cap (5 / 14) is enforced by
     * {@link CreateListingValidator} with an inline error.
     */
    private void applyExpiryDefault() {
        if (expiryDaysInput.getText().toString().trim().isEmpty()) {
            expiryDaysInput.setText(String.valueOf(
                    selectedType == ListingType.HARVEST ? 2 : 7));
        }
    }

    private void publish() {
        if (photosUploading > 0) {
            statusView.setText("Still uploading your photos — wait a moment, then tap Publish again.");
            return;
        }
        applyPickupWindowDefault();
        applyExpiryDefault();
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
                .potSize(blankToNull(potSizeInput.getText().toString()))
                .plantAge(blankToNull(plantAgeInput.getText().toString()))
                .creditCost(creditCost)
                .pickupWindow(draft.pickupStartMs, draft.pickupEndMs)
                .pickupWindowDays(4)
                .expiresAtMs(draft.expiresAtMs)
                .geo(geoLat, geoLon)
                .sprayDisclosure(sprayInput.getText().toString().trim())
                .visitRules(blankToNull(visitRulesInput.getText().toString()))
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
                .putString("potSize", potSizeInput.getText().toString())
                .putString("plantAge", plantAgeInput.getText().toString())
                .putInt("credit", creditCost)
                .putString("pickupStart", pickupStartInput.getText().toString())
                .putString("pickupEnd", pickupEndInput.getText().toString())
                .putString("expiryDays", expiryDaysInput.getText().toString())
                .putString("spray", sprayInput.getText().toString())
                .putString("visitRules", visitRulesInput.getText().toString())
                .putBoolean("free", freeListing)
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
        potSizeInput.setText(prefs.getString("potSize", ""));
        plantAgeInput.setText(prefs.getString("plantAge", ""));
        setCredit(prefs.getInt("credit", 1));
        pickupStartInput.setText(prefs.getString("pickupStart", ""));
        pickupEndInput.setText(prefs.getString("pickupEnd", ""));
        expiryDaysInput.setText(prefs.getString("expiryDays", ""));
        visitRulesInput.setText(prefs.getString("visitRules", ""));
        String spray = prefs.getString("spray", "");
        if ("none".equals(spray.trim())) {
            selectSprayMode(true);
        } else {
            sprayInput.setText(spray);
            selectSprayMode(false);
        }
        freeListing = prefs.getBoolean("free", false);
        Ui.setChipSelected(this, freeChip, freeListing);
        setCreditRowEnabled(!freeListing);
        updateCreditLabel();
    }

    private void clearDraft() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
    }
}
