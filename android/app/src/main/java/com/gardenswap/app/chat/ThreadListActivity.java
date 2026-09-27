package com.gardenswap.app.chat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.ChatThread;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.ui.Ui;

import java.util.List;

/**
 * Chat thread list (AND-080).
 *
 * <p>One thread per exchange/booking (API-080). Each row shows the
 * exchange-context header (listing summary + status), the other party,
 * and the last message. Tapping a row opens {@link ChatActivity}.
 */
public class ThreadListActivity extends AppCompatActivity {

    private TextView statusText;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Messages");
        title.setTextSize(20);
        statusText = Ui.status(this);
        list = Ui.column(this, 0);

        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(statusText);
        Ui.gap(root, this, 8);
        root.addView(list);
        setContentView(root);
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
        LinearLayout row = Ui.column(this, 12);

        // Exchange-context header (design board): what this chat is about.
        TextView context = Ui.label(this,
                thread.getListingSummary() + " · " + thread.getListingStatus());
        row.addView(context);

        String name = thread.getOtherPartyName();
        if (thread.getUnreadCount() > 0) {
            name += " (" + thread.getUnreadCount() + " new)";
        }
        TextView nameView = Ui.label(this, name);
        nameView.setTextSize(16);
        row.addView(nameView);
        row.addView(Ui.label(this, thread.getLastMessagePreview()));

        android.widget.Button open = Ui.button(this, "Open chat");
        open.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_THREAD_ID, thread.getThreadId());
            intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, thread.getOtherPartyName());
            intent.putExtra(ChatActivity.EXTRA_CONTEXT,
                    thread.getListingSummary() + " · " + thread.getListingStatus());
            startActivity(intent);
        });
        row.addView(open);
        return row;
    }
}
