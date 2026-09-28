package com.gardenswap.app.chat;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.ChatMessage;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.CoordinateGuard;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.List;

/**
 * Message thread (AND-080).
 *
 * <p>Exchange-context header on top, system messages rendered distinctly,
 * photo share placeholder (Wave 3 mock). Before sending, text is screened
 * by {@link CoordinateGuard}: pasting GPS coordinates triggers a warning
 * dialog (SEC-010 — exact location stays private until the exchange is
 * confirmed). The server-side abuse filter (API-080) is the real
 * enforcement; this is the client-side nudge.
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_THREAD_ID = "thread_id";
    public static final String EXTRA_OTHER_NAME = "other_name";
    public static final String EXTRA_CONTEXT = "context";

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
        TextView title = Ui.label(this, otherName == null ? "Chat" : otherName);
        title.setTextSize(20);
        if (context != null) {
            root.addView(Ui.label(this, context));
            Ui.gap(root, this, 4);
        }
        statusText = Ui.status(this);

        ScrollView scroll = new ScrollView(this);
        messages = Ui.column(this, 8);
        scroll.addView(messages);

        input = Ui.input(this, "Message…", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Button send = Ui.button(this, "Send");
        send.setOnClickListener(v -> onSend());

        root.addView(title);
        Ui.gap(root, this, 4);
        root.addView(statusText);
        Ui.gap(root, this, 4);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.gap(root, this, 8);
        root.addView(input);
        Ui.gap(root, this, 4);
        root.addView(send);
        setContentView(root);

        load();
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
        for (ChatMessage message : result) {
            TextView view;
            if (message.getKind() == ChatMessage.Kind.SYSTEM) {
                view = Ui.label(this, "— " + message.getText() + " —");
            } else {
                String prefix = message.isMine() ? "You: " : message.getSenderName() + ": ";
                String body = message.getKind() == ChatMessage.Kind.PHOTO
                        ? "[photo] " + message.getText()
                        : message.getText();
                view = Ui.label(this, prefix + body);
            }
            messages.addView(view);
            Ui.gap(messages, this, 4);
        }
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
