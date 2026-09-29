package com.gardenswap.test.notifications;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.gardenswap.test.BuildConfig;
import com.gardenswap.test.MainActivity;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * FCM receive path (AND-152). Shows system notifications for incoming data
 * messages on the matching channel; tapping a notification opens
 * {@link MainActivity} carrying the payload extras.
 *
 * <p>Channels are created by {@code GardenSwapApp} at startup; this service
 * only posts to them.
 */
public class GardenSwapMessagingService extends FirebaseMessagingService {

    private static final String TAG = "PushService";

    /** Claim / new-listing / match alerts. */
    public static final String CHANNEL_LISTINGS = "listings";
    /** Booking requests, check-ins, completions, reviews. */
    public static final String CHANNEL_BOOKINGS = "bookings";
    /** Ripe alerts, credit/expiry notices, quiet-hours-respecting pings. */
    public static final String CHANNEL_ALERTS = "alerts";

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();

        // Debug-only remote UAT trigger: a data message with type=uat_run
        // and journey=<id|all|photo-regression> launches the UAT console.
        // The harness lives in the debug source set; reflection keeps the
        // release build compiling without it.
        if ("uat_run".equals(data.get("type")) && BuildConfig.DEBUG) {
            launchUat(data.get("journey"));
            return;
        }

        // Prefer the data payload; fall back to the notification payload so
        // console-sent messages still render.
        String title = data.get("title");
        String body = data.get("body");
        RemoteMessage.Notification notification = message.getNotification();
        if (title == null && notification != null) {
            title = notification.getTitle();
        }
        if (body == null && notification != null) {
            body = notification.getBody();
        }
        if (title == null) {
            title = "Garden Swap";
        }
        if (body == null) {
            Log.d(TAG, "onMessageReceived: empty body, nothing to show");
            return;
        }

        String type = data.get("type");
        String channelId = channelForType(type);
        postNotification(channelId, title, body, data, type);
    }

    @Override
    public void onNewToken(String token) {
        // Same backend path as HomeZipActivity's re-register flow.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user == null ? "" : user.getUid();
        ApiProvider.get().registerFcmToken(uid, token,
                new GardenSwapApi.Callback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        Log.d(TAG, "onNewToken: re-registered");
                    }

                    @Override
                    public void onError(ApiException e) {
                        Log.w(TAG, "onNewToken: re-register failed: " + e.getCode());
                    }
                });
    }

    private static String channelForType(String type) {
        if ("claim".equals(type) || "listing".equals(type) || "match".equals(type)) {
            return CHANNEL_LISTINGS;
        }
        if ("booking".equals(type) || "review".equals(type)
                || "sitting".equals(type)) {
            return CHANNEL_BOOKINGS;
        }
        return CHANNEL_ALERTS;
    }

    /**
     * Debug-only: launches the UAT console via reflection so release builds
     * (which lack the debug source set) keep compiling.
     */
    private void launchUat(String journey) {
        try {
            Class<?> cls = Class.forName("com.gardenswap.test.uat.UatRunnerActivity");
            Intent intent = new Intent(this, cls);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (journey != null) {
                intent.putExtra("uat_journey", journey);
            }
            startActivity(intent);
            Log.i(TAG, "UAT console launched (journey=" + journey + ")");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "uat_run received but the UAT harness is not in this build", e);
        }
    }

    private void postNotification(String channelId, String title, String body,
            Map<String, String> data, String type) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            intent.putExtra("push_" + entry.getKey(), entry.getValue());
        }
        intent.putExtra("push_type", type);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int notificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }
}
