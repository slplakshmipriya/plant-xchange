package com.gardenswap.test.chat;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.ChatThread;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.ui.Nav;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.ChatLogic;

import com.gardenswap.test.util.NavRouter;

import java.util.List;

/**
 * Chat thread list (AND-080), restyled to the prototype (UID-019).
 *
 * <p>One thread per exchange/booking (API-080). Each row is a card with the
 * exchange-context eyebrow, an avatar placeholder, the other party's name,
 * a last-message snippet, and a relative timestamp. Threads with unread
 * messages render the name bold with an acid dot. Tapping a row opens
 * {@link ChatActivity}.
 */
public class ThreadListActivity extends AppCompatActivity {

    private TextView statusText;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sticky brand bar, same as Explore; the screen title scrolls below.
        LinearLayout header = Ui.column(this, 24);
        header.addView(Ui.appTitleRow(this));
        int pad = Ui.dp(this, 24);
        header.setPadding(pad, pad, pad, 0);

        statusText = Ui.status(this);
        list = Ui.column(this, 0);
        LinearLayout root = Ui.column(this, 24);
        root.setPadding(pad, 0, pad, pad);
        root.addView(Ui.headline(this, "Messages"));
        Ui.gap(root, this, 8);
        root.addView(statusText);
        Ui.gap(root, this, 8);
        root.addView(list);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(Ui.stickyHeaderScreen(this, header, scroll));
        Nav.attach(this, NavRouter.Tab.MESSAGES);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        statusText.setText("Loading messages…");
        ApiProvider.get().getThreads(new GardenSwapApi.Callback<List<ChatThread>>() {
            @Override
            public void onSuccess(List<ChatThread> threads) {
                render(threads);
            }

            @Override
            public void onError(ApiException e) {
                statusText.setText("Couldn't load messages (" + e.getCode() + ").");
            }
        });
    }

    private void render(List<ChatThread> threads) {
        statusText.setText("");
        list.removeAllViews();
        if (threads.isEmpty()) {
            list.addView(Ui.label(this,
                    "No messages yet. Claim a listing to start chatting."));
            return;
        }
        for (ChatThread thread : threads) {
            list.addView(threadRow(thread));
            Ui.gap(list, this, 12);
        }
    }

    private LinearLayout threadRow(ChatThread thread) {
        boolean unread = thread.getUnreadCount() > 0;
        LinearLayout card = Ui.card(this);

        // Exchange-context header: what this chat is about.
        card.addView(Ui.eyebrow(this,
                thread.getListingSummary() + " · " + thread.getListingStatus()));
        Ui.gap(card, this, 8);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ChatViews.avatar(this, thread.getOtherPartyName()));
        row.addView(ChatViews.hGap(this, 12));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView nameView = Ui.body(this, thread.getOtherPartyName());
        nameView.setTextSize(16);
        if (unread) {
            nameView.setTypeface(null, Typeface.BOLD);
        }
        topRow.addView(nameView);
        if (unread) {
            topRow.addView(ChatViews.unreadDot(this));
        }
        TextView time = Ui.caption(this, ChatLogic.shortTime(
                System.currentTimeMillis(), thread.getLastMessageAtMs()));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        time.setLayoutParams(timeParams);
        time.setGravity(Gravity.END);
        topRow.addView(time);
        textCol.addView(topRow);

        TextView snippet = Ui.body(this, thread.getLastMessagePreview());
        snippet.setSingleLine(true);
        snippet.setEllipsize(TextUtils.TruncateAt.END);
        snippet.setTextColor(ResourcesCompat.getColor(getResources(),
                R.color.garden_muted, getTheme()));
        textCol.addView(snippet);
        row.addView(textCol);
        card.addView(row);

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_THREAD_ID, thread.getThreadId());
            intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, thread.getOtherPartyName());
            intent.putExtra(ChatActivity.EXTRA_PARTICIPANT_ID, thread.getParticipantUserId());
            intent.putExtra(ChatActivity.EXTRA_CONTEXT,
                    thread.getListingSummary() + " · " + thread.getListingStatus());
            startActivity(intent);
        });
        return card;
    }
}
