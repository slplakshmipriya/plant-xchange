package com.gardenswap.test.sitters;

import android.app.AlertDialog;
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
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.Booking;
import com.gardenswap.test.api.BookingStatus;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.Review;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.ReviewFormLogic;
import com.gardenswap.test.util.ReviewGuard;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Post-booking review screen (AND-070, API-072), restyled per the design
 * prototype (UID-018).
 *
 * <p>Two-sided reviews are writable only after completion, once per side —
 * enforced server-side. This screen refuses to render the form unless the
 * booking reached {@link BookingStatus#COMPLETED}, so the UI can never
 * imply a review is possible early. Submit wiring (gating, validation,
 * API call, analytics) is unchanged from the pre-restyle screen.
 */
public class ReviewActivity extends AppCompatActivity {

    public static final String EXTRA_BOOKING_ID = "booking_id";
    public static final String EXTRA_BOOKING_STATUS = "booking_status";
    public static final String EXTRA_REVIEWER_ROLE = "reviewer_role";

    private static final String[] REVIEW_TAGS = {
            "On time", "As described", "Great communication", "Careful", "Flexible"
    };

    private TextView statusText;
    private TextView bookingCaption;
    private TextView[] starViews = new TextView[5];
    private TextView ratingLabel;
    private int rating;
    private boolean submitting;
    private final Set<String> selectedTags = new LinkedHashSet<>();
    private String reviewerRole = "OWNER";

    // Booking resolution (API-134): the review is attached to a completed
    // booking looked up via listBookings. The verified flag is only true
    // when the booking came from that API call.
    private String resolvedBookingId;
    private boolean bookingVerified;
    private boolean bookingLookupDone;
    private List<Booking> completedBookings;
    private boolean pendingSubmit;
    private int pendingRating;
    private String pendingText;
    private Button pendingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String bookingIdExtra = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        final String fallbackBookingId =
                bookingIdExtra == null ? "mock-booking-1" : bookingIdExtra;
        String statusWire = getIntent().getStringExtra(EXTRA_BOOKING_STATUS);
        BookingStatus status = BookingStatus.fromString(statusWire);
        String roleExtra = getIntent().getStringExtra(EXTRA_REVIEWER_ROLE);
        reviewerRole = roleExtra == null ? "OWNER" : roleExtra;

        LinearLayout root = Ui.column(this, 24);
        root.addView(Ui.eyebrow(this, "Post-booking review"));
        root.addView(Ui.headline(this, "Leave a review"));
        Ui.gap(root, this, 4);
        root.addView(Ui.body(this,
                "Share how the sit went. Reviews are public and help other gardeners."));
        Ui.gap(root, this, 8);
        bookingCaption = Ui.caption(this, "");
        root.addView(bookingCaption);
        Ui.gap(root, this, 8);
        statusText = Ui.status(this);
        root.addView(statusText);
        Ui.gap(root, this, 8);

        if (!ReviewGuard.canSubmitReview(status)) {
            // Gated: reviews unlock only after the sit is completed.
            LinearLayout card = Ui.card(this);
            card.addView(Ui.body(this,
                    "Reviews open after the sit is completed. "
                            + "You'll be prompted once it's done."));
            root.addView(card);
            setContentView(wrapInScroll(root));
            return;
        }

        root.addView(Ui.eyebrow(this, "Your rating"));
        Ui.gap(root, this, 4);
        // ratingLabel must exist before starRow(): the row's initializer calls
        // updateStars(), which writes the label.
        ratingLabel = Ui.caption(this, "");
        root.addView(starRow());
        root.addView(ratingLabel);
        Ui.gap(root, this, 12);

        root.addView(Ui.eyebrow(this, "Your review (optional)"));
        Ui.gap(root, this, 4);
        EditText textInput = Ui.input(this, "What went well? Any tips for next time?",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textInput.setMinLines(4);
        root.addView(textInput);
        Ui.gap(root, this, 12);

        root.addView(Ui.eyebrow(this, "Quick tags (optional)"));
        Ui.gap(root, this, 4);
        root.addView(tagChipRows());
        Ui.gap(root, this, 16);

        Button submit = Ui.primaryButton(this, "Submit review");
        submit.setOnClickListener(v -> submit(
                rating, textInput.getText().toString(), submit));
        root.addView(submit);

        resolveVerifiedBooking(fallbackBookingId);
        setContentView(wrapInScroll(root));
    }

    private ScrollView wrapInScroll(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    /** Five tappable stars; fills up to the chosen rating in garden yellow. */
    private LinearLayout starRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < starViews.length; i++) {
            final int stars = i + 1;
            TextView star = new TextView(this);
            star.setText("★");
            star.setTextSize(36);
            star.setGravity(Gravity.CENTER);
            int pad = Ui.dp(this, 6);
            star.setPadding(pad, pad, pad, pad);
            star.setClickable(true);
            star.setFocusable(true);
            star.setContentDescription("Rate " + stars + " star" + (stars == 1 ? "" : "s"));
            star.setOnClickListener(v -> {
                rating = stars;
                updateStars();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            star.setLayoutParams(params);
            starViews[i] = star;
            row.addView(star);
        }
        updateStars();
        return row;
    }

    private void updateStars() {
        int filled = ResourcesCompat.getColor(getResources(),
                R.color.garden_yellow, getTheme());
        int empty = ResourcesCompat.getColor(getResources(),
                R.color.garden_muted, getTheme());
        for (int i = 0; i < starViews.length; i++) {
            starViews[i].setTextColor(i < rating ? filled : empty);
            starViews[i].setAlpha(i < rating ? 1f : 0.45f);
        }
        ratingLabel.setText(ReviewFormLogic.ratingLabel(rating));
    }

    /** Multi-select tag chips, wrapped into rows by estimated width. */
    private LinearLayout tagChipRows() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        float density = getResources().getDisplayMetrics().density;
        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels / density) - 48;
        LinearLayout row = chipRow();
        column.addView(row);
        int used = 0;
        for (String tag : REVIEW_TAGS) {
            int estimate = tag.length() * 8 + 48;
            if (used > 0 && used + estimate > maxWidth) {
                Ui.gap(column, this, 8);
                row = chipRow();
                column.addView(row);
                used = 0;
            }
            TextView chip = Ui.chip(this, tag);
            chip.setOnClickListener(v -> toggleTag(chip, tag));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(Ui.dp(this, 8));
            chip.setLayoutParams(params);
            row.addView(chip);
            used += estimate;
        }
        return column;
    }

    private LinearLayout chipRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void toggleTag(TextView chip, String tag) {
        boolean now = !selectedTags.contains(tag);
        if (now) {
            selectedTags.add(tag);
        } else {
            selectedTags.remove(tag);
        }
        Ui.setChipSelected(this, chip, now);
    }

    /**
     * Resolves the completed booking this review attaches to. The reviewer is
     * the booking's owner side (reviewerRole), so bookings are listed with
     * that role filtered to completed ones. One booking resolves silently;
     * several wait for an explicit pick at submit time; lookup failure falls
     * back to the caller-supplied id with no verified flag.
     */
    private void resolveVerifiedBooking(final String fallbackId) {
        bookingCaption.setText("Checking completed bookings…");
        String role = reviewerRole == null ? "owner"
                : reviewerRole.toLowerCase(Locale.US);
        ApiProvider.get().listBookings(role, true,
                new GardenSwapApi.Callback<List<Booking>>() {
                    @Override
                    public void onSuccess(List<Booking> bookings) {
                        completedBookings = new ArrayList<>();
                        if (bookings != null) {
                            for (Booking booking : bookings) {
                                if (booking != null
                                        && booking.getStatus() == BookingStatus.COMPLETED) {
                                    completedBookings.add(booking);
                                }
                            }
                        }
                        bookingLookupDone = true;
                        if (completedBookings.size() == 1) {
                            Booking only = completedBookings.get(0);
                            resolvedBookingId = only.getBookingId();
                            bookingVerified = true;
                            bookingCaption.setText("✓ Verified completed booking: "
                                    + only.getBookingId());
                        } else if (completedBookings.size() > 1) {
                            bookingCaption.setText(completedBookings.size()
                                    + " completed bookings found — you'll pick one "
                                    + "when you submit.");
                        } else {
                            useFallbackBooking(fallbackId,
                                    "No completed bookings found — "
                                            + "submitting as an unverified review.");
                        }
                        if (pendingSubmit) {
                            pendingSubmit = false;
                            continueSubmit();
                        }
                    }

                    @Override
                    public void onError(ApiException e) {
                        // Endpoint not available yet (404 until the backend
                        // lands): keep the previous hardcoded behavior, never
                        // crash.
                        bookingLookupDone = true;
                        useFallbackBooking(fallbackId,
                                "Booking history unavailable — "
                                        + "submitting as an unverified review.");
                        Toast.makeText(ReviewActivity.this,
                                "Couldn't load booking history (" + e.getCode() + ")",
                                Toast.LENGTH_LONG).show();
                        if (pendingSubmit) {
                            pendingSubmit = false;
                            continueSubmit();
                        }
                    }
                });
    }

    private void useFallbackBooking(String fallbackId, String caption) {
        resolvedBookingId = fallbackId;
        bookingVerified = false;
        bookingCaption.setText(caption);
    }

    private void submit(int rating, String text, Button submitButton) {
        if (!ReviewGuard.isValidRating(rating)) {
            statusText.setText("Tap a star rating from 1 to 5.");
            return;
        }
        if (!text.trim().isEmpty() && !ReviewGuard.isValidText(text)) {
            statusText.setText("Review text is too long (max 2000 characters).");
            return;
        }
        if (submitting) {
            return;
        }
        pendingRating = rating;
        pendingText = text;
        pendingButton = submitButton;
        if (!bookingLookupDone) {
            // Lookup is still in flight; submit once it resolves.
            statusText.setText("Finding your completed booking…");
            pendingSubmit = true;
            return;
        }
        continueSubmit();
    }

    private void continueSubmit() {
        if (completedBookings != null && completedBookings.size() > 1
                && resolvedBookingId == null) {
            showBookingPicker();
            return;
        }
        if (resolvedBookingId == null) {
            resolvedBookingId = "mock-booking-1";
        }
        doSubmit(resolvedBookingId, pendingRating, pendingText, pendingButton);
    }

    /** Simple single-choice dialog: booking id + status + dates per row. */
    private void showBookingPicker() {
        final SimpleDateFormat dayFormat =
                new SimpleDateFormat("MMM d", Locale.US);
        String[] labels = new String[completedBookings.size()];
        for (int i = 0; i < completedBookings.size(); i++) {
            Booking booking = completedBookings.get(i);
            labels[i] = booking.getBookingId() + " · "
                    + booking.getStatus().name() + " · "
                    + datesSummary(booking, dayFormat);
        }
        new AlertDialog.Builder(this)
                .setTitle("Which booking is this review for?")
                .setItems(labels, (d, which) -> {
                    Booking chosen = completedBookings.get(which);
                    resolvedBookingId = chosen.getBookingId();
                    bookingVerified = true;
                    bookingCaption.setText("✓ Verified completed booking: "
                            + chosen.getBookingId());
                    doSubmit(resolvedBookingId, pendingRating, pendingText, pendingButton);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * "3 days (Sep 1 – Sep 5)" / "1 day (Sep 1)" — the booking's explicit
     * dates list, rendered as a count plus its span.
     */
    private static String datesSummary(Booking booking, SimpleDateFormat dayFormat) {
        java.util.List<String> dates = booking.getDates();
        if (dates.isEmpty()) {
            return "no dates";
        }
        String first = formatIsoDate(dates.get(0), dayFormat);
        String last = formatIsoDate(dates.get(dates.size() - 1), dayFormat);
        String dayWord = dates.size() == 1 ? "day" : "days";
        return dates.size() + " " + dayWord + " ("
                + (dates.size() == 1 ? first : first + " – " + last) + ")";
    }

    /** "2026-09-20" -> "Sep 20" via the picker's day format; best effort. */
    private static String formatIsoDate(String iso, SimpleDateFormat dayFormat) {
        try {
            java.util.Date d = new SimpleDateFormat("yyyy-MM-dd",
                    java.util.Locale.US).parse(iso);
            return d == null ? iso : dayFormat.format(d);
        } catch (java.text.ParseException e) {
            return iso;
        }
    }

    private void doSubmit(String bookingId, int rating, String text, Button submitButton) {
        if (submitting) {
            return;
        }
        submitting = true;
        submitButton.setEnabled(false);
        statusText.setText("Submitting…");
        Review review = Review.builder(rating)
                .tags(selectedTags.toArray(new String[0]))
                .text(text.trim())
                .reviewerRole(reviewerRole)
                .build();
        ApiProvider.get().submitReview(bookingId, review, new GardenSwapApi.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                statusText.setText("Thanks! Your review is live.");
                submitButton.setVisibility(View.GONE);
                FirebaseAnalytics.getInstance(ReviewActivity.this)
                        .logEvent("review_submitted", null);
            }

            @Override
            public void onError(ApiException e) {
                submitting = false;
                submitButton.setEnabled(true);
                statusText.setText("");
                Toast.makeText(ReviewActivity.this,
                        bookingVerified ? "Couldn't submit your review — please try again"
                                : "Couldn't submit review — booking history unavailable",
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
