package com.gardenswap.test.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.api.SitterProfile;
import com.gardenswap.test.util.SitterLogic;

/**
 * Reusable sitter card (UID-017): circular avatar placeholder, name in
 * Libre Franklin, verification badge, rate, rating line, skill chips.
 * Matches the garden-swap-app-ui-design prototype: 18dp card, surface
 * background, ink text.
 */
public class SitterCardView extends LinearLayout {

    private final TextView avatarView;
    private final TextView nameView;
    private final VerifiedBadgeView badgeView;
    private final TextView rateView;
    private final TextView ratingView;
    private final LinearLayout chipsRow;

    public SitterCardView(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundResource(R.drawable.card_bg);
        int padding = Ui.dp(context, 16);
        setPadding(padding, padding, padding, padding);
        setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Top row: avatar + name block.
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        avatarView = avatar(context, "", 56);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 56), Ui.dp(context, 56));
        avatarParams.setMarginEnd(Ui.dp(context, 12));
        avatarView.setLayoutParams(avatarParams);
        top.addView(avatarView);

        LinearLayout nameBlock = new LinearLayout(context);
        nameBlock.setOrientation(LinearLayout.VERTICAL);
        nameView = Ui.title(context, "");
        badgeView = new VerifiedBadgeView(context);
        rateView = Ui.body(context, "");
        nameBlock.addView(nameView);
        nameBlock.addView(badgeView);
        nameBlock.addView(rateView);
        top.addView(nameBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addView(top);

        ratingView = Ui.caption(context, "");
        LinearLayout.LayoutParams ratingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ratingParams.topMargin = Ui.dp(context, 8);
        ratingView.setLayoutParams(ratingParams);
        addView(ratingView);

        chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.topMargin = Ui.dp(context, 8);
        chipsRow.setLayoutParams(chipsParams);
        addView(chipsRow);
    }

    /**
     * Circular avatar placeholder: leaf-green circle with the sitter's
     * initial. Colors resolve from the day/night tokens.
     */
    public static TextView avatar(Context context, String displayName, int sizeDp) {
        TextView view = new TextView(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_leaf, context.getTheme()));
        view.setBackground(circle);
        view.setText(SitterLogic.initial(displayName));
        view.setGravity(Gravity.CENTER);
        view.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_nav_ink, context.getTheme()));
        view.setTypeface(ResourcesCompat.getFont(context, R.font.libre_franklin), Typeface.BOLD);
        view.setTextSize(sizeDp * 0.4f);
        return view;
    }

    /** Binds the sitter; null-safe no-op. Shows up to 3 skill chips + "+N". */
    public void bind(SitterProfile sitter) {
        if (sitter == null) {
            return;
        }
        avatarView.setText(SitterLogic.initial(sitter.getDisplayName()));
        nameView.setText(sitter.getDisplayName() != null ? sitter.getDisplayName() : "");
        badgeView.setState(sitter.isIdVerified() ? BadgeState.ID_VERIFIED : BadgeState.UNVERIFIED);
        rateView.setText(SitterLogic.rateText(creditsPerDay(sitter)));
        ratingView.setText(SitterLogic.starsText(sitter.getRating(), sitter.getReviewCount()));

        chipsRow.removeAllViews();
        String[] services = sitter.getServices();
        int shown = 0;
        int total = 0;
        if (services != null) {
            for (String service : services) {
                if (service == null || service.trim().isEmpty()) {
                    continue;
                }
                total++;
                if (shown >= 3) {
                    continue;
                }
                TextView chip = Ui.chip(getContext(), service.trim());
                chip.setClickable(false);
                chip.setFocusable(false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMarginEnd(Ui.dp(getContext(), 8));
                chip.setLayoutParams(params);
                chipsRow.addView(chip);
                shown++;
            }
            if (total > shown) {
                TextView more = Ui.caption(getContext(), "+" + (total - shown) + " more");
                chipsRow.addView(more);
            }
        }
    }

    /**
     * Mock-phase conversion: the model prices per-visit in cents; the
     * design shows credits/day (pricing is credits-not-cents per the
     * contract decision). 100 cents = 1 credit, minimum 1.
     */
    private static int creditsPerDay(SitterProfile sitter) {
        return Math.max(1, sitter.getRatePerVisitCents() / 100);
    }
}
