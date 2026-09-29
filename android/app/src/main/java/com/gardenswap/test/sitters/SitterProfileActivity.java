package com.gardenswap.test.sitters;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.Booking;
import com.gardenswap.test.api.BookingRequest;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.SitterProfile;
import com.gardenswap.test.ui.BadgeState;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.ui.VerifiedBadgeView;
import com.gardenswap.test.util.ReviewGuard;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Sitter profile + booking sheet (AND-070).
 *
 * <p>Profile shows badges, the swap-history-derived trust line, and reviews
 * summary. The booking sheet captures dates, services, and per-plant care
 * instructions, and shows price math including the platform fee (display
 * only — the server computes the charge). Payment hold happens on confirm
 * server-side (API-070); this screen only requests the booking.
 */
public class SitterProfileActivity extends AppCompatActivity {

    public static final String EXTRA_SITTER_ID = "sitter_id";

    private TextView statusText;
    private LinearLayout content;
    private SitterProfile sitter;
    private boolean requesting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Sitter profile");
        title.setTextSize(20);
        statusText = Ui.status(this);
        content = Ui.column(this, 0);

        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(statusText);
        Ui.gap(root, this, 8);
        root.addView(content);
        setContentView(root);

        String sitterId = getIntent().getStringExtra(EXTRA_SITTER_ID);
        if (sitterId == null) {
            sitterId = "s1";
        }
        load(sitterId);
    }

    private void load(String sitterId) {
        statusText.setText("Loading sitter…");
        ApiProvider.get().getSitter(sitterId, new GardenSwapApi.Callback<SitterProfile>() {
            @Override
            public void onSuccess(SitterProfile result) {
                sitter = result;
                render();
            }

            @Override
            public void onError(ApiException e) {
                statusText.setText("Couldn't load this sitter (" + e.getCode() + ").");
            }
        });
    }

    private void render() {
        statusText.setText("");
        content.removeAllViews();

        TextView name = Ui.label(this, sitter.getDisplayName());
        name.setTextSize(18);
        content.addView(name);

        VerifiedBadgeView badge = new VerifiedBadgeView(this);
        badge.setState(sitter.isIdVerified() ? BadgeState.ID_VERIFIED : BadgeState.UNVERIFIED);
        content.addView(badge);
        Ui.gap(content, this, 4);

        // Trust line derived from swap history + reviews (design board).
        content.addView(Ui.label(this,
                ReviewGuard.ratingLine(sitter.getRating(), sitter.getReviewCount())
                        + " · " + sitter.getCompletedSits() + " sits completed"));
        content.addView(Ui.label(this,
                ReviewGuard.formatPrice(sitter.getRatePerVisitCents())
                        + " per visit · covers " + sitter.getRadiusMiles() + " mi"));
        content.addView(Ui.label(this,
                "Services: " + TextUtils.join(", ", sitter.getServices())));
        Ui.gap(content, this, 16);

        TextView bookingTitle = Ui.label(this, "Request a booking");
        bookingTitle.setTextSize(16);
        content.addView(bookingTitle);
        Ui.gap(content, this, 4);

        // Mock-phase date entry: number of days from today (real UI uses a date picker).
        EditText daysInput = Ui.input(this, "Days from today (e.g. 7)", InputType.TYPE_CLASS_NUMBER);
        content.addView(daysInput);
        Ui.gap(content, this, 4);
        EditText visitsInput = Ui.input(this, "Number of visits", InputType.TYPE_CLASS_NUMBER);
        content.addView(visitsInput);
        Ui.gap(content, this, 4);
        EditText careInput = Ui.input(this,
                "Care instructions (per-plant notes)", InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        content.addView(careInput);
        Ui.gap(content, this, 8);

        // Price math preview, incl. the 18% platform fee (display only).
        TextView pricePreview = Ui.label(this, "");
        content.addView(pricePreview);
        Ui.gap(content, this, 4);
        Button previewButton = Ui.button(this, "Preview price");
        previewButton.setOnClickListener(v -> {
            int visits = parsePositive(visitsInput.getText().toString(), 1);
            int fee = Math.round(visits * sitter.getRatePerVisitCents() * 0.18f);
            pricePreview.setText(ReviewGuard.priceLine(
                    visits, sitter.getRatePerVisitCents(), fee));
        });
        content.addView(previewButton);
        Ui.gap(content, this, 8);

        Button requestButton = Ui.button(this, "Request booking");
        requestButton.setOnClickListener(v -> {
            int days = parsePositive(daysInput.getText().toString(), 7);
            request(days, careInput.getText().toString(), requestButton);
        });
        content.addView(requestButton);
    }

    private int parsePositive(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void request(int daysFromNow, String care, Button requestButton) {
        if (requesting) {
            return;
        }
        requesting = true;
        requestButton.setEnabled(false);
        statusText.setText("Requesting booking…");
        long start = System.currentTimeMillis() + daysFromNow * 24L * 3_600_000L;
        long end = start + 7 * 24L * 3_600_000L;
        BookingRequest req = new BookingRequest(sitter.getSitterId(), start, end,
                sitter.getServices(), care);
        ApiProvider.get().requestBooking(req, new GardenSwapApi.Callback<Booking>() {
            @Override
            public void onSuccess(Booking booking) {
                requesting = false;
                statusText.setText("Booking requested! The sitter confirms next. "
                        + "Payment is held on confirm and captured on completion.");
                requestButton.setVisibility(View.GONE);
                FirebaseAnalytics.getInstance(SitterProfileActivity.this)
                        .logEvent("booking_requested", null);
            }

            @Override
            public void onError(ApiException e) {
                requesting = false;
                requestButton.setEnabled(true);
                statusText.setText("Couldn't request the booking (" + e.getCode() + ").");
            }
        });
    }
}
