package com.gardenswap.test.chat;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.api.ChatMessage;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.ChatLogic;
import com.gardenswap.test.util.ImageLoader;

/**
 * Shared view builders for the messaging screens (UID-019).
 *
 * <p>All colors come from the UID-001 design tokens, so both themes work:
 * drawables reference theme-aware colors, and the sent-bubble text color is
 * picked per night mode (garden_leaf is light in the dark theme).
 */
final class ChatViews {

    private ChatViews() {
    }

    static boolean isNight(Context context) {
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Circular avatar placeholder with the other party's initials. */
    static TextView avatar(Context context, String name) {
        TextView view = new TextView(context);
        view.setText(initialsOf(name));
        view.setGravity(Gravity.CENTER);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextSize(16);
        view.setTextColor(ResourcesCompat.getColor(context.getResources(),
                R.color.garden_ink, context.getTheme()));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ResourcesCompat.getColor(context.getResources(),
                R.color.garden_line, context.getTheme()));
        view.setBackground(bg);
        int size = Ui.dp(context, 48);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
    }

    private static String initialsOf(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }

    /** Small acid dot marking threads with unread messages. */
    static View unreadDot(Context context) {
        View dot = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ResourcesCompat.getColor(context.getResources(),
                R.color.garden_acid, context.getTheme()));
        dot.setBackground(bg);
        int size = Ui.dp(context, 10);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        int margin = Ui.dp(context, 6);
        params.setMargins(margin, 0, margin, 0);
        dot.setLayoutParams(params);
        return dot;
    }

    /** Fixed-width horizontal spacer for hand-built rows. */
    static View hGap(Context context, int dp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(context, dp), ViewGroup.LayoutParams.MATCH_PARENT));
        return spacer;
    }

    /**
     * One chat bubble row: sent = leaf bubble / right-aligned,
     * received = surface bubble / left-aligned, 16dp radius, timestamp
     * caption inside the bubble.
     */
    static LinearLayout bubbleRow(Context context, ChatMessage message) {
        boolean mine = message.isMine();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(
                mine ? R.drawable.bubble_sent : R.drawable.bubble_received);
        int hPad = Ui.dp(context, 12);
        bubble.setPadding(hPad, Ui.dp(context, 8), hPad, Ui.dp(context, 8));

        int onBubble = ResourcesCompat.getColor(context.getResources(),
                mine ? (isNight(context) ? R.color.garden_nav : R.color.garden_nav_ink)
                        : R.color.garden_ink,
                context.getTheme());
        boolean isPhoto = message.getKind() == ChatMessage.Kind.PHOTO;
        if (message.isDeleted()) {
            // Deleted tombstone: no content, muted caption so the other side
            // sees the message was removed rather than it vanishing.
            TextView tombstone = Ui.caption(context,
                    message.isMine() ? "You deleted this message"
                            : "This message was deleted");
            tombstone.setTypeface(tombstone.getTypeface(), Typeface.ITALIC);
            tombstone.setTextColor(onBubble);
            tombstone.setAlpha(0.75f);
            bubble.addView(tombstone);
        } else if (isPhoto) {
            // Photo attachment: render the image; fall back to text on failure.
            ImageView photoView = new ImageView(context);
            int photoSize = Ui.dp(context, 200);
            photoView.setLayoutParams(new LinearLayout.LayoutParams(
                    photoSize, photoSize));
            photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photoView.setContentDescription("Photo attachment");
            bubble.addView(photoView);
            ImageLoader.loadInto(photoView, message.getText());
        } else {
            TextView body = Ui.body(context, message.getText());
            body.setTextColor(onBubble);
            body.setMaxWidth(Ui.dp(context, 280));
            bubble.addView(body);
        }
        TextView time = Ui.caption(context,
                ChatLogic.shortTime(System.currentTimeMillis(), message.getSentAtMs()));
        time.setTextColor(onBubble);
        time.setGravity(mine ? Gravity.END : Gravity.START);

        bubble.addView(time);
        bubble.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(bubble);
        return row;
    }

    /** Centered muted system message ("— Claim confirmed —"). */
    static TextView systemMessage(Context context, String text) {
        TextView view = Ui.caption(context, "— " + text + " —");
        view.setGravity(Gravity.CENTER);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }
}
