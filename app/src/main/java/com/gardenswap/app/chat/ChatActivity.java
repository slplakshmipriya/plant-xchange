package com.gardenswap.app.chat;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.app.R;
import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.ChatMessage;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.ChatLogic;
import com.gardenswap.app.util.CoordinateGuard;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.List;

/**
 * Message thread (AND-080), restyled to the prototype (UID-019).
 *
 * <p>Exchange-context header on top, chat bubbles below (sent = leaf /
 * right, received = surface / left, 16dp radius), consecutive messages from
 * the same sender clustered via {@link ChatLogic#groupConsecutive}. A
 * location-privacy banner stays visible above the input at all times; before
 * sending, text is screened by {@link CoordinateGuard} exactly as before:
 * pasting GPS coordinates triggers a warning dialog (SEC-010 — exact
 * location stays private until the exchange is confirmed). The server-side
 * abuse filter (API-080) is the real enforcement; this is the client-side
 * nudge.
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_THREAD_ID = "thread_id";
    public static final String EXTRA_OTHER_NAME = "other_name";
    public static final String EXTRA_CONTEXT = "context";

    private ScrollView scroll;
    private TextView statusText;
    private LinearLayout messages;
    private EditText input;
    private String threadId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        threadId = getIntent().getStringExtra(EXTRA_THREAD_ID);
        String otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);
        String context = getIntent().getStringExtra(EXTRA_CONTEXT);
        if (threadId == null) {
            threadId = "t1";
        }

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.headline(this, otherName == null ? "Chat" : otherName);
        if (context != null) {
            root.addView(Ui.eyebrow(this, context));
            Ui.gap(root, this, 4);
        }
        statusText = Ui.status(this);

        scroll = new ScrollView(this);
        messages = Ui.column(this, 8);
        scroll.addView(messages);

        input = Ui.input(this, "Message…", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Button send = Ui.primaryButton(this, "Send");
        send.setOnClickListener(v -> onSend());

        root.addView(title);
        Ui.gap(root, this, 4);
        root.addView(statusText);
        Ui.gap(root, this, 4);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.gap(root, this, 8);
        root.addView(locationBanner());
        Ui.gap(root, this, 8);
        root.addView(input);
        Ui.gap(root, this, 8);
        root.addView(send);
        setContentView(root);

        load();
    }

    /**
     * Persistent location-privacy banner above the input (UID-019 T3).
     * The send-time {@link CoordinateGuard} dialog behavior is unchanged.
     */
    private LinearLayout locationBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText("!");
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextSize(14);
        badge.setTextColor(ResourcesCompat.getColor(getResources(),
                R.color.garden_ink, getTheme()));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(ResourcesCompat.getColor(getResources(),
                R.color.garden_yellow, getTheme()));
        badge.setBackground(badgeBg);
        int badgeSize = Ui.dp(this, 24);
        badge.setLayoutParams(new LinearLayout.LayoutParams(badgeSize, badgeSize));
        banner.addView(badge);
        banner.addView(ChatViews.hGap(this, 8));

        TextView note = Ui.caption(this,
                "Keep your exact location private — it stays hidden until an exchange is confirmed.");
        banner.addView(note);
        return banner;
    }

    private void load() {
        statusText.setText("Loading…");
        ApiProvider.get().getMessages(threadId, new GardenSwapApi.Callback<List<ChatMessage>>() {
            @Override
            public void onSuccess(List<ChatMessage> result) {
                statusText.setText("");
                render(result);
            }

            @Override
            public void onError(ApiException e) {
                statusText.setText("Couldn't load messages (" + e.getCode() + ").");
            }
        });
    }

    private void render(List<ChatMessage> result) {
        messages.removeAllViews();
        boolean first = true;
        for (List<ChatMessage> group : ChatLogic.groupConsecutive(result)) {
            if (!first) {
                Ui.gap(messages, this, 12);
            }
            first = false;
            boolean system = group.get(0).getKind() == ChatMessage.Kind.SYSTEM;
            for (ChatMessage message : group) {
                messages.addView(system
                        ? ChatViews.systemMessage(this, message.getText())
                        : ChatViews.bubbleRow(this, message));
                Ui.gap(messages, this, 4);
            }
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void onSend() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        String matched = CoordinateGuard.findCoordinates(text);
        if (matched != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Share your location?")
                    .setMessage(CoordinateGuard.warningText(matched))
                    .setPositiveButton("Send anyway", (d, w) -> send(text))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            send(text);
        }
    }

    private void send(String text) {
        input.setEnabled(false);
        ApiProvider.get().sendMessage(threadId, text,
                new GardenSwapApi.Callback<ChatMessage>() {
                    @Override
                    public void onSuccess(ChatMessage message) {
                        input.setText("");
                        input.setEnabled(true);
                        load(); // re-render the thread with the new message
                        FirebaseAnalytics.getInstance(ChatActivity.this)
                                .logEvent("chat_message_sent", null);
                    }

                    @Override
                    public void onError(ApiException e) {
                        input.setEnabled(true);
                        statusText.setText("Couldn't send (" + e.getCode() + ").");
                    }
                });
    }
}
