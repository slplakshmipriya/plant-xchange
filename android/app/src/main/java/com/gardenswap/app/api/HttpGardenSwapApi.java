package com.gardenswap.app.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Real HTTP implementation of {@link GardenSwapApi} (AND-090, Wave 1
 * integration checkpoint).
 *
 * <p>Uses {@link HttpURLConnection} — no new dependencies. Every call:
 * <ol>
 *   <li>fetches a fresh Firebase ID token (async),</li>
 *   <li>runs the HTTP exchange on a background thread,</li>
 *   <li>delivers the callback on the main thread.</li>
 * </ol>
 *
 * <p>Endpoint map (real backend, branch {@code develop}):
 * <ul>
 *   <li>{@code getMe} → {@code GET /v1/users/me}</li>
 *   <li>{@code upsertProfile} → {@code POST /v1/users} (ProfileIn body)</li>
 *   <li>{@code createIdvSession} → {@code POST /v1/idv/session}</li>
 *   <li>{@code getIdvStatus} → {@code GET /v1/users/me} → {@code idv_status}
 *       (no dedicated endpoint; reconciled in docs/api-contract.md)</li>
 *   <li>{@code registerFcmToken} → {@code FirebaseMessaging.subscribeToTopic("user_" + uid)}
 *       — the backend fans out to the per-user topic; there is intentionally
 *       no {@code /v1/devices} endpoint (see notify.py contract).</li>
 * </ul>
 */
public class HttpGardenSwapApi implements GardenSwapApi {

    private static final String TAG = "HttpGardenSwapApi";

    private final String baseUrl;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Executor net = Executors.newSingleThreadExecutor();

    public HttpGardenSwapApi(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
    }

    // ------------------------------------------------------------ plumbing

    private interface JsonHandler {
        void onJson(int status, JSONObject body) throws Exception;
    }

    private void authed(String method, String path, JSONObject body, JsonHandler handler,
                        Callback<?> errorCallback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            fail(errorCallback, new ApiException("not_signed_in", "No Firebase user signed in."));
            return;
        }
        Task<GetTokenResult> tokenTask = user.getIdToken(true);
        tokenTask.addOnSuccessListener(result -> net.execute(() -> {
            try {
                String token = result.getToken();
                HttpURLConnection conn = (HttpURLConnection)
                        new URL(baseUrl + path).openConnection();
                try {
                    conn.setRequestMethod(method);
                    conn.setConnectTimeout(15_000);
                    conn.setReadTimeout(15_000);
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                    conn.setRequestProperty("Accept", "application/json");
                    if (body != null) {
                        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/json");
                        try (OutputStream out = conn.getOutputStream()) {
                            out.write(bytes);
                        }
                    }
                    int status = conn.getResponseCode();
                    String raw = readAll(status < 400
                            ? conn.getInputStream() : conn.getErrorStream());
                    if (status >= 200 && status < 300) {
                        JSONObject json = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
                        succeed(handler, errorCallback, json);
                    } else {
                        fail(errorCallback, JsonParsers.parseError(status, raw));
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.w(TAG, "request failed: " + method + " " + path, e);
                fail(errorCallback, new ApiException("network_error",
                        "Couldn't reach the server. Check your connection."));
            }
        }));
        tokenTask.addOnFailureListener(e ->
                fail(errorCallback, new ApiException("token_error",
                        "Couldn't refresh your sign-in. Try again.")));
    }

    private void succeed(JsonHandler handler, Callback<?> errorCallback, JSONObject body) {
        main.post(() -> {
            try {
                handler.onJson(200, body);
            } catch (Exception e) {
                Log.w(TAG, "response parse failed", e);
                errorCallback.onError(new ApiException("parse_error",
                        "Couldn't understand the server's response."));
            }
        });
    }

    private void fail(Callback<?> callback, ApiException error) {
        main.post(() -> callback.onError(error));
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        try (InputStream autoClose = in;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = autoClose.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private boolean phoneVerified() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // Reconciled drift: the backend exposes no phone_verified field
        // (SEC-010). Phone-auth sign-in implies a verified number client-side.
        return user != null && user.getPhoneNumber() != null;
    }

    // ------------------------------------------------------------ Wave 1

    @Override
    public void getMe(Callback<UserProfile> callback) {
        authed("GET", "/v1/users/me", null,
                (status, body) -> callback.onSuccess(
                        JsonParsers.parseUserProfile(body, phoneVerified())),
                callback);
    }

    @Override
    public void upsertProfile(ProfileUpdate update, Callback<UserProfile> callback) {
        try {
            JSONObject body = new JSONObject();
            if (update.getDisplayName() != null) {
                body.put("display_name", update.getDisplayName());
            }
            if (update.getAvatarUrl() != null) {
                body.put("avatar_url", update.getAvatarUrl());
            }
            if (update.getHomeZip() != null) {
                body.put("home_zip", update.getHomeZip());
            }
            authed("POST", "/v1/users", body,
                    (s, json) -> callback.onSuccess(
                            JsonParsers.parseUserProfile(json, phoneVerified())),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the profile."));
        }
    }

    @Override
    public void createIdvSession(Callback<IdvSession> callback) {
        authed("POST", "/v1/idv/session", new JSONObject(),
                (status, body) -> callback.onSuccess(JsonParsers.parseIdvSession(body)),
                callback);
    }

    @Override
    public void getIdvStatus(Callback<IdvStatus> callback) {
        authed("GET", "/v1/users/me", null,
                (status, body) -> callback.onSuccess(
                        IdvStatus.fromString(body.optString("idv_status", null))),
                callback);
    }

    @Override
    public void registerFcmToken(String userId, String fcmToken, Callback<Void> callback) {
        // Backend contract: pushes go to the per-user FCM topic
        // "user_{uid}" (see api/app/notify.py). Subscribing the device to
        // that topic IS the registration — no token upload endpoint exists.
        FirebaseMessaging.getInstance().subscribeToTopic("user_" + userId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        main.post(() -> callback.onSuccess(null));
                    } else {
                        fail(callback, new ApiException("fcm_subscribe_failed",
                                "Couldn't subscribe to push notifications."));
                    }
                });
    }
}
