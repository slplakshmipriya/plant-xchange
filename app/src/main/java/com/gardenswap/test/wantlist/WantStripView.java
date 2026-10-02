package com.gardenswap.test.wantlist;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gardenswap.test.R;
import com.gardenswap.test.api.WantItem;
import com.gardenswap.test.ui.Ui;

import java.util.List;

/**
 * Shared want-list strip (AND-030): a horizontal scrollable row of wanted-item
 * bubbles, each with a × remove action, plus a fixed "+ Add" button pinned at
 * the right end outside the scroll region. Used by the want-list screen and the
 * Explore tab.
 */
public class WantStripView extends LinearLayout {

    /** Called with the want id when the × on a bubble is tapped. */
    public interface OnRemoveListener {
        void onRemove(String wantId);
    }

    private final LinearLayout bubbles;
    private final Button addButton;
    private OnRemoveListener onRemoveListener;

    public WantStripView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        HorizontalScrollView bubbleScroll = new HorizontalScrollView(context);
        bubbleScroll.setHorizontalScrollBarEnabled(false);
        bubbleScroll.setLayoutParams(new LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bubbles = new LinearLayout(context);
        bubbles.setOrientation(HORIZONTAL);
        bubbles.setGravity(Gravity.CENTER_VERTICAL);
        bubbleScroll.addView(bubbles,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(bubbleScroll);

        addButton = Ui.button(context, "+ Add");
        LayoutParams addParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.leftMargin = Ui.dp(context, 8);
        addButton.setLayoutParams(addParams);
        addView(addButton);
    }

    public void setOnAddClickListener(OnClickListener listener) {
        addButton.setOnClickListener(listener);
    }

    public void setOnRemoveListener(OnRemoveListener listener) {
        onRemoveListener = listener;
    }

    /** Renders the bubbles; an empty list shows the empty-state prompt. */
    public void setWants(List<WantItem> wants) {
        bubbles.removeAllViews();
        if (wants == null || wants.isEmpty()) {
            bubbles.addView(Ui.caption(getContext(),
                    "Add to your wish list — see matches as they're posted."));
            return;
        }
        for (WantItem want : wants) {
            bubbles.addView(wantBubble(want));
        }
    }

    /** One wanted item as a bubble: the variety plus a × to remove it. */
    private LinearLayout wantBubble(final WantItem want) {
        Context context = getContext();
        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setBackgroundResource(R.drawable.chip_bg);
        int hPad = Ui.dp(context, 12);
        int vPad = Ui.dp(context, 8);
        bubble.setPadding(hPad, vPad, hPad, vPad);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = Ui.dp(context, 8);
        bubble.setLayoutParams(params);

        TextView name = new TextView(context);
        name.setText(want.getVariety());
        name.setTextSize(14);
        bubble.addView(name);

        TextView remove = new TextView(context);
        remove.setText("  ×");
        remove.setTextSize(16);
        remove.setClickable(true);
        remove.setFocusable(true);
        remove.setPadding(Ui.dp(context, 4), 0, 0, 0);
        remove.setOnClickListener(v -> {
            if (onRemoveListener != null) {
                onRemoveListener.onRemove(want.getId());
            }
        });
        bubble.addView(remove);
        return bubble;
    }
}
