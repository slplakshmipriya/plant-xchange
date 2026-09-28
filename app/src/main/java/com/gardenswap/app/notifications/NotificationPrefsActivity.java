package com.gardenswap.app.notifications;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.NotificationPrefs;
import com.gardenswap.app.ui.Nav;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.NavRouter;

import java.util.Locale;

/**
 * Notification preferences screen (PRD parity, r2).
 *
 * <p>Five category toggles plus quiet-hours start/end (24h "HH:mm",
 * picked with {@link TimePickerDialog}). Loads via
 * {@code GET /v1/users/me/notification-prefs}; saving goes to
 * {@code PUT /v1/users/me/notification-prefs}. The endpoints may 404
 * until the backend lands — load falls back to
 * {@link NotificationPrefs#defaultAllOn()} with a toast, and save
 * failures toast without crashing.
 *
 * <p>Category rows are grouped by the channels
 * {@link GardenSwapMessagingService} posts to:
 * <ul>
 *   <li>Listings: want-list matches</li>
 *   <li>Bookings: booking reminders</li>
 *   <li>Alerts: harvest alerts, expiry nudges, credit warnings</li>
 * </ul>
 */
public class NotificationPrefsActivity extends AppCompatActivity {

    private Switch harvestSwitch;
    private Switch wantMatchesSwitch;
    private Switch expiryNudgesSwitch;
    private Switch creditWarningsSwitch;
    private Switch bookingRemindersSwitch;
    private TextView quietStartView;
    private TextView quietEndView;
    private TextView statusView;
    private Button saveButton;

    private String quietStart;
    private String quietEnd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sticky brand bar, same as Profile; the screen title scrolls below.
        LinearLayout header = Ui.column(this, 24);
        header.addView(Ui.appTitleRow(this));
        int pad = Ui.dp(this, 24);
        header.setPadding(pad, pad, pad, 0);

        LinearLayout root = Ui.column(this, 24);
        root.setPadding(pad, 0, pad, pad);
        root.addView(Ui.headline(this, "Notification preferences"));
        Ui.gap(root, this, 8);
        root.addView(Ui.caption(this,
                "Choose which pushes you get. Changes take effect on save."));
        Ui.gap(root, this, 16);

        statusView = Ui.status(this);
        root.addView(statusView);
        Ui.gap(root, this, 8);

        root.addView(categoryCard());
        Ui.gap(root, this, 16);
        root.addView(quietHoursCard());
        Ui.gap(root, this, 16);

        saveButton = Ui.primaryButton(this, "Save preferences");
        saveButton.setOnClickListener(v -> save());
        root.addView(saveButton);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(Ui.stickyHeaderScreen(this, header, scroll));
        Nav.attach(this, NavRouter.Tab.PROFILE);

        load();
    }

    /** Five category toggle rows inside one card. */
    private LinearLayout categoryCard() {
        LinearLayout card = Ui.card(this);
        harvestSwitch = toggleRow(card, "Harvest alerts",
                "Ripe and harvest reminders · Alerts");
        wantMatchesSwitch = toggleRow(card, "Want-list matches",
                "New listings that match your want list · Listings");
        expiryNudgesSwitch = toggleRow(card, "Listing expiry nudges",
                "Reminders before your listings expire · Alerts");
        creditWarningsSwitch = toggleRow(card, "Credit expiry warnings",
                "Warnings before your credits expire · Alerts");
        bookingRemindersSwitch = toggleRow(card, "Booking reminders",
                "Sitting bookings and check-ins · Bookings");
        return card;
    }

    /**
     * Horizontal row: label + subtitle on the left, Switch on the right.
     * There is no toggle helper in {@link Ui}, so the rows build the
     * {@link Switch} directly here.
     */
    private Switch toggleRow(LinearLayout parent, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        text.addView(Ui.body(this, title));
        text.addView(Ui.caption(this, subtitle));
        row.addView(text);

        Switch toggle = new Switch(this);
        toggle.setEnabled(false);
        row.addView(toggle);

        parent.addView(row);
        return toggle;
    }

    /** Quiet-hours card: two tappable time fields plus a clear link. */
    private LinearLayout quietHoursCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.body(this, "Quiet hours"));
        card.addView(Ui.caption(this,
                "No pushes during this window. Tap a time to change it."));
        Ui.gap(card, this, 8);

        quietStartView = timeField(card, "Start");
        Ui.gap(card, this, 8);
        quietEndView = timeField(card, "End");

        TextView clear = Ui.caption(this, "Clear quiet hours");
        clear.setPadding(0, Ui.dp(this, 12), 0, 0);
        clear.setOnClickListener(v -> {
            quietStart = null;
            quietEnd = null;
            renderQuietHours();
        });
        card.addView(clear);
        return card;
    }

    /** Tappable field that opens a 24h TimePickerDialog and stores "HH:mm". */
    private TextView timeField(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = Ui.caption(this, label);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        TextView value = Ui.title(this, "Not set");
        value.setClickable(true);
        value.setFocusable(true);
        final boolean isStart = label.equals("Start");
        value.setOnClickListener(v -> pickTime(isStart));
        row.addView(value);

        parent.addView(row);
        return value;
    }

    private void pickTime(boolean isStart) {
        int defaultHour = isStart ? 22 : 8;
        int hour = defaultHour;
        int minute = 0;
        String current = isStart ? quietStart : quietEnd;
        if (current != null) {
            String[] parts = current.split(":");
            if (parts.length == 2) {
                try {
                    hour = Integer.parseInt(parts[0]);
                    minute = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    // Fall back to the default above.
                }
            }
        }
        new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
            String value = String.format(Locale.US, "%02d:%02d",
                    hourOfDay, minuteOfHour);
            if (isStart) {
                quietStart = value;
            } else {
                quietEnd = value;
            }
            renderQuietHours();
        }, hour, minute, true).show();
    }

    private void renderQuietHours() {
        quietStartView.setText(quietStart == null ? "Not set" : quietStart);
        quietEndView.setText(quietEnd == null ? "Not set" : quietEnd);
    }

    private void load() {
        statusView.setText("Loading preferences…");
        saveButton.setEnabled(false);
        ApiProvider.get().getNotificationPrefs(
                new GardenSwapApi.Callback<NotificationPrefs>() {
                    @Override
                    public void onSuccess(NotificationPrefs prefs) {
                        bind(prefs);
                    }

                    @Override
                    public void onError(ApiException e) {
                        Toast.makeText(NotificationPrefsActivity.this,
                                "Couldn't load preferences — showing defaults.",
                                Toast.LENGTH_LONG).show();
                        bind(NotificationPrefs.defaultAllOn());
                    }
                });
    }

    private void bind(NotificationPrefs prefs) {
        statusView.setText("");
        harvestSwitch.setChecked(prefs.isHarvestAlerts());
        wantMatchesSwitch.setChecked(prefs.isWantMatches());
        expiryNudgesSwitch.setChecked(prefs.isExpiryNudges());
        creditWarningsSwitch.setChecked(prefs.isCreditWarnings());
        bookingRemindersSwitch.setChecked(prefs.isBookingReminders());
        quietStart = prefs.getQuietHoursStart();
        quietEnd = prefs.getQuietHoursEnd();
        renderQuietHours();
        for (Switch toggle : new Switch[]{harvestSwitch, wantMatchesSwitch,
                expiryNudgesSwitch, creditWarningsSwitch,
                bookingRemindersSwitch}) {
            toggle.setEnabled(true);
        }
        saveButton.setEnabled(true);
    }

    private void save() {
        saveButton.setEnabled(false);
        NotificationPrefs prefs = NotificationPrefs.builder()
                .harvestAlerts(harvestSwitch.isChecked())
                .wantMatches(wantMatchesSwitch.isChecked())
                .expiryNudges(expiryNudgesSwitch.isChecked())
                .creditWarnings(creditWarningsSwitch.isChecked())
                .bookingReminders(bookingRemindersSwitch.isChecked())
                .quietHours(quietStart, quietEnd)
                .build();
        ApiProvider.get().updateNotificationPrefs(prefs,
                new GardenSwapApi.Callback<NotificationPrefs>() {
                    @Override
                    public void onSuccess(NotificationPrefs saved) {
                        saveButton.setEnabled(true);
                        Toast.makeText(NotificationPrefsActivity.this,
                                "Preferences saved.", Toast.LENGTH_SHORT)
                                .show();
                        bind(saved);
                    }

                    @Override
                    public void onError(ApiException e) {
                        saveButton.setEnabled(true);
                        Toast.makeText(NotificationPrefsActivity.this,
                                "Couldn't save preferences. Please try again.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
