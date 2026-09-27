package com.gardenswap.app.sitters;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.BookingStatus;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.Review;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.ReviewGuard;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Post-booking review screen (AND-070, API-072).
 *
 * <p>Two-sided reviews are writable only after completion, once per side —
 * enforced server-side. This screen refuses to render the form unless the
 * booking reached {@link BookingStatus#COMPLETED}, so the UI can never
 * imply a review is possible early.
 */
public class ReviewActivity extends AppCompatActivity {

    public static final String EXTRA_BOOKING_ID = "booking_id";
    public static final String EXTRA_BOOKING_STATUS = "booking_status";

    private TextView statusText;
    private boolean submitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        String statusWire = getIntent().getStringExtra(EXTRA_BOOKING_STATUS);
        BookingStatus status = BookingStatus.fromString(statusWire);

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Leave a review");
        title.setTextSize(20);
        statusText = Ui.status(this);
        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(statusText);
        Ui.gap(root, this, 8);

        if (!ReviewGuard.canSubmitReview(status)) {
            // Gated: reviews unlock only after the sit is completed.
            root.addView(Ui.label(this,
                    "Reviews open after the sit is completed. "
                            + "You'll be prompted once it's done."));
            setContentView(root);
            return;
        }

        EditText ratingInput = Ui.input(this, "Rating (1–5)", InputType.TYPE_CLASS_NUMBER);
        root.addView(ratingInput);
        Ui.gap(root, this, 4);
        EditText textInput = Ui.input(this, "What went well? (optional)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(textInput);
        Ui.gap(root, this, 8);

        Button submit = Ui.button(this, "Submit review");
        submit.setOnClickListener(v -> submit(
                bookingId == null ? "mock-booking-1" : bookingId,
                ratingInput.getText().toString(),
                textInput.getText().toString(), submit));
        root.addView(submit);
        setContentView(root);
    }

    private void submit(String bookingId, String ratingRaw, String text, Button submit) {
        int rating;
        try {
            rating = Integer.parseInt(ratingRaw.trim());
        } catch (NumberFormatException e) {
            statusText.setText("Enter a rating from 1 to 5.");
            return;
        }
        if (!ReviewGuard.isValidRating(rating)) {
            statusText.setText("Enter a rating from 1 to 5.");
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
        Review review = new Review(rating, new String[0], text.trim());
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
                statusText.setText("Couldn't submit (" + e.getCode() + "). Try again.");
            }
        });
    }
}
