package com.gardenswap.test.harvest;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.HarvestEvent;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.HarvestLogLogic;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Harvest log (AND-040): quantity remaining, progress toward zero, and the
 * event history for one harvest listing. Owners log pickings (added) and
 * takeaways; the remaining quantity drives the listing's availability.
 */
public class HarvestLogActivity extends AppCompatActivity {

    public static final String EXTRA_LISTING_ID = "listing_id";

    private Listing listing;
    private TextView remainingView;
    private ProgressBar progressBar;
    private LinearLayout eventRows;
    private TextView statusView;
    private EditText amountInput;
    private EditText noteInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String listingId = getIntent().getStringExtra(EXTRA_LISTING_ID);
        if (listingId == null) {
            Toast.makeText(this, "Missing listing id", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        LinearLayout root = Ui.column(this, 20);
        TextView title = Ui.label(this, "Harvest log");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, "Remaining"));
        remainingView = Ui.label(this, "…");
        remainingView.setTextSize(28);
        remainingView.setGravity(Gravity.CENTER);
        root.addView(remainingView);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(progressBar);
        Ui.gap(root, this, 12);

        root.addView(Ui.label(this, "Log an event"));
        amountInput = Ui.input(this, "Amount (e.g. 2.5)",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
        noteInput = Ui.input(this, "Note (optional)", InputType.TYPE_CLASS_TEXT);
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        Button addedButton = Ui.primaryButton(this, "+ Picked");
        addedButton.setOnClickListener(v -> logEvent(1));
        Button takenButton = Ui.secondaryButton(this, "− Taken");
        takenButton.setOnClickListener(v -> logEvent(-1));
        buttonRow.addView(addedButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        buttonRow.addView(takenButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(amountInput);
        root.addView(noteInput);
        root.addView(buttonRow);
        Ui.gap(root, this, 12);

        root.addView(Ui.label(this, "History"));
        eventRows = new LinearLayout(this);
        eventRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(eventRows);
        Ui.gap(root, this, 8);

        statusView = Ui.status(this);
        root.addView(statusView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        load(listingId);
    }

    private void load(final String listingId) {
        GardenSwapApi api = ApiProvider.get();
        api.getListing(listingId, new GardenSwapApi.Callback<Listing>() {
            @Override
            public void onSuccess(Listing result) {
                listing = result;
                refreshEvents();
            }

            @Override
            public void onError(ApiException error) {
                statusView.setText("Could not load listing: " + error.getMessage());
            }
        });
    }

    private void refreshEvents() {
        ApiProvider.get().getHarvestEvents(listing.getId(),
                new GardenSwapApi.Callback<List<HarvestEvent>>() {
                    @Override
                    public void onSuccess(List<HarvestEvent> result) {
                        render(result);
                    }

                    @Override
                    public void onError(ApiException error) {
                        statusView.setText("Could not load events: " + error.getMessage());
                    }
                });
    }

    private void render(List<HarvestEvent> events) {
        double initial = listing.getQuantity() == null ? 0 : listing.getQuantity();
        String unit = listing.getUnit() == null ? "" : " " + listing.getUnit();
        double remaining = HarvestLogLogic.remaining(initial, events);
        remainingView.setText(trim(remaining) + unit + " left");
        progressBar.setProgress((int) Math.round(
                HarvestLogLogic.progressTowardZero(initial, events) * 100));

        eventRows.removeAllViews();
        List<HarvestEvent> sorted = HarvestLogLogic.newestFirst(events);
        if (sorted.isEmpty()) {
            eventRows.addView(Ui.label(this, "No events yet."));
            return;
        }
        DateFormat format = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT, Locale.US);
        for (HarvestEvent event : sorted) {
            String sign = event.getDelta() > 0 ? "+" : "−";
            String line = sign + trim(Math.abs(event.getDelta())) + unit
                    + " · " + format.format(new Date(event.getCreatedAtMs()));
            if (!event.getNote().isEmpty()) {
                line += " · " + event.getNote();
            }
            eventRows.addView(Ui.label(this, line));
        }
    }

    private void logEvent(int sign) {
        String error = HarvestLogLogic.validateDelta(amountInput.getText().toString());
        if (error != null) {
            statusView.setText(error);
            return;
        }
        double amount = Math.abs(Double.parseDouble(amountInput.getText().toString().trim()));
        statusView.setText("Logging…");
        ApiProvider.get().logHarvestEvent(listing.getId(), sign * amount,
                noteInput.getText().toString().trim(),
                new GardenSwapApi.Callback<HarvestEvent>() {
                    @Override
                    public void onSuccess(HarvestEvent result) {
                        amountInput.setText("");
                        noteInput.setText("");
                        statusView.setText("");
                        refreshEvents();
                    }

                    @Override
                    public void onError(ApiException error) {
                        statusView.setText("Could not log: " + error.getMessage());
                    }
                });
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** Convenience launcher. */
    public static void open(android.content.Context context, String listingId) {
        Intent intent = new Intent(context, HarvestLogActivity.class);
        intent.putExtra(EXTRA_LISTING_ID, listingId);
        context.startActivity(intent);
    }
}
