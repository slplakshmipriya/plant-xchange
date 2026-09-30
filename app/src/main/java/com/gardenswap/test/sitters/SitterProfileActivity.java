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
import com.gardenswap.test.ui.AvailabilityStrip;
import com.gardenswap.test.ui.BadgeState;
import com.gardenswap.test.ui.SitterCardView;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.ui.VerifiedBadgeView;
import com.gardenswap.test.util.SitterLogic;
import com.gardenswap.test.util.SitterServices;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;

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

    private TextView statusText;
    private LinearLayout content;
    private SitterProfile sitter;
    private boolean requesting;
    private long bookingStartMs;
    private long bookingEndMs;
    private TextView startDateLabel;
    private TextView endDateLabel;
    private TextView pricePreview;
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

        // Availability: read-only strip for other sitters, editable with a
        // save button on your own profile (PUT /v1/sitters/me/availability).
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
        // You can't book yourself: when this screen shows your own profile
        // (reached via "Your sitter profile"), the booking section is
        // omitted entirely.
        if (isOwnProfile()) {
            return;
        }
        // Reset per render: default is a 7-day booking starting 7 days out,
        // all services selected.
        bookingStartMs = startOfDay(System.currentTimeMillis()
                + 7 * 24L * 3_600_000L);
        bookingEndMs = bookingStartMs + 6 * 24L * 3_600_000L;
        selectedServices.clear();
        String[] services = sitter.getServices();
        if (services != null) {
            for (String service : services) {
                if (service != null && !service.trim().isEmpty()) {
                    selectedServices.add(service.trim());
                }
            }
        }

        content.addView(Ui.headline(this, "Request a booking"));
        Ui.gap(content, this, 8);

        // Start/end dates side by side with compact "Choose" buttons.
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.addView(dateGroup(true));
        dateRow.addView(dateGroup(false));
        content.addView(dateRow);
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

        // Price preview for the chosen inclusive date range: days × daily
        // rate, plus the 18% platform fee on usd rates (display only — the
        // server computes the charge). Updates live as dates change.
        pricePreview = Ui.label(this, "");
        content.addView(pricePreview);
        Ui.gap(content, this, 8);
        updatePricePreview();

        Button requestButton = Ui.primaryButton(this, "Request booking");
        requestButton.setOnClickListener(v ->
                request(careInput.getText().toString(), requestButton));
        content.addView(requestButton);
    }

    /** Start/end date group: eyebrow, date label, compact "Choose" button. */
    private LinearLayout dateGroup(boolean isStart) {
        LinearLayout group = Ui.column(this, 0);
        group.addView(Ui.eyebrow(this, isStart ? "Start date" : "End date"));
        Ui.gap(group, this, 4);
        TextView label = Ui.body(this,
                dateFormat.format(isStart ? bookingStartMs : bookingEndMs));
        group.addView(label);
        if (isStart) {
            startDateLabel = label;
        } else {
            endDateLabel = label;
        }
        Ui.gap(group, this, 4);
        Button choose = Ui.rowButton(this, "Choose", false);
        choose.setOnClickListener(v -> showDatePicker(isStart));
        group.addView(choose);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        group.setLayoutParams(params);
        return group;
    }

    /** Inclusive day count for the booking range. Always >= 1. */
    private int bookingDays() {
        long days = (bookingEndMs - bookingStartMs) / (24L * 3_600_000L) + 1;
        return (int) Math.max(1, days);
    }

    private void updatePricePreview() {
        if (pricePreview != null) {
            pricePreview.setText(SitterLogic.bookingPreview(
                    sitter.getRateAmount(), sitter.getRateUnit(), bookingDays()));
        }
    }

    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(isStart ? bookingStartMs : bookingEndMs);
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(year, month, day, 9, 0, 0);
                    chosen.set(Calendar.MILLISECOND, 0);
                    long picked = chosen.getTimeInMillis();
                    // Keep the range valid: clamp the other end if needed.
                    if (isStart) {
                        bookingStartMs = picked;
                        if (bookingEndMs < bookingStartMs) {
                            bookingEndMs = bookingStartMs;
                        }
                    } else {
                        bookingEndMs = picked;
                        if (bookingEndMs < bookingStartMs) {
                            bookingStartMs = bookingEndMs;
                        }
                    }
                    startDateLabel.setText(dateFormat.format(bookingStartMs));
                    endDateLabel.setText(dateFormat.format(bookingEndMs));
                    updatePricePreview();
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(startOfDay(System.currentTimeMillis()));
        dialog.show();
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

    /** Multi-select service chips for the booking sheet. */
    private LinearLayout serviceChipRows() {
        return Ui.serviceChipGrid(this, sitter.getServices(), selectedServices, true);
    }

    /** Read-only wrapped service chips (taxonomy display names). */
    private LinearLayout skillChips() {
        return Ui.serviceChipGrid(this, sitter.getServices(),
                java.util.Collections.<String>emptySet(), false);
    }

    /**
     * Sitter availability for the next two weeks. Own profile gets an
     * editable strip with a save button; everyone else gets a read-only
     * strip with an honest open-days count.
     */
    private LinearLayout availabilitySection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(Ui.eyebrow(this, "Availability"));
        Ui.gap(section, this, 8);
        LinearLayout strip = Ui.column(this, 0);
        section.addView(strip);
        if (isOwnProfile()) {
            final AvailabilityStrip.Editor editor =
                    AvailabilityStrip.renderEditor(this, strip, sitter.getUnavailableDates());
            Ui.gap(section, this, 8);
            section.addView(Ui.caption(this,
                    "Tap days you're unavailable — filled days are blocked out."));
            Ui.gap(section, this, 8);
            Button saveButton = Ui.secondaryButton(this, "Save availability");
            saveButton.setOnClickListener(v -> saveAvailability(editor, saveButton));
            section.addView(saveButton);
        } else {
            AvailabilityStrip.renderView(this, strip, sitter.getUnavailableDates());
            Ui.gap(section, this, 8);
            section.addView(Ui.caption(this, availabilityCaption()));
        }
        return section;
    }

    /** "Open all of the next 14 days." or "9 of the next 14 days open." */
    private String availabilityCaption() {
        int busy = 0;
        for (int i = 0; i < AvailabilityStrip.DAYS; i++) {
            if (sitter.isUnavailableOn(AvailabilityStrip.isoForOffset(i))) {
                busy++;
            }
        }
        int open = AvailabilityStrip.DAYS - busy;
        return open == AvailabilityStrip.DAYS
                ? "Open all of the next " + AvailabilityStrip.DAYS + " days."
                : open + " of the next " + AvailabilityStrip.DAYS + " days open.";
    }

    private void saveAvailability(AvailabilityStrip.Editor editor, Button saveButton) {
        saveButton.setEnabled(false);
        java.util.List<String> days =
                new java.util.ArrayList<>(editor.getUnavailable());
        ApiProvider.get().setSitterAvailability(days, new GardenSwapApi.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                saveButton.setEnabled(true);
                Toast.makeText(SitterProfileActivity.this,
                        "Availability saved", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(ApiException e) {
                saveButton.setEnabled(true);
                Toast.makeText(SitterProfileActivity.this,
                        "Couldn't save availability (" + e.getCode() + ").",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** True when the profile being viewed belongs to the signed-in user. */
    private boolean isOwnProfile() {
        return sitter != null
                && FirebaseAuth.getInstance().getCurrentUser() != null
                && sitter.getSitterId()
                .equals(FirebaseAuth.getInstance().getCurrentUser().getUid());
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
        String[] offered = sitter.getServices();
        boolean offersAny = offered != null && offered.length > 0;
        if (offersAny && selectedServices.isEmpty()) {
            requesting = false;
            requestButton.setEnabled(true);
            statusText.setText("Pick at least one service for this booking.");
            return;
        }
        statusText.setText("Requesting booking…");
        long start = bookingStartMs;
        long end = bookingEndMs;
        String[] services = selectedServices.toArray(new String[0]);
        // The booking endpoint has no services field; fold the selection
        // into the notes so it reaches the sitter.
        String careText = care == null ? "" : care.trim();
        String notes = careText;
        if (services.length > 0) {
            StringBuilder names = new StringBuilder();
            for (String s : services) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(SitterServices.displayName(s));
            }
            String line = "Services requested: " + names;
            notes = careText.isEmpty() ? line : careText + "\n" + line;
            // Backend caps notes at 2000 chars: trim the care text, never
            // the services line.
            if (notes.length() > 2000) {
                int keep = 2000 - line.length() - 1;
                notes = keep > 0 && !careText.isEmpty()
                        ? careText.substring(0, Math.min(keep, careText.length()))
                        + "\n" + line
                        : line;
            }
        }
        BookingRequest req = new BookingRequest(sitter.getSitterId(), start, end,
                services, notes);
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
