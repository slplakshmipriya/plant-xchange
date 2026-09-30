package com.gardenswap.test.sitters;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.Booking;
import com.gardenswap.test.api.BookingRequest;
import com.gardenswap.test.api.BookingStatus;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.IdvStatus;
import com.gardenswap.test.api.PaymentIntent;
import com.gardenswap.test.api.Review;
import com.gardenswap.test.api.SitterProfile;
import com.gardenswap.test.idv.IdvActivity;
import com.gardenswap.test.ui.BadgeState;
import com.gardenswap.test.ui.SitterCardView;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.ui.VerifiedBadgeView;
import com.gardenswap.test.util.SitterLogic;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sitter profile + booking sheet (AND-070), restyled per the design
 * prototype (UID-017).
 *
 * <p>Header card (avatar, name, verification badge), skills section,
 * reviews list, then the booking sheet. The booking sheet captures
 * dates, services, and per-plant care instructions, and shows price
 * math including the platform fee (display only — the server computes
 * the charge). Payment hold happens on confirm server-side (API-070);
 * this screen only requests the booking.
 */
public class SitterProfileActivity extends AppCompatActivity {

    public static final String EXTRA_SITTER_ID = "sitter_id";

    /** Booking length in days: the sheet picks a start date; end = start + this. */
    private static final int BOOKING_DAYS = 7;

    private TextView statusText;
    private LinearLayout content;
    private SitterProfile sitter;
    private boolean requesting;
    private long bookingStartMs;
    private final Set<String> selectedServices = new LinkedHashSet<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEE, MMM d", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 24);
        root.addView(Ui.headline(this, "Sitter profile"));
        Ui.gap(root, this, 4);
        statusText = Ui.status(this);
        root.addView(statusText);
        Ui.gap(root, this, 8);
        content = Ui.column(this, 0);
        root.addView(content);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

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

        content.addView(headerCard());
        Ui.gap(content, this, 16);

        content.addView(Ui.eyebrow(this, "Skills"));
        Ui.gap(content, this, 8);
        content.addView(skillChips());
        Ui.gap(content, this, 16);

        // Availability: SitterProfile exposes no availability data and the
        // API has no sitter-update endpoint, so this is a read-only strip
        // showing the next 14 days as open, with the limitation stated.
        content.addView(availabilitySection());
        Ui.gap(content, this, 16);

        content.addView(Ui.eyebrow(this, "Reviews"));
        Ui.gap(content, this, 8);
        for (Review review : mockReviews(sitter)) {
            content.addView(reviewCard(review));
            Ui.gap(content, this, 8);
        }
        Ui.gap(content, this, 8);

        // ReviewActivity resolves the real completed booking via
        // GET /v1/bookings (role + completed filter); the id passed here is
        // only the fallback if that endpoint is not available yet.
        Button writeReview = Ui.secondaryButton(this, "Write a review");
        writeReview.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReviewActivity.class);
            intent.putExtra(ReviewActivity.EXTRA_BOOKING_ID, "mock-booking-1");
            intent.putExtra(ReviewActivity.EXTRA_BOOKING_STATUS,
                    BookingStatus.COMPLETED.name());
            startActivity(intent);
        });
        content.addView(writeReview);
        Ui.gap(content, this, 16);

        renderBookingSheet();
    }

    private LinearLayout headerCard() {
        LinearLayout card = Ui.card(this);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = SitterCardView.avatar(this,
                sitter.getDisplayName(), 72);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 72), Ui.dp(this, 72));
        avatarParams.setMarginEnd(Ui.dp(this, 16));
        avatar.setLayoutParams(avatarParams);
        top.addView(avatar);

        LinearLayout nameBlock = new LinearLayout(this);
        nameBlock.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.title(this,
                sitter.getDisplayName() != null ? sitter.getDisplayName() : "");
        nameBlock.addView(name);
        VerifiedBadgeView badge = new VerifiedBadgeView(this);
        badge.setState(sitter.isIdVerified() ? BadgeState.ID_VERIFIED : BadgeState.UNVERIFIED);
        nameBlock.addView(badge);
        top.addView(nameBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);
        Ui.gap(card, this, 8);

        // Trust line derived from swap history + reviews (design board).
        card.addView(Ui.body(this,
                SitterLogic.starsText(sitter.getRating(), sitter.getReviewCount())
                        + " · " + sitter.getCompletedSits() + " sits completed"));
        card.addView(Ui.body(this,
                SitterLogic.rateLine(sitter.getRateAmount(), sitter.getRateUnit())
                        + " · covers " + sitter.getRadiusMiles() + " mi"));
        return card;
    }

    private LinearLayout skillChips() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] services = sitter.getServices();
        boolean any = false;
        if (services != null) {
            for (String service : services) {
                if (service == null || service.trim().isEmpty()) {
                    continue;
                }
                TextView chip = Ui.chip(this, service.trim());
                chip.setClickable(false);
                chip.setFocusable(false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMarginEnd(Ui.dp(this, 8));
                chip.setLayoutParams(params);
                row.addView(chip);
                any = true;
            }
        }
        if (!any) {
            row.addView(Ui.body(this, "Services on request"));
        }
        return row;
    }

    private LinearLayout reviewCard(Review review) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.body(this, SitterLogic.starsText(review.getRating(), 1)));
        Ui.gap(card, this, 4);
        card.addView(Ui.caption(this,
                review.getText() != null ? review.getText() : ""));
        if (review.isVerifiedBooking()) {
            Ui.gap(card, this, 8);
            TextView badge = Ui.chip(this, "✓ Verified booking");
            badge.setClickable(false);
            badge.setFocusable(false);
            card.addView(badge);
        }
        return card;
    }

    /**
     * Mock-phase review rows: no per-review endpoint exists yet
     * (API-072 is proposed), so the profile synthesizes representative
     * rows from the sitter's aggregate rating.
     */
    private List<Review> mockReviews(SitterProfile profile) {
        List<Review> reviews = new ArrayList<>();
        if (profile == null || profile.getReviewCount() <= 0) {
            return reviews;
        }
        int top = (int) Math.round(profile.getRating());
        reviews.add(new Review(Math.max(1, Math.min(5, top)), null,
                "Great communication and my plants looked happy when I got back."));
        reviews.add(new Review(Math.max(1, Math.min(5, top - 1)), null,
                "Reliable watering while we were away. Would book again."));
        return reviews;
    }

    // ---- Booking sheet (behavior unchanged from the pre-restyle screen) ----

    private void renderBookingSheet() {
        // Reset per render: default start is 7 days out, all services selected.
        bookingStartMs = startOfDay(System.currentTimeMillis()
                + 7 * 24L * 3_600_000L);
        selectedServices.clear();
        String[] services = sitter.getServices();
        if (services != null) {
            for (String service : services) {
                if (service != null && !service.trim().isEmpty()) {
                    selectedServices.add(service.trim());
                }
            }
        }

        TextView bookingTitle = Ui.headline(this, "Request a booking");
        content.addView(bookingTitle);
        Ui.gap(content, this, 8);

        content.addView(Ui.eyebrow(this, "Start date"));
        Ui.gap(content, this, 4);
        TextView dateLabel = Ui.body(this, dateFormat.format(bookingStartMs));
        content.addView(dateLabel);
        Ui.gap(content, this, 4);
        Button pickDate = Ui.secondaryButton(this, "Choose date");
        pickDate.setOnClickListener(v -> showDatePicker(dateLabel));
        content.addView(pickDate);
        Ui.gap(content, this, 8);

        content.addView(Ui.eyebrow(this, "Services"));
        Ui.gap(content, this, 4);
        content.addView(serviceChipRows());
        Ui.gap(content, this, 8);

        EditText careInput = Ui.input(this,
                "Care instructions (per-plant notes)", InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        content.addView(careInput);
        Ui.gap(content, this, 8);

        // Price preview for the 7-day booking: days × daily rate, plus the
        // 18% platform fee on usd rates (display only — the server computes
        // the charge).
        TextView pricePreview = Ui.label(this, "");
        content.addView(pricePreview);
        Ui.gap(content, this, 8);
        Button previewButton = Ui.secondaryButton(this, "Preview price");
        previewButton.setOnClickListener(v -> pricePreview.setText(
                SitterLogic.bookingPreview(sitter.getRateAmount(),
                        sitter.getRateUnit(), BOOKING_DAYS)));
        content.addView(previewButton);
        Ui.gap(content, this, 12);

        Button requestButton = Ui.primaryButton(this, "Request booking");
        requestButton.setOnClickListener(v ->
                request(careInput.getText().toString(), requestButton));
        content.addView(requestButton);
    }

    private long startOfDay(long timeMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMs);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void showDatePicker(TextView dateLabel) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(bookingStartMs);
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(year, month, day, 9, 0, 0);
                    chosen.set(Calendar.MILLISECOND, 0);
                    bookingStartMs = chosen.getTimeInMillis();
                    dateLabel.setText(dateFormat.format(bookingStartMs));
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(startOfDay(System.currentTimeMillis()));
        dialog.show();
    }

    /** Multi-select service chips, wrapped into rows by estimated width. */
    private LinearLayout serviceChipRows() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        float density = getResources().getDisplayMetrics().density;
        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels / density) - 48;
        LinearLayout row = chipRow();
        column.addView(row);
        int used = 0;
        boolean any = false;
        Set<String> seen = new LinkedHashSet<>();
        String[] allServices = sitter.getServices();
        if (allServices != null) {
            for (String raw : allServices) {
                if (raw == null || raw.trim().isEmpty() || !seen.add(raw.trim())) {
                    continue;
                }
                String service = raw.trim();
                int estimate = service.length() * 8 + 48;
                if (used > 0 && used + estimate > maxWidth) {
                    Ui.gap(column, this, 8);
                    row = chipRow();
                    column.addView(row);
                    used = 0;
                }
                TextView chip = Ui.chip(this, service);
                Ui.setChipSelected(this, chip, selectedServices.contains(service));
                chip.setOnClickListener(v -> toggleService(chip, service));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMarginEnd(Ui.dp(this, 8));
                chip.setLayoutParams(params);
                row.addView(chip);
                used += estimate;
                any = true;
            }
        }
        if (!any) {
            column.removeAllViews();
            column.addView(Ui.body(this, "Services on request"));
        }
        return column;
    }

    private LinearLayout chipRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    /**
     * Read-only strip of the next 14 days. The {@link SitterProfile} model
     * carries no availability data and the API exposes no sitter-update
     * endpoint, so days cannot be toggled or persisted — the strip defaults
     * to "Available" and states the limitation explicitly.
     */
    private LinearLayout availabilitySection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(Ui.eyebrow(this, "Availability"));
        Ui.gap(section, this, 8);
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE d", Locale.US);
        Calendar cal = Calendar.getInstance();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        section.addView(row);
        for (int i = 0; i < 14; i++) {
            if (i > 0 && i % 7 == 0) {
                Ui.gap(section, this, 8);
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                section.addView(row);
            }
            row.addView(dayCell(dayFormat.format(cal.getTime())));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        Ui.gap(section, this, 8);
        section.addView(Ui.caption(this,
                "This sitter hasn't shared a calendar, so all days show as open. "
                        + "Availability editing isn't supported by the API yet."));
        return section;
    }

    private LinearLayout dayCell(String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        TextView day = Ui.caption(this, label);
        day.setGravity(Gravity.CENTER);
        cell.addView(day);
        TextView chip = Ui.chip(this, "Available");
        chip.setClickable(false);
        chip.setFocusable(false);
        cell.addView(chip);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(Ui.dp(this, 4));
        cell.setLayoutParams(params);
        return cell;
    }

    private void toggleService(TextView chip, String service) {
        boolean now = !selectedServices.contains(service);
        if (now) {
            selectedServices.add(service);
        } else {
            selectedServices.remove(service);
        }
        Ui.setChipSelected(this, chip, now);
    }

    /**
     * IDV gate (AND-146): the booker must be ID-verified before the booking
     * request is sent. Unverified users get a toast and are routed to
     * {@link IdvActivity} instead.
     */
    private void request(String care, Button requestButton) {
        if (requesting) {
            return;
        }
        requesting = true;
        requestButton.setEnabled(false);
        statusText.setText("Checking verification…");
        ApiProvider.get().getIdvStatus(new GardenSwapApi.Callback<IdvStatus>() {
            @Override
            public void onSuccess(IdvStatus status) {
                if (status != IdvStatus.VERIFIED) {
                    requesting = false;
                    requestButton.setEnabled(true);
                    statusText.setText("");
                    Toast.makeText(SitterProfileActivity.this,
                            "Verify your ID to book a sitter", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(SitterProfileActivity.this,
                            IdvActivity.class));
                    return;
                }
                submitBooking(care, requestButton);
            }

            @Override
            public void onError(ApiException e) {
                requesting = false;
                requestButton.setEnabled(true);
                statusText.setText("Couldn't check verification (" + e.getCode() + ").");
            }
        });
    }

    private void submitBooking(String care, Button requestButton) {
        statusText.setText("Requesting booking…");
        long start = bookingStartMs;
        long end = start + BOOKING_DAYS * 24L * 3_600_000L;
        String[] services = selectedServices.toArray(new String[0]);
        BookingRequest req = new BookingRequest(sitter.getSitterId(), start, end,
                services, care);
        ApiProvider.get().requestBooking(req, new GardenSwapApi.Callback<Booking>() {
            @Override
            public void onSuccess(Booking booking) {
                requesting = false;
                statusText.setText("Booking requested! The sitter confirms next. "
                        + "Payment is held on confirm and captured on completion.");
                requestButton.setVisibility(View.GONE);
                FirebaseAnalytics.getInstance(SitterProfileActivity.this)
                        .logEvent("booking_requested", null);
                requestPaymentIntentStub(booking.getBookingId());
            }

            @Override
            public void onError(ApiException e) {
                requesting = false;
                requestButton.setEnabled(true);
                statusText.setText("Couldn't request the booking (" + e.getCode() + ").");
            }
        });
    }

    /**
     * Payment sheet scaffold (API-070). Once the booking exists, the client
     * mints a sitting payment intent and would hand its client secret to the
     * Stripe payment sheet. Stripe is not integrated yet, so this only shows
     * the stub dialog; no SDK, no keys, no charge.
     */
    private void requestPaymentIntentStub(final String bookingId) {
        statusText.setText("Preparing payment…");
        ApiProvider.get().createSittingPaymentIntent(bookingId,
                new GardenSwapApi.Callback<PaymentIntent>() {
                    @Override
                    public void onSuccess(PaymentIntent intent) {
                        statusText.setText("");
                        showPaymentStubDialog(bookingId, intent.getClientSecret());
                    }

                    @Override
                    public void onError(ApiException e) {
                        statusText.setText("");
                        Toast.makeText(SitterProfileActivity.this,
                                "Couldn't start payment (" + e.getCode() + ")",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showPaymentStubDialog(String bookingId, String clientSecret) {
        new AlertDialog.Builder(this)
                .setTitle("Payment (stub — Stripe not integrated)")
                .setMessage("A Stripe payment sheet would open here for booking "
                        + bookingId + " with the client secret:\n\n"
                        + clientSecret
                        + "\n\nNo payment was made — Stripe is not integrated yet, "
                        + "so there is nothing to charge against.")
                .setPositiveButton("Done", null)
                .show();
    }
}
