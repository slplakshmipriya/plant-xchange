package com.gardenswap.app;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Application entry point (AND-002). Initializes Firebase (Analytics,
 * Crashlytics, Messaging) on launch and kicks off FCM token registration.
 *
 * <p>Requires a git-ignored {@code google-services.json} — see
 * {@code docs/firebase-setup.md}.
 */
public class GardenSwapApp extends Application {

    private static final String TAG = "GardenSwapApp";

    private BackendRegistrar backendRegistrar = new NoOpBackendRegistrar();

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        FirebaseAnalytics.getInstance(this); // starts session/event logging
        FirebaseCrashlytics.getInstance().setCustomKey("app_init", true);
        registerFcmToken();
    }

    /** Visible for tests / future DI. */
    public void setBackendRegistrar(BackendRegistrar registrar) {
        this.backendRegistrar = registrar;
    }

    private void registerFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "FCM token fetch failed", task.getException());
                        return;
                    }
                    // Real backend call lands with the API contract (API-004);
                    // for now this is routed to the no-op stub.
                    backendRegistrar.registerFcmToken(currentUserId(), task.getResult());
                });
    }

    private String currentUserId() {
        // Firebase Auth sign-in lands in EPIC-MVP-2 (AND-010); empty = anonymous.
        return "";
    }
}
