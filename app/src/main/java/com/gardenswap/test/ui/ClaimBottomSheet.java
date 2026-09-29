package com.gardenswap.test.ui;

import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.R;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.ClaimRequest;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.util.ClaimSheetLogic;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Calendar;
import java.util.Locale;

/**
 * Claim bottom sheet (UID-013). Replaces the UID-012 stub in
 * {@code ListingDetailActivity#openClaimSheet()}.
 *
 * <p>The sheet collects quantity (stepper, min 1, capped at the listing's
 * available quantity when known), a pickup window (preset chips), and an
 * optional note for the giver. Confirm calls {@link GardenSwapApi#claimListing(String,
 * ClaimRequest, GardenSwapApi.Callback)} — the quantity stepper, selected
 * pickup window, and note now travel with the request (contract API-135).
 * On success the sheet dismisses, a "Claimed!" toast shows, and the
 * optional listener receives the updated listing so the caller can
 * re-render (mirroring the old {@code listing = result; render();}).
 */
public final class ClaimBottomSheet {

    /** Receives the updated listing after a successful claim. */
    public interface OnClaimedListener {
        void onClaimed(Listing listing);
    }

    private ClaimBottomSheet() {
    }

    public static void show(AppCompatActivity activity, String listingId) {
        show(activity, listingId, null);
    }

    public static void show(AppCompatActivity activity, String listingId,
                            OnClaimedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        LinearLayout root = Ui.column(activity, 24);

        TextView status = Ui.status(activity);
        Ui.textColor(activity, status, R.color.garden_sheet_muted);
        status.setText("Loading\u2026");
        root.addView(status);

        // Fetch the listing so the stepper caps at the available quantity.
        ApiProvider.get().getListing(listingId, new GardenSwapApi.Callback<Listing>() {
            @Override
            public void onSuccess(Listing listing) {
                root.removeAllViews();
                root.addView(buildForm(activity, dialog, listing, listener));
            }

            @Override
            public void onError(ApiException e) {
                status.setText("Could not load listing: " + e.getMessage());
            }
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private static LinearLayout buildForm(AppCompatActivity activity,
                                          BottomSheetDialog dialog,
                                          Listing listing,
                                          OnClaimedListener listener) {
        LinearLayout form = Ui.column(activity, 0);

        // The sheet always uses the fixed Light dialog theme while the host
        // activity may be in dark mode, so every text/background color here is
        // pinned to the night-independent garden_sheet_* tokens.
        TextView title = Ui.headline(activity, "Claim this listing");
        Ui.textColor(activity, title, R.color.garden_sheet_ink);
        form.addView(title);
        Ui.gap(form, activity, 4);
        String variety = listing.getVariety() == null ? "Listing" : listing.getVariety();
        TextView subtitle = Ui.caption(activity, variety
                + " \u2014 the giver will be notified. Credits move when you both "
                + "confirm the exchange.");
        Ui.textColor(activity, subtitle, R.color.garden_sheet_muted);
        form.addView(subtitle);
        Ui.gap(form, activity, 16);

        // Quantity stepper.
        Double available = listing.getQuantity();
        final double[] qty = {ClaimSheetLogic.clampQuantity(1, available)};
        TextView qtyEyebrow = Ui.eyebrow(activity, "Quantity");
        Ui.textColor(activity, qtyEyebrow, R.color.garden_sheet_ink);
        form.addView(qtyEyebrow);
        Ui.gap(form, activity, 8);
        LinearLayout stepper = new LinearLayout(activity);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);

        Button minus = Ui.secondaryButton(activity, "\u2212");
        minus.setBackgroundResource(R.drawable.btn_sheet_secondary);
        Ui.textColor(activity, minus, R.color.garden_sheet_leaf);
        minus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button plus = Ui.secondaryButton(activity, "+");
        plus.setBackgroundResource(R.drawable.btn_sheet_secondary);
        Ui.textColor(activity, plus, R.color.garden_sheet_leaf);
        plus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView qtyView = Ui.title(activity, formatQty(qty[0], listing.getUnit()));
        Ui.textColor(activity, qtyView, R.color.garden_sheet_ink);
        qtyView.setGravity(Gravity.CENTER);
        qtyView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        minus.setOnClickListener(v -> {
            qty[0] = ClaimSheetLogic.clampQuantity(qty[0] - 1, available);
            qtyView.setText(formatQty(qty[0], listing.getUnit()));
        });
        plus.setOnClickListener(v -> {
            qty[0] = ClaimSheetLogic.clampQuantity(qty[0] + 1, available);
            qtyView.setText(formatQty(qty[0], listing.getUnit()));
        });
        stepper.addView(minus);
        stepper.addView(qtyView);
        stepper.addView(plus);
        form.addView(stepper);
        Ui.gap(form, activity, 16);

        // Pickup window chips (single-select).
        TextView pickupEyebrow = Ui.eyebrow(activity, "Pickup window");
        Ui.textColor(activity, pickupEyebrow, R.color.garden_sheet_ink);
        form.addView(pickupEyebrow);
        Ui.gap(form, activity, 8);
        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] chipViews = new TextView[ClaimSheetLogic.PICKUP_OPTIONS.length];
        final int[] pickupIndex = {0};
        for (int i = 0; i < chipViews.length; i++) {
            final int index = i;
            TextView chip = Ui.chip(activity, ClaimSheetLogic.PICKUP_OPTIONS[i]);
            chip.setClickable(true);
            chip.setFocusable(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(Ui.dp(activity, 8));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                pickupIndex[0] = index;
                for (int j = 0; j < chipViews.length; j++) {
                    setSheetChipSelected(activity, chipViews[j], j == index);
                }
            });
            chipViews[i] = chip;
            chips.addView(chip);
        }
        setSheetChipSelected(activity, chipViews[0], true);
        form.addView(chips);
        Ui.gap(form, activity, 16);

        // Optional note.
        TextView noteEyebrow = Ui.eyebrow(activity, "Note for the giver \u00b7 optional");
        Ui.textColor(activity, noteEyebrow, R.color.garden_sheet_ink);
        form.addView(noteEyebrow);
        Ui.gap(form, activity, 8);
        EditText notes = Ui.input(activity, "e.g. I can pick up after 5pm",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Ui.textColor(activity, notes, R.color.garden_sheet_ink);
        notes.setHintTextColor(activity.getResources().getColor(
                R.color.garden_sheet_muted, activity.getTheme()));
        form.addView(notes);
        Ui.gap(form, activity, 8);

        TextView error = Ui.status(activity);
        Ui.textColor(activity, error, R.color.garden_sheet_red);
        form.addView(error);
        Ui.gap(form, activity, 8);

        Button claim = Ui.primaryButton(activity, "Claim");
        claim.setBackgroundResource(R.drawable.btn_sheet_primary);
        claim.setTextColor(activity.getResources().getColor(
                android.R.color.white, activity.getTheme()));
        claim.setOnClickListener(v -> {
            String noteText = notes.getText() == null ? "" : notes.getText().toString().trim();
            if (!ClaimSheetLogic.validQuantity(qty[0], available)) {
                error.setText("Quantity must be between 1 and the available amount.");
                return;
            }
            if (!ClaimSheetLogic.validNotes(noteText)) {
                error.setText("Note is too long (max "
                        + ClaimSheetLogic.MAX_NOTES_LENGTH + " characters).");
                return;
            }
            error.setText("");
            claim.setEnabled(false); // no double-tap; same guard spirit as the old dialog.
            // ClaimRequest.quantity is whole units/kilos; the stepper is integral
            // by construction, so truncate (never round up past the available).
            long[] window = pickupWindowMs(pickupIndex[0]);
            ClaimRequest request = new ClaimRequest(
                    (int) qty[0],
                    window[0],
                    window[1],
                    noteText.isEmpty() ? null : noteText);
            ApiProvider.get().claimListing(listing.getId(), request,
                    new GardenSwapApi.Callback<Listing>() {
                        @Override
                        public void onSuccess(Listing result) {
                            dialog.dismiss();
                            Toast.makeText(activity, "Claimed!", Toast.LENGTH_SHORT).show();
                            if (listener != null) {
                                listener.onClaimed(result);
                            }
                        }

                        @Override
                        public void onError(ApiException e) {
                            claim.setEnabled(true);
                            String message = e.getMessage();
                            error.setText(message == null || message.trim().isEmpty()
                                    ? "Couldn't place the claim. Try again."
                                    : message);
                        }
                    });
        });
        form.addView(claim);

        return form;
    }

    /**
     * Sheet-local chip selection with night-independent colors. The shared
     * {@link Ui#setChipSelected} resolves theme colors that go light in dark
     * mode, which is unreadable on this always-light sheet.
     */
    private static void setSheetChipSelected(AppCompatActivity activity,
                                             TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.chip_sheet_selected : R.drawable.chip_sheet);
        Ui.textColor(activity, chip, selected
                ? android.R.color.white : R.color.garden_sheet_ink);
    }

    /**
     * Resolves a pickup-window chip index ({@link ClaimSheetLogic#PICKUP_OPTIONS})
     * to a day-boundary [startMs, endMs] epoch window:
     * "Today" is today 00:00–23:59, "Tomorrow" is the same for tomorrow, and
     * "This weekend" is the upcoming Saturday 00:00 through Sunday 23:59
     * (today→Sunday when today is Saturday, today only when Sunday).
     */
    private static long[] pickupWindowMs(int index) {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        int dayOfWeek = start.get(Calendar.DAY_OF_WEEK);
        if (index == 1) { // Tomorrow
            start.add(Calendar.DAY_OF_YEAR, 1);
            end.add(Calendar.DAY_OF_YEAR, 1);
        } else if (index == 2) { // This weekend
            if (dayOfWeek == Calendar.SUNDAY) {
                // Window is today only; end stays on today.
            } else {
                int daysToSaturday = Calendar.SATURDAY - dayOfWeek; // 1..6, 0 on Saturday
                start.add(Calendar.DAY_OF_YEAR, daysToSaturday);
                end.add(Calendar.DAY_OF_YEAR, daysToSaturday + 1);
            }
        }
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    private static String formatQty(double qty, String unit) {        String number = qty == Math.floor(qty)
                ? String.valueOf((int) qty)
                : String.format(Locale.US, "%.1f", qty);
        return unit == null || unit.trim().isEmpty() ? number : number + " " + unit;
    }
}
