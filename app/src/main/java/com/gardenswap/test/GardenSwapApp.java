package com.gardenswap.test;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.messaging.FirebaseMessaging;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.notifications.GardenSwapMessagingService;

/**
 * Application entry point (AND-002). Initializes Firebase (Analytics,
 * Crashlytics, Messaging) on launch and kicks off FCM token registration.
 *
 * <p>Requires a git-ignored {@code google-services.json} — see
 * {@code docs/firebase-setup.md}.
 */
public class GardenSwapApp extends Application {

    private static final String TAG = "GardenSwapApp";

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        FirebaseAnalytics.getInstance(this); // starts session/event logging
        FirebaseCrashlytics.getInstance().setCustomKey("app_init", true);
        registerFcmToken();
        createNotificationChannels();
        padScreensBelowStatusBar();
    }

    /**
     * Shifts every screen's content below the status bar and above the
     * system navigation bar. The app draws programmatic layouts under a
     * NoActionBar theme, so without this the first rows sit under the status
     * icons and bottom-anchored controls (e.g. the chat send button) sink
     * under the system nav. Applied once per activity via the content frame,
     * so all current and future screens are covered.
     */
    private void padScreensBelowStatusBar() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                View content = activity.findViewById(android.R.id.content);
                if (content == null) {
                    return;
                }
                final int baseTop = content.getPaddingTop();
                final int baseBottom = content.getPaddingBottom();
                ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                    int statusTop = insets.getInsets(
                            WindowInsetsCompat.Type.statusBars()).top;
                    int navBottom = insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars()).bottom;
                    v.setPadding(v.getPaddingLeft(), baseTop + statusTop,
                            v.getPaddingRight(), baseBottom + navBottom);
                    return insets;
                });
            }

            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    /**
     * Creates the push channels the messaging service posts to (AND-152).
     * Guarded for API 26+; channels are a no-op on older platforms.
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(
                GardenSwapMessagingService.CHANNEL_LISTINGS,
                "Listings", NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                GardenSwapMessagingService.CHANNEL_BOOKINGS,
                "Bookings", NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(
                GardenSwapMessagingService.CHANNEL_ALERTS,
                "Alerts", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private void registerFcmToken() {
        // Register this device's FCM token with the backend whenever a
        // user is signed in (rotation is handled by
        // GardenSwapMessagingService.onNewToken). Signed-out devices have
        // no account to target, so there is nothing to register.
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                return;
            }
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "FCM token fetch failed", task.getException());
                            return;
                        }
                        ApiProvider.get().registerFcmToken(user.getUid(), task.getResult(),
                                new GardenSwapApi.Callback<Void>() {
                                    @Override
                                    public void onSuccess(Void ignored) {
                                    }

                                    @Override
                                    public void onError(ApiException e) {
                                        Log.w(TAG, "FCM register failed: " + e.getCode());
                                    }
                                });
                    });
        });
    }
}
