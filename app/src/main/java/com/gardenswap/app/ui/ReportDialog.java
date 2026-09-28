package com.gardenswap.app.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gardenswap.app.R;
import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.ReportRequest;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Report bottom sheet (AND-158). Same always-light sheet style as
 * {@link ClaimBottomSheet}: colors pinned to the night-independent
 * {@code garden_sheet_*} tokens and sheet-local chip selection.
 *
 * <p>Collects a single-select report category plus optional free-text
 * details, then submits via
 * {@link GardenSwapApi#reportContent(ReportRequest, GardenSwapApi.Callback)}.
 * The backend triages; the client shows a confirmation only.
 *
 * <p>{@code targetType} is one of {@code "LISTING"}, {@code "USER"},
 * {@code "BOOKING"} — kept flexible so listing/booking report entry points
 * can reuse this dialog later.
 */
public final class ReportDialog {

    private static final String[] CATEGORY_VALUES =
            {"spam", "safety", "fraud", "inappropriate", "other"};
    private static final String[] CATEGORY_LABELS =
            {"Spam", "Safety", "Fraud", "Inappropriate", "Other"};

    private final Context context;
    private final String targetType;
    private final String targetId;

    /**
     * @param context    an Activity context (used for the sheet and toasts)
     * @param targetType one of {@code "LISTING"}, {@code "USER"}, {@code "BOOKING"}
     * @param targetId   id of the reported listing/user/booking
     */
    public ReportDialog(Context context, String targetType, String targetId) {
        this.context = context;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    /** Builds and shows the sheet. */
    public void show() {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = Ui.column(context, 24);

        TextView title = Ui.headline(context, "Report");
        Ui.textColor(context, title, R.color.garden_sheet_ink);
        root.addView(title);
        Ui.gap(root, context, 4);
        TextView subtitle = Ui.caption(context,
                "Tell us what's wrong. Our team reviews every report.");
        Ui.textColor(context, subtitle, R.color.garden_sheet_muted);
        root.addView(subtitle);
        Ui.gap(root, context, 16);

        // Category chips (single-select, defaults to "spam").
        TextView categoryEyebrow = Ui.eyebrow(context, "What's the issue?");
        Ui.textColor(context, categoryEyebrow, R.color.garden_sheet_ink);
        root.addView(categoryEyebrow);
        Ui.gap(root, context, 8);
        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] chipViews = new TextView[CATEGORY_LABELS.length];
        final int[] selectedIndex = {0};
        for (int i = 0; i < chipViews.length; i++) {
            final int index = i;
            TextView chip = Ui.chip(context, CATEGORY_LABELS[i]);
            chip.setClickable(true);
            chip.setFocusable(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(Ui.dp(context, 8));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                selectedIndex[0] = index;
                for (int j = 0; j < chipViews.length; j++) {
                    setSheetChipSelected(chipViews[j], j == index);
                }
            });
            chipViews[i] = chip;
            chips.addView(chip);
        }
        setSheetChipSelected(chipViews[0], true);
        root.addView(chips);
        Ui.gap(root, context, 16);

        // Optional details.
        TextView detailsEyebrow = Ui.eyebrow(context, "Details \u00b7 optional");
        Ui.textColor(context, detailsEyebrow, R.color.garden_sheet_ink);
        root.addView(detailsEyebrow);
        Ui.gap(root, context, 8);
        EditText details = Ui.input(context, "What happened?",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Ui.textColor(context, details, R.color.garden_sheet_ink);
        details.setHintTextColor(context.getResources().getColor(
                R.color.garden_sheet_muted, context.getTheme()));
        root.addView(details);
        Ui.gap(root, context, 8);

        TextView error = Ui.status(context);
        Ui.textColor(context, error, R.color.garden_sheet_red);
        root.addView(error);
        Ui.gap(root, context, 8);

        Button submit = Ui.primaryButton(context, "Submit report");
        submit.setBackgroundResource(R.drawable.btn_sheet_primary);
        submit.setTextColor(context.getResources().getColor(
                android.R.color.white, context.getTheme()));
        submit.setOnClickListener(v -> {
            String detailText = details.getText() == null ? ""
                    : details.getText().toString().trim();
            error.setText("");
            submit.setEnabled(false); // no double-tap.
            ReportRequest request = new ReportRequest(
                    targetType, targetId, CATEGORY_VALUES[selectedIndex[0]],
                    detailText.isEmpty() ? null : detailText);
            ApiProvider.get().reportContent(request,
                    new GardenSwapApi.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            dialog.dismiss();
                            Toast.makeText(context,
                                    "Report submitted. Thanks for keeping Garden Swap safe.",
                                    Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(ApiException e) {
                            submit.setEnabled(true);
                            error.setText(e.getMessage());
                        }
                    });
        });
        root.addView(submit);

        dialog.setContentView(root);
        dialog.show();
    }

    /**
     * Sheet-local chip selection with night-independent colors. Mirrors
     * {@code ClaimBottomSheet}: the shared {@link Ui#setChipSelected} resolves
     * theme colors that go light in dark mode, which is unreadable on this
     * always-light sheet.
     */
    private void setSheetChipSelected(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.chip_sheet_selected : R.drawable.chip_sheet);
        Ui.textColor(context, chip, selected
                ? android.R.color.white : R.color.garden_sheet_ink);
    }
}
