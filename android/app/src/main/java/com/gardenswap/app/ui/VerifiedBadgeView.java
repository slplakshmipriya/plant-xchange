package com.gardenswap.app.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Reusable verification badge (AND-011): colored dot + label. Used on the
 * profile, the IDV screen, and later on sitter cards.
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

        addView(dot);
        addView(label);
        setState(BadgeState.UNVERIFIED);
    }

    public void setState(BadgeState state) {
        int color;
        String text;
        if (state == null) {
            state = BadgeState.UNVERIFIED;
        }
        switch (state) {
            case ID_VERIFIED:
                color = 0xFF2E7D32;
                text = "ID verified";
                break;
            case PHONE_VERIFIED:
                color = 0xFF2E7D32;
                text = "Phone verified";
                break;
            case PENDING:
                color = 0xFFEF6C00;
                text = "Verification pending";
                break;
            case UNVERIFIED:
            default:
                color = 0xFF9E9E9E;
                text = "Not verified";
                break;
        }
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        dot.setBackground(background);
        label.setText(text);
    }
}
