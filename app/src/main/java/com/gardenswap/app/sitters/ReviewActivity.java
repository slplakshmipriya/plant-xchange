package com.gardenswap.app.sitters;

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

import com.gardenswap.app.R;
import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.BookingStatus;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.Review;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.ReviewFormLogic;
import com.gardenswap.app.util.ReviewGuard;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.LinkedHashSet;
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
    private TextView[] starViews = new TextView[5];
    private TextView ratingLabel;
    private int rating;
    private boolean submitting;
    private final Set<String> selectedTags = new LinkedHashSet<>();
    private String reviewerRole = "OWNER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
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
                bookingId == null ? "mock-booking-1" : bookingId,
                rating, textInput.getText().toString(), submit));
        root.addView(submit);

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

    private void submit(String bookingId, int rating, String text, Button submit) {
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
        submitting = true;
        submit.setEnabled(false);
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
                submit.setVisibility(View.GONE);
                FirebaseAnalytics.getInstance(ReviewActivity.this)
                        .logEvent("review_submitted", null);
            }

            @Override
            public void onError(ApiException e) {
                submitting = false;
                submit.setEnabled(true);
                // The booking id is still the mock-phase placeholder until
                // API-134 lands; surface the failure instead of failing silently.
                statusText.setText("");
                Toast.makeText(ReviewActivity.this,
                        "Couldn't submit review — booking history unavailable",
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
