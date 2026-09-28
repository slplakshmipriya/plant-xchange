package com.gardenswap.app.ui;

import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.Listing;
import com.gardenswap.app.util.ClaimSheetLogic;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

/**
 * Claim bottom sheet (UID-013). Replaces the UID-012 stub in
 * {@code ListingDetailActivity#openClaimSheet()}.
 *
 * <p>The sheet collects quantity (stepper, min 1, capped at the listing's
 * available quantity when known), a pickup window (preset chips), and an
 * optional note for the giver. Confirm calls
 * {@link GardenSwapApi#claimListing(String, GardenSwapApi.Callback)} exactly
 * as the pre-012 dialog did — the claim contract is unchanged, so quantity,
 * pickup window and notes are sheet-only for now and travel with the
 * claimer, not the request. On success the sheet dismisses, a
 * "Claimed!" toast shows, and the optional listener receives the updated
 * listing so the caller can re-render (mirroring the old
 * {@code listing = result; render();}).
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

        form.addView(Ui.headline(activity, "Claim this listing"));
        Ui.gap(form, activity, 4);
        String variety = listing.getVariety() == null ? "Listing" : listing.getVariety();
        form.addView(Ui.caption(activity, variety
                + " \u2014 the giver will be notified. Credits move when you both "
                + "confirm the exchange."));
        Ui.gap(form, activity, 16);

        // Quantity stepper.
        Double available = listing.getQuantity();
        final double[] qty = {ClaimSheetLogic.clampQuantity(1, available)};
        form.addView(Ui.eyebrow(activity, "Quantity"));
        Ui.gap(form, activity, 8);
        LinearLayout stepper = new LinearLayout(activity);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);

        Button minus = Ui.secondaryButton(activity, "\u2212");
        minus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button plus = Ui.secondaryButton(activity, "+");
        plus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView qtyView = Ui.title(activity, formatQty(qty[0], listing.getUnit()));
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
        form.addView(Ui.eyebrow(activity, "Pickup window"));
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
                    Ui.setChipSelected(activity, chipViews[j], j == index);
                }
            });
            chipViews[i] = chip;
            chips.addView(chip);
        }
        Ui.setChipSelected(activity, chipViews[0], true);
        form.addView(chips);
        Ui.gap(form, activity, 16);

        // Optional note.
        form.addView(Ui.eyebrow(activity, "Note for the giver \u00b7 optional"));
        Ui.gap(form, activity, 8);
        EditText notes = Ui.input(activity, "e.g. I can pick up after 5pm",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        form.addView(notes);
        Ui.gap(form, activity, 8);

        TextView error = Ui.status(activity);
        form.addView(error);
        Ui.gap(form, activity, 8);

        Button claim = Ui.primaryButton(activity, "Claim");
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
            ApiProvider.get().claimListing(listing.getId(),
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
                            error.setText(e.getMessage());
                        }
                    });
        });
        form.addView(claim);

        return form;
    }

    private static String formatQty(double qty, String unit) {
        String number = qty == Math.floor(qty)
                ? String.valueOf((int) qty)
                : String.format(Locale.US, "%.1f", qty);
        return unit == null || unit.trim().isEmpty() ? number : number + " " + unit;
    }
}
