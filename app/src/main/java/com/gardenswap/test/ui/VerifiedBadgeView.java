package com.gardenswap.test.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;

/**
 * Reusable verification badge (AND-011): colored dot + label. Used on the
 * profile, the IDV screen, and later on sitter cards.
 *
 * <p>UID-022 restyles the badge against the design-system tokens: leaf dot
 * for verified states, orange for pending, muted for unverified. The
 * {@link BadgeState} mapping in {@link com.gardenswap.test.util.IdvStatusMapper}
 * is unchanged — FAILED still renders as the unverified badge, with the
 * failure called out by the surrounding screen copy.
 */
public class VerifiedBadgeView extends LinearLayout {

    private final TextView dot;
    private final TextView label;

    public VerifiedBadgeView(Context context) {
        this(context, null);
    }

    public VerifiedBadgeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        dot = new TextView(context);
        int size = Ui.dp(context, 10);
        LayoutParams dotParams = new LayoutParams(size, size);
        dotParams.setMarginEnd(Ui.dp(context, 8));
        dot.setLayoutParams(dotParams);

        label = new TextView(context);
        label.setTextSize(13);
        label.setTypeface(
                ResourcesCompat.getFont(context, R.font.dm_sans), Typeface.BOLD);
        label.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_ink, context.getTheme()));

        addView(dot);
        addView(label);
        setState(BadgeState.UNVERIFIED);
    }

    public void setState(BadgeState state) {
        int colorRes;
        String text;
        if (state == null) {
            state = BadgeState.UNVERIFIED;
        }
        switch (state) {
            case ID_VERIFIED:
                colorRes = R.color.garden_leaf;
                text = "ID verified";
                break;
            case PHONE_VERIFIED:
                colorRes = R.color.garden_leaf;
                text = "Phone verified";
                break;
            case PENDING:
                colorRes = R.color.garden_orange;
                text = "Verification pending";
                break;
            case UNVERIFIED:
            default:
                colorRes = R.color.garden_muted;
                text = "Not verified";
                break;
        }
        int color = ResourcesCompat.getColor(
                getResources(), colorRes, getContext().getTheme());
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        dot.setBackground(background);
        label.setText(text);
    }
}
