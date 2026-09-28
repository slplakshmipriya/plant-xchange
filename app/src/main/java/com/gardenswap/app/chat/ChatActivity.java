package com.gardenswap.app.chat;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.app.R;
import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.ChatMessage;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.ui.ReportDialog;
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
    /**
     * Optional: the other participant's user id. When present, reports target
     * this id directly ({@code ChatThread.getParticipantUserId()}); when
     * absent, the report falls back to the display name and the backend
     * resolves the participant from the thread. ThreadListActivity owns the
     * thread object — it should pass this extra at launch time.
     */
    public static final String EXTRA_PARTICIPANT_ID = "participant_user_id";

    private ScrollView scroll;
    private TextView statusText;
    private LinearLayout messages;
    private EditText input;
    private String threadId;
    private String otherName;
    private String participantUserId;

    /** Gallery picker launcher (r2 chat): mirrors ProfileFormActivity's idiom. */
    private ActivityResultLauncher<String> photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        threadId = getIntent().getStringExtra(EXTRA_THREAD_ID);
        otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);
        String context = getIntent().getStringExtra(EXTRA_CONTEXT);
        if (threadId == null) {
            threadId = "t1";
        }
        participantUserId = getIntent().getStringExtra(EXTRA_PARTICIPANT_ID);

        // Gallery picker (ACTION_GET_CONTENT via GetContent contract, same as
        // onboarding's ProfileFormActivity): no storage permission needed.
        photoPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        sendPhoto(uri.toString());
                    }
                });

        LinearLayout root = Ui.column(this, 24);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.headline(this, otherName == null ? "Chat" : otherName);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button report = Ui.rowButton(this, "⚠ Report user", false);
        report.setTextSize(12);
        report.setMinHeight(Ui.dp(this, 36));
        report.setTextColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_sheet_red, getTheme()));
        report.setOnClickListener(v -> openReport());
        header.addView(title);
        header.addView(report);
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
        Button attach = Ui.rowButton(this, "Photo", false);
        attach.setOnClickListener(v -> photoPicker.launch("image/*"));
        Button send = Ui.rowButton(this, "Send", true);
        send.setOnClickListener(v -> onSend());

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.addView(attach);
        inputRow.addView(ChatViews.hGap(this, 8));
        input.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(input);
        inputRow.addView(ChatViews.hGap(this, 8));
        inputRow.addView(send);

        root.addView(header);
        Ui.gap(root, this, 4);
        root.addView(statusText);
        Ui.gap(root, this, 4);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.gap(root, this, 8);
        root.addView(locationBanner());
        Ui.gap(root, this, 8);
        root.addView(maskingNotice());
        Ui.gap(root, this, 8);
        root.addView(inputRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        load();
    }

    /**
     * Static phone-masking notice above the input (r2 chat). Informational
     * only — there is no agreement-to-share flow yet.
     */
    private TextView maskingNotice() {
        return Ui.caption(this,
                "Phone numbers stay masked until both of you agree to share.");
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

    /**
     * Report entry point (AND-158). The report target is the other
     * participant's user id when the thread carries one
     * ({@link com.gardenswap.app.api.ChatThread#getParticipantUserId()},
     * passed in as {@link #EXTRA_PARTICIPANT_ID}); otherwise it falls back to
     * the display name and the backend resolves the participant from the
     * thread.
     */
    private void openReport() {
        String target = participantUserId != null ? participantUserId : otherName;
        new ReportDialog(this, "USER", target).show();
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

    /**
     * Sends a picked photo as a chat attachment (r2 chat). The app has no
     * upload helper yet, so the picked content URI's string form is passed as
     * {@code photoUrl}; the mock echoes it back and
     * {@link ChatViews#bubbleRow} renders {@link ChatMessage.Kind#PHOTO}
     * messages as "[photo] &lt;url&gt;". On error a toast is shown and nothing
     * is appended; nothing here throws.
     */
    private void sendPhoto(String photoUrl) {
        ApiProvider.get().sendAttachment(threadId, photoUrl,
                new GardenSwapApi.Callback<ChatMessage>() {
                    @Override
                    public void onSuccess(ChatMessage message) {
                        messages.addView(ChatViews.bubbleRow(ChatActivity.this, message));
                        Ui.gap(messages, ChatActivity.this, 4);
                        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                        FirebaseAnalytics.getInstance(ChatActivity.this)
                                .logEvent("chat_photo_sent", null);
                    }

                    @Override
                    public void onError(ApiException e) {
                        Toast.makeText(ChatActivity.this,
                                "Couldn't send photo (" + e.getCode() + ").",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
