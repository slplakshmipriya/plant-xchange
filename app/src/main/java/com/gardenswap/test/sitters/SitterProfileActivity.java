package com.gardenswap.test.sitters;

import android.app.AlertDialog;
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
import com.gardenswap.test.ui.BookingDateGrid;
import com.gardenswap.test.ui.SitterCardView;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.ui.VerifiedBadgeView;
import com.gardenswap.test.util.SitterLogic;
import com.gardenswap.test.util.SitterServices;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sitter profile + booking sheet (AND-070), restyled per the design
 * prototype (UID-017).
 *
 * <p>Header card (avatar, name, verification badge), skills section,
 * reviews list, then the booking sheet. The booking sheet captures the
 * sitter's available dates (picked individually from a 30-day grid with a
 * green-tick highlight), services, and per-plant care instructions, and
 * shows price math with no customer-facing fee (the sitter covers the 18%
 * platform fee; display only — the server computes the charge). Payment
 * hold happens on confirm server-side (API-070); this screen only requests
 * the booking.
 */
public class SitterProfileActivity extends AppCompatActivity {

    public static final String EXTRA_SITTER_ID = "sitter_id";

    private TextView statusText;
    private LinearLayout content;
    private SitterProfile sitter;
    private boolean requesting;
    /** ISO dates (yyyy-MM-dd) the booker picked from the 30-day grid. */
    private final Set<String> selectedDates = new LinkedHashSet<>();
    private BookingDateGrid.Grid bookingGrid;
    private TextView pricePreview;
    private Button requestButton;
    private final Set<String> selectedServices = new LinkedHashSet<>();

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

        // Your own profile: edit the advertised services too
        // (PUT /v1/sitters/me, services-only body).
        if (isOwnProfile()) {
            content.addView(servicesEditorSection());
            Ui.gap(content, this, 16);
        }

        content.addView(Ui.eyebrow(this, "Reviews"));
        Ui.gap(content, this, 8);
        LinearLayout reviewsSection = Ui.column(this, 0);
        content.addView(reviewsSection);
        loadReviews(reviewsSection);
        Ui.gap(content, this, 8);

        // You can't review yourself: the button is hidden on your own
        // profile. ReviewActivity resolves the real completed booking via
        // GET /v1/bookings (role + completed filter); the id passed here is
        // only the fallback if that endpoint is not available yet.
        if (!isOwnProfile()) {
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
        }

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
        String dateLine = reviewDateLine(review.getCreatedAt());
        if (dateLine != null) {
            Ui.gap(card, this, 2);
            card.addView(Ui.caption(this, dateLine));
        }
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
     * Real reviews from GET /v1/sitters/{uid}/reviews, rendered newest
     * first. The backend already orders latest-first; the client re-sorts
     * defensively so mock and real sources behave identically.
     */
    private void loadReviews(final LinearLayout section) {
        ApiProvider.get().getSitterReviews(sitter.getSitterId(),
                new GardenSwapApi.Callback<List<Review>>() {
                    @Override
                    public void onSuccess(List<Review> reviews) {
                        List<Review> sorted = new ArrayList<>(reviews);
                        Collections.sort(sorted, new Comparator<Review>() {
                            @Override
                            public int compare(Review a, Review b) {
                                return compareNewestFirst(a, b);
                            }
                        });
                        section.removeAllViews();
                        if (sorted.isEmpty()) {
                            section.addView(Ui.caption(SitterProfileActivity.this,
                                    "No reviews yet."));
                            return;
                        }
                        for (Review review : sorted) {
                            section.addView(reviewCard(review));
                            Ui.gap(section, SitterProfileActivity.this, 8);
                        }
                    }

                    @Override
                    public void onError(ApiException e) {
                        section.removeAllViews();
                        section.addView(Ui.caption(SitterProfileActivity.this,
                                "Couldn't load reviews."));
                    }
                });
    }

    /** Newest first by ISO-8601 created_at; undated reviews sink to the end. */
    private static int compareNewestFirst(Review a, Review b) {
        String ca = a.getCreatedAt();
        String cb = b.getCreatedAt();
        if (ca == null && cb == null) {
            return 0;
        }
        if (ca == null) {
            return 1;
        }
        if (cb == null) {
            return -1;
        }
        return cb.compareTo(ca);
    }

    /** "2026-09-20T14:05:00+00:00" -> "Sep 20, 2026"; null when unparseable. */
    private static String reviewDateLine(String iso) {
        if (iso == null || iso.length() < 10) {
            return null;
        }
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .parse(iso.substring(0, 10));
            return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(date);
        } catch (ParseException e) {
            return null;
        }
    }

    // ---- Booking sheet: explicit date picks, no start/end range ----

    private void renderBookingSheet() {
        // You can't book yourself: when this screen shows your own profile
        // (reached via "Your sitter profile"), the booking section is
        // omitted entirely.
        if (isOwnProfile()) {
            return;
        }
        // Reset per render: nothing picked, all advertised services selected.
        selectedDates.clear();
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

        // Date picker: 30-day grid bound to the sitter's available dates.
        // Only available dates are tappable; a green tick marks selection
        // (distinct from the blue outline of the availability strip above).
        content.addView(Ui.eyebrow(this, "Dates"));
        Ui.gap(content, this, 4);
        LinearLayout gridHolder = Ui.column(this, 0);
        content.addView(gridHolder);
        bookingGrid = BookingDateGrid.render(this, gridHolder,
                sitter.getAvailableDates(), null, this::updateBookingState);
        Ui.gap(content, this, 4);
        content.addView(Ui.caption(this,
                "Only days the sitter marked available can be picked — a "
                        + "green tick means selected."));
        Ui.gap(content, this, 8);

        content.addView(Ui.eyebrow(this, "Services"));
        Ui.gap(content, this, 4);
        if (sitterOffersAny()) {
            content.addView(serviceChipRows());
        } else {
            content.addView(Ui.body(this, "Services on request"));
        }
        Ui.gap(content, this, 8);

        EditText careInput = Ui.input(this,
                "Care instructions (per-plant notes)", InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        content.addView(careInput);
        Ui.gap(content, this, 8);

        // Price preview for the picked dates: days × daily rate. No
        // platform-fee line — the customer pays the subtotal only; the
        // sitter covers the 18% fee (display only — the server computes the
        // charge). Updates live as dates/services change.
        pricePreview = Ui.label(this, "");
        content.addView(pricePreview);
        if (sitter.getRateAmount() != null && sitter.getRateUnit() != null) {
            Ui.gap(content, this, 2);
            content.addView(Ui.caption(this,
                    "Sitter covers the 18% platform fee."));
        }
        Ui.gap(content, this, 8);
        updateBookingState();

        requestButton = Ui.primaryButton(this, "Request booking");
        requestButton.setOnClickListener(v ->
                request(careInput.getText().toString(), requestButton));
        content.addView(requestButton);
    }

    /** True when the sitter advertises at least one service. */
    private boolean sitterOffersAny() {
        String[] offered = sitter.getServices();
        if (offered == null) {
            return false;
        }
        for (String s : offered) {
            if (s != null && !s.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Refresh the price preview and the request button's enabled state. */
    private void updateBookingState() {
        if (bookingGrid != null) {
            selectedDates.clear();
            selectedDates.addAll(bookingGrid.getSelected());
        }
        int days = selectedDates.size();
        if (pricePreview != null) {
            pricePreview.setText(SitterLogic.bookingPreview(
                    sitter.getRateAmount(), sitter.getRateUnit(), days));
        }
        if (requestButton != null) {
            boolean servicesOk = !sitterOffersAny() || !selectedServices.isEmpty();
            requestButton.setEnabled(days >= 1 && servicesOk && !requesting);
        }
    }

    /** Multi-select service chips for the booking sheet. */
    private LinearLayout serviceChipRows() {
        return Ui.serviceChipGrid(this, sitter.getServices(), selectedServices,
                true, this::updateBookingState);
    }

    /** Read-only wrapped service chips (taxonomy display names). */
    private LinearLayout skillChips() {
        return Ui.serviceChipGrid(this, sitter.getServices(),
                java.util.Collections.<String>emptySet(), false);
    }

    /**
     * Sitter availability for the next two weeks. Own profile gets an
     * editable strip with a save button; everyone else gets a read-only
     * strip with an honest available-days count.
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
                    AvailabilityStrip.renderEditor(this, strip, sitter.getAvailableDates());
            Ui.gap(section, this, 8);
            section.addView(Ui.caption(this,
                    "Tap the days you're free for plant sitting — a blue outline "
                            + "means you're available that day."));
            Ui.gap(section, this, 8);
            Button saveButton = Ui.secondaryButton(this, "Save availability");
            saveButton.setOnClickListener(v -> saveAvailability(editor, saveButton));
            section.addView(saveButton);
        } else {
            AvailabilityStrip.renderView(this, strip, sitter.getAvailableDates());
            Ui.gap(section, this, 8);
            section.addView(Ui.caption(this, availabilityCaption()));
        }
        return section;
    }

    /** "Available 9 of the next 14 days." / "No availability marked…". */
    private String availabilityCaption() {
        int open = 0;
        for (int i = 0; i < AvailabilityStrip.DAYS; i++) {
            if (sitter.isAvailableOn(AvailabilityStrip.isoForOffset(i))) {
                open++;
            }
        }
        return open == 0
                ? "No availability marked for the next " + AvailabilityStrip.DAYS + " days."
                : "Available " + open + " of the next " + AvailabilityStrip.DAYS + " days.";
    }

    private void saveAvailability(AvailabilityStrip.Editor editor, Button saveButton) {
        saveButton.setEnabled(false);
        java.util.List<String> days =
                new java.util.ArrayList<>(editor.getAvailable());
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

    /**
     * Own-profile services editor: multi-select chips over the fixed
     * taxonomy (same pattern as BecomeSitter), wired to
     * {@code PUT /v1/sitters/me}. Empty = "on request". Shown only on your
     * own profile.
     */
    private LinearLayout servicesEditorSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(Ui.eyebrow(this, "Services you offer"));
        Ui.gap(section, this, 8);

        final Set<String> editing = new LinkedHashSet<>();
        final Set<String> original = new LinkedHashSet<>();
        String[] current = sitter.getServices();
        if (current != null) {
            for (String s : current) {
                if (s != null && !s.trim().isEmpty()) {
                    editing.add(s.trim());
                    original.add(s.trim());
                }
            }
        }
        final Button saveButton = Ui.secondaryButton(this, "Save services");
        saveButton.setEnabled(false);
        section.addView(Ui.serviceChipGrid(this, SitterServices.KEYS, editing,
                true, () -> saveButton.setEnabled(!editing.equals(original))));
        Ui.gap(section, this, 4);
        section.addView(Ui.caption(this,
                "Bookers can only request the services you pick here. "
                        + "Leave all off for \"on request\"."));
        Ui.gap(section, this, 8);
        saveButton.setOnClickListener(v -> {
            saveButton.setEnabled(false);
            String[] services = editing.toArray(new String[0]);
            ApiProvider.get().updateSitterServices(services,
                    new GardenSwapApi.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            original.clear();
                            original.addAll(editing);
                            Toast.makeText(SitterProfileActivity.this,
                                    "Services saved", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(ApiException e) {
                            saveButton.setEnabled(true);
                            Toast.makeText(SitterProfileActivity.this,
                                    "Couldn't save services (" + e.getCode() + ").",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
        section.addView(saveButton);
        return section;
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
        // The button is gated on these, but the IDV check is async — the
        // sitter's availability or the selection could have changed since.
        List<String> dates = new ArrayList<>(selectedDates);
        Collections.sort(dates);
        boolean servicesOk = !sitterOffersAny() || !selectedServices.isEmpty();
        if (dates.isEmpty() || !servicesOk) {
            requesting = false;
            requestButton.setEnabled(true);
            statusText.setText(dates.isEmpty()
                    ? "Pick at least one date for this booking."
                    : "Pick at least one service for this booking.");
            updateBookingState();
            return;
        }
        statusText.setText("Requesting booking…");
        String[] services = selectedServices.toArray(new String[0]);
        // Services now ride as a first-class wire field; notes carry only
        // the care instructions (backend caps notes at 2000 chars).
        String notes = care == null ? "" : care.trim();
        if (notes.length() > 2000) {
            notes = notes.substring(0, 2000);
        }
        BookingRequest req = new BookingRequest(sitter.getSitterId(), dates,
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
