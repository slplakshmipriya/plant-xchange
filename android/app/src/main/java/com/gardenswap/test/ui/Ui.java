package com.gardenswap.test.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tiny programmatic-UI helpers (AND-010). Wave 1 uses no XML layouts; these
 * keep the onboarding/IDV screens consistent without duplication.
 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** Vertical LinearLayout with uniform padding. */
    public static LinearLayout column(Context context, int paddingDp) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, paddingDp);
        layout.setPadding(padding, padding, padding, padding);
        return layout;
    }

    public static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        return view;
    }

    public static TextView status(Context context) {
        return label(context, "");
    }

    public static EditText input(Context context, String hint, int inputType) {
        EditText view = new EditText(context);
        view.setHint(hint);
        view.setInputType(inputType);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    public static Button button(Context context, String text) {
        Button view = new Button(context);
        view.setText(text);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /** Fixed-height vertical spacer. */
    public static void gap(LinearLayout parent, Context context, int dp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, dp)));
        parent.addView(spacer);
    }
}
