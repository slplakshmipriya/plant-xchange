package com.gardenswap.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.app.R;

/**
 * Tiny programmatic-UI helpers (AND-010). Wave 1 uses no XML layouts; these
 * keep the onboarding/IDV screens consistent without duplication.
 *
 * <p>UID-003 adds the design-system components: pill buttons, cards, chips,
 * and type-scale text views matching the garden-swap-app-ui-design
 * prototype.
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

    /** Display heading: Libre Franklin bold 28sp. */
    public static TextView display(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Display);
    }

    /** Section heading: Libre Franklin bold 22sp. */
    public static TextView headline(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Headline);
    }

    /** Card title: Libre Franklin bold 18sp. */
    public static TextView title(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Title);
    }

    /** Body copy: DM Sans 15sp. */
    public static TextView body(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Body);
    }

    /** Eyebrow label: DM Sans bold 13sp, letterspaced, muted. */
    public static TextView eyebrow(Context context, String text) {
        TextView view = styledText(context, text.toUpperCase(), R.style.TextAppearance_GardenSwap_Label);
        return view;
    }

    /** Small print: DM Sans 12sp, muted. */
    public static TextView caption(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Caption);
    }

    private static TextView styledText(Context context, String text, int textAppearance) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextAppearance(textAppearance);
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

    /** Primary CTA: leaf-green pill, nav-ink text, DM Sans medium. */
    public static Button primaryButton(Context context, String text) {
        Button view = new Button(context);
        view.setText(text);
        view.setBackgroundResource(R.drawable.btn_primary);
        view.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_nav_ink, context.getTheme()));
        view.setTypeface(ResourcesCompat.getFont(context, R.font.dm_sans), Typeface.BOLD);
        view.setAllCaps(false);
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 52));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /** Secondary action: leaf outline pill. */
    public static Button secondaryButton(Context context, String text) {
        Button view = primaryButton(context, text);
        view.setBackgroundResource(R.drawable.btn_secondary);
        view.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_leaf, context.getTheme()));
        return view;
    }

    /** Card container: surface fill, 18dp radius, 16dp inner padding. */
    public static LinearLayout card(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundResource(R.drawable.card_bg);
        int padding = dp(context, 16);
        layout.setPadding(padding, padding, padding, padding);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    /**
     * Filter chip. Call {@link #setChipSelected} to toggle the selected
     * (leaf fill) state.
     */
    public static TextView chip(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setBackgroundResource(R.drawable.chip_bg);
        view.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_ink, context.getTheme()));
        view.setTypeface(ResourcesCompat.getFont(context, R.font.dm_sans), Typeface.BOLD);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        int hPad = dp(context, 4);
        view.setPadding(view.getPaddingLeft(), hPad, view.getPaddingRight(), hPad);
        return view;
    }

    public static void setChipSelected(Context context, TextView chip, boolean selected) {
        chip.setBackgroundResource(
                selected ? R.drawable.chip_bg_selected : R.drawable.chip_bg);
        chip.setTextColor(ResourcesCompat.getColor(context.getResources(),
                selected ? R.color.garden_nav_ink : R.color.garden_ink,
                context.getTheme()));
    }

    /** Fixed-height vertical spacer. */
    public static void gap(LinearLayout parent, Context context, int dp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, dp)));
        parent.addView(spacer);
    }

    /** Sets a TextView's color from a color resource (theme-aware). */
    public static void textColor(Context context, TextView view, int colorRes) {
        view.setTextColor(ResourcesCompat.getColor(
                context.getResources(), colorRes, context.getTheme()));
    }
}
