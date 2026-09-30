package com.gardenswap.test.sitters;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.SitterProfileIn;
import com.gardenswap.test.ui.AvailabilityStrip;
import com.gardenswap.test.ui.Nav;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.NavRouter;
import com.gardenswap.test.util.SitterServices;

/**
 * Sitter registration form: registers the signed-in user as a plant sitter
 * (or updates their existing sitter profile) via {@code PUT /v1/sitters/me}.
 *
 * <p>Reached from the "+ Become a sitter" button on {@link SitterListActivity}.
 */
public class BecomeSitterActivity extends AppCompatActivity {

    private EditText bioInput;
    private EditText experienceInput;
    private EditText radiusInput;
    private EditText rateInput;
    private TextView creditsChip;
    private TextView usdChip;
    private String rateUnit = "credits";
    private Switch activeSwitch;
    private TextView statusText;
    private Button submitButton;
    private final java.util.Set<String> selectedServices = new java.util.LinkedHashSet<>();
    private AvailabilityStrip.Editor availabilityEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout header = Ui.column(this, 24);
        header.addView(Ui.appTitleRow(this));
        int pad = Ui.dp(this, 24);
        header.setPadding(pad, pad, pad, 0);

        LinearLayout form = Ui.column(this, 24);
        form.setPadding(pad, 0, pad, pad);
        form.addView(Ui.headline(this, "Become a plant sitter"));
        Ui.gap(form, this, 4);
        form.addView(Ui.body(this,
                "Offer watering, repotting, and vacation care to neighbors. You can update this anytime."));
        Ui.gap(form, this, 16);

        form.addView(Ui.label(this, "Bio"));
        Ui.gap(form, this, 4);
        bioInput = Ui.input(this, "A few lines about you and your plants",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bioInput.setMinLines(3);
        form.addView(bioInput);
        Ui.gap(form, this, 12);

        form.addView(Ui.label(this, "Years of experience"));
        Ui.gap(form, this, 4);
        experienceInput = Ui.input(this, "0",
                InputType.TYPE_CLASS_NUMBER);
        form.addView(experienceInput);
        Ui.gap(form, this, 12);

        form.addView(Ui.label(this, "Service radius (miles)"));
        Ui.gap(form, this, 4);
        radiusInput = Ui.input(this, "5",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(radiusInput);
        Ui.gap(form, this, 12);

        // Optional daily rate: amount + unit (credits/day or $/day).
        // Empty amount = "rate on request".
        form.addView(Ui.label(this, "Daily rate (optional)"));
        Ui.gap(form, this, 4);
        rateInput = Ui.input(this, "e.g. 5",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(rateInput);
        Ui.gap(form, this, 8);
        LinearLayout unitRow = new LinearLayout(this);
        unitRow.setOrientation(LinearLayout.HORIZONTAL);
        creditsChip = Ui.chip(this, "Credits/day");
        usdChip = Ui.chip(this, "$/day");
        creditsChip.setOnClickListener(v -> selectRateUnit("credits"));
        usdChip.setOnClickListener(v -> selectRateUnit("usd"));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        chipParams.setMarginEnd(Ui.dp(this, 8));
        creditsChip.setLayoutParams(chipParams);
        usdChip.setLayoutParams(chipParams);
        unitRow.addView(creditsChip);
        unitRow.addView(usdChip);
        form.addView(unitRow);
        selectRateUnit("credits");
        Ui.gap(form, this, 4);
        form.addView(Ui.caption(this,
                "Leave the rate empty to show \"Rate on request\"."));
        Ui.gap(form, this, 12);

        // Services offered: multi-select chips from the fixed taxonomy,
        // wrapped into rows so they stay on-screen.
        form.addView(Ui.label(this, "Services you offer"));
        Ui.gap(form, this, 4);
        form.addView(Ui.serviceChipGrid(this, SitterServices.KEYS, selectedServices, true));
        Ui.gap(form, this, 4);
        form.addView(Ui.caption(this,
                "Pick the services bookers can request. Leave all off for \"on request\"."));
        Ui.gap(form, this, 12);

        // Availability: tap the days you're free for plant sitting. Saved
        // right after the profile registers (PUT /v1/sitters/me/availability).
        form.addView(Ui.label(this, "Availability"));
        Ui.gap(form, this, 4);
        LinearLayout strip = Ui.column(this, 0);
        form.addView(strip);
        availabilityEditor = AvailabilityStrip.renderEditor(this, strip, null);
        Ui.gap(form, this, 4);
        form.addView(Ui.caption(this,
                "Tap the days you're free for plant sitting — a blue outline "
                        + "means you're available that day."));
        Ui.gap(form, this, 12);

        activeSwitch = new Switch(this);
        activeSwitch.setText("Available for bookings");
        activeSwitch.setChecked(true);
        form.addView(activeSwitch);
        Ui.gap(form, this, 16);

        statusText = Ui.status(this);
        form.addView(statusText);
        Ui.gap(form, this, 8);

        submitButton = Ui.primaryButton(this, "Register as sitter");
        submitButton.setOnClickListener(v -> submit());
        form.addView(submitButton);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        setContentView(Ui.stickyHeaderScreen(this, header, scroll));
        Nav.attach(this, NavRouter.Tab.CARE);
    }

    private void selectRateUnit(String unit) {
        rateUnit = unit;
        Ui.setChipSelected(this, creditsChip, "credits".equals(unit));
        Ui.setChipSelected(this, usdChip, "usd".equals(unit));
    }

    private void submit() {
        String bio = bioInput.getText().toString().trim();
        int years;
        double radius;
        try {
            years = Integer.parseInt(experienceInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            statusText.setText("Years of experience must be a whole number (0-60).");
            return;
        }
        try {
            radius = Double.parseDouble(radiusInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            statusText.setText("Service radius must be a number greater than 0.");
            return;
        }
        if (years < 0 || years > 60) {
            statusText.setText("Years of experience must be between 0 and 60.");
            return;
        }
        if (radius <= 0 || radius > 100) {
            statusText.setText("Service radius must be between 0 and 100 miles.");
            return;
        }
        if (bio.length() > 2000) {
            statusText.setText("Bio must be under 2000 characters.");
            return;
        }

        // Optional rate: empty = "rate on request". Credits must be whole;
        // usd allows cents.
        Double rateAmount = null;
        String rateUnitValue = null;
        String rateRaw = rateInput.getText().toString().trim();
        if (!rateRaw.isEmpty()) {
            double parsed;
            try {
                parsed = Double.parseDouble(rateRaw);
            } catch (NumberFormatException e) {
                statusText.setText("Daily rate must be a number, or leave it empty.");
                return;
            }
            if (parsed < 0) {
                statusText.setText("Daily rate can't be negative.");
                return;
            }
            if ("credits".equals(rateUnit) && parsed != Math.rint(parsed)) {
                statusText.setText("Credit rates must be whole credits.");
                return;
            }
            rateAmount = parsed;
            rateUnitValue = rateUnit;
        }

        submitButton.setEnabled(false);
        statusText.setText("Registering…");
        String[] services = selectedServices.toArray(new String[0]);
        final java.util.List<String> available =
                new java.util.ArrayList<>(availabilityEditor.getAvailable());
        SitterProfileIn profile = new SitterProfileIn(
                bio, years, radius, activeSwitch.isChecked(),
                rateAmount, rateUnitValue, services);
        ApiProvider.get().upsertSitterProfile(profile, new GardenSwapApi.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Profile registered — now save the picked availability.
                ApiProvider.get().setSitterAvailability(available,
                        new GardenSwapApi.Callback<Void>() {
                            @Override
                            public void onSuccess(Void r) {
                                finishRegistered();
                            }

                            @Override
                            public void onError(ApiException e) {
                                // Profile is live; availability can be set
                                // from "Your sitter profile".
                                Toast.makeText(BecomeSitterActivity.this,
                                        "Registered — but availability didn't save ("
                                                + e.getCode()
                                                + "). Set it from your sitter profile.",
                                        Toast.LENGTH_LONG).show();
                                finish();
                            }
                        });
            }

            @Override
            public void onError(ApiException e) {
                submitButton.setEnabled(true);
                if ("profile_required".equals(e.getCode())) {
                    statusText.setText("Create your profile first, then register as a sitter.");
                } else {
                    statusText.setText("Couldn't register (" + e.getCode() + "). Try again.");
                }
            }
        });
    }

    private void finishRegistered() {
        Toast.makeText(BecomeSitterActivity.this,
                "You're listed as a sitter", Toast.LENGTH_SHORT).show();
        finish();
    }
}
