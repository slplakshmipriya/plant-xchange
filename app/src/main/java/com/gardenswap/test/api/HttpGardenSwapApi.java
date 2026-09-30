package com.gardenswap.test.api;

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
import java.util.List;
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
            if (update.getAgeAttestation() != null) {
                body.put("age_attestation", update.getAgeAttestation());
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
    public void uploadAvatar(byte[] imageBytes, String contentType, Callback<String> callback) {
        try {
            JSONObject signBody = new JSONObject();
            signBody.put("content_type", contentType);
            signBody.put("size_bytes", imageBytes.length);
            authed("POST", "/v1/uploads/sign", signBody, (s, signJson) -> {
                String key = signJson.optString("key", null);
                String uploadUrl = signJson.optString("upload_url", null);
                if (key == null || uploadUrl == null) {
                    callback.onError(new ApiException("upload_error",
                            "Couldn't start the photo upload."));
                    return;
                }
                // Step 2: PUT raw bytes to the signed upload URL.
                putRawBytes(uploadUrl, imageBytes, contentType, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void v) {
                        // Step 3: Finalize (strips EXIF GPS, makes it servable).
                        try {
                            JSONObject finBody = new JSONObject();
                            finBody.put("key", key);
                            authed("POST", "/v1/uploads/finalize", finBody,
                                    (s2, finJson) -> {
                                        String publicUrl = finJson.optString("public_url", null);
                                        if (publicUrl == null) {
                                            callback.onError(new ApiException("upload_error",
                                                    "Couldn't finish the photo upload."));
                                            return;
                                        }
                                        // Backend returns a relative URL; make it absolute.
                                        String absolute = publicUrl.startsWith("http")
                                                ? publicUrl : baseUrl + publicUrl;
                                        callback.onSuccess(absolute);
                                    },
                                    callback);
                        } catch (Exception e) {
                            fail(callback, new ApiException("encode_error",
                                    "Couldn't encode the upload request."));
                        }
                    }

                    @Override
                    public void onError(ApiException error) {
                        callback.onError(error);
                    }
                });
            }, callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the upload request."));
        }
    }

    @Override
    public void uploadFileKey(byte[] bytes, String contentType, Callback<String> callback) {
        try {
            JSONObject signBody = new JSONObject();
            signBody.put("content_type", contentType);
            signBody.put("size_bytes", bytes.length);
            authed("POST", "/v1/uploads/sign", signBody, (s, signJson) -> {
                final String key = signJson.optString("key", null);
                String uploadUrl = signJson.optString("upload_url", null);
                if (key == null || uploadUrl == null) {
                    callback.onError(new ApiException("upload_error",
                            "Couldn't start the photo upload."));
                    return;
                }
                putRawBytes(uploadUrl, bytes, contentType, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void v) {
                        try {
                            JSONObject finBody = new JSONObject();
                            finBody.put("key", key);
                            authed("POST", "/v1/uploads/finalize", finBody,
                                    (s2, finJson) -> callback.onSuccess(key),
                                    callback);
                        } catch (Exception e) {
                            fail(callback, new ApiException("encode_error",
                                    "Couldn't encode the upload request."));
                        }
                    }

                    @Override
                    public void onError(ApiException error) {
                        callback.onError(error);
                    }
                });
            }, callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the upload request."));
        }
    }

    @Override
    public void getSwaps(Callback<List<Swap>> callback) {
        authed("GET", "/v1/me/swaps", null,
                (s, json) -> callback.onSuccess(JsonParsers.parseSwaps(json)),
                callback);
    }

    /** PUT raw bytes to an upload URL with Firebase auth. */
    private void putRawBytes(String path, byte[] bytes, String contentType,
                             Callback<Void> callback) {        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            fail(callback, new ApiException("not_signed_in", "No Firebase user signed in."));
            return;
        }
        user.getIdToken(true).addOnSuccessListener(result -> net.execute(() -> {
            try {
                String url = path.startsWith("http") ? path : baseUrl + path;
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                try {
                    conn.setRequestMethod("PUT");
                    conn.setConnectTimeout(30_000);
                    conn.setReadTimeout(30_000);
                    conn.setRequestProperty("Authorization", "Bearer " + result.getToken());
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", contentType);
                    conn.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream out = conn.getOutputStream()) {
                        out.write(bytes);
                    }
                    int status = conn.getResponseCode();
                    if (status >= 200 && status < 300) {
                        main.post(() -> callback.onSuccess(null));
                    } else {
                        String raw = readAll(conn.getErrorStream());
                        main.post(() -> callback.onError(JsonParsers.parseError(status, raw)));
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.w(TAG, "raw PUT failed", e);
                fail(callback, new ApiException("network_error",
                        "Couldn't upload the photo. Check your connection."));
            }
        })).addOnFailureListener(e -> fail(callback,
                new ApiException("auth_error", "Couldn't get an auth token.")));
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
        // The fcmToken parameter is intentionally unused (kept for signature
        // stability); delivery relies on backend topic fan-out.
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

    // ------------------------------------------------------------ helpers

    private String myUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private static String enc(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    // ------------------------------------------------------------ Wave 2: listings

    @Override
    public void createListing(ListingInput input, Callback<Listing> callback) {
        // Client-side pre-validation mirrors the backend's hard requirements
        // (photos min 1, spray_disclosure min 1): fail fast with a clear code
        // instead of a bare 422.
        if (input.getType() == null) {
            fail(callback, new ApiException("invalid_type", "Pick a listing type."));
            return;
        }
        if (input.getPhotos() == null || input.getPhotos().isEmpty()) {
            fail(callback, new ApiException("photo_required",
                    "Add at least one photo."));
            return;
        }
        if (input.getSprayDisclosure() == null
                || input.getSprayDisclosure().trim().isEmpty()) {
            fail(callback, new ApiException("spray_disclosure_required",
                    "Tell swappers about spray history."));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("type", input.getType().getWireValue());
            org.json.JSONArray photos = new org.json.JSONArray();
            for (String photo : input.getPhotos()) {
                photos.put(photo);
            }
            body.put("photos", photos);
            if (input.getVariety() != null) {
                body.put("variety", input.getVariety());
            }
            if (input.getQuantity() != null) {
                body.put("quantity", input.getQuantity());
            }
            if (input.getUnit() != null) {
                body.put("unit", input.getUnit());
            }
            // Reconciled: the backend prices listings at 1-3 credits (DB CHECK).
            // "Free" maps to the 1-credit floor — never silently dropped, never
            // sent as 0 (the backend would 422). See docs/api-contract.md.
            body.put("credit_cost", input.isFree() ? 1 : input.getCreditCost());
            if (input.getPickupStartMs() != null && input.getPickupEndMs() != null) {
                JSONObject window = new JSONObject();
                window.put("start", JsonParsers.formatIsoUtc(input.getPickupStartMs()));
                window.put("end", JsonParsers.formatIsoUtc(input.getPickupEndMs()));
                body.put("pickup_window", window);
            }
            if (input.getExpiresAtMs() != null) {
                body.put("expires_at", JsonParsers.formatIsoUtc(input.getExpiresAtMs()));
            }
            if (input.getGeoLat() != null && input.getGeoLon() != null) {
                body.put("geo_lat", input.getGeoLat());
                body.put("geo_lon", input.getGeoLon());
            }
            body.put("spray_disclosure", input.getSprayDisclosure());
            if (input.getVisitRules() != null) {
                body.put("visit_rules", input.getVisitRules());
            }
            // PRD parity (r2): seedling extras; PRD field names mirror the
            // ListingInput model. pickupWindowDays is a required int (PRD §4
            // default 4), always sent; the nullables are omitted when unset.
            if (input.getPotSize() != null) {
                body.put("potSize", input.getPotSize());
            }
            if (input.getPlantAge() != null) {
                body.put("plant_age_years", input.getPlantAge());
            }
            body.put("pickupWindowDays", input.getPickupWindowDays());
            // The create flow publishes immediately (mock-era semantics).
            body.put("status", "live");
            authed("POST", "/v1/listings", body,
                    (status, json) -> callback.onSuccess(JsonParsers.parseListing(json)),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the listing."));
        }
    }

    @Override
    public void getListing(String listingId, Callback<Listing> callback) {
        authed("GET", "/v1/listings/" + enc(listingId), null,
                (status, json) -> callback.onSuccess(JsonParsers.parseListing(json)),
                callback);
    }

    @Override
    public void patchListing(String listingId, ListingPatch patch,
            Callback<Listing> callback) {
        try {
            JSONObject body = new JSONObject();
            if (patch.getVariety() != null) {
                body.put("variety", patch.getVariety());
            }
            if (patch.getQuantity() != null) {
                body.put("quantity", patch.getQuantity());
            }
            if (patch.getUnit() != null) {
                body.put("unit", patch.getUnit());
            }
            if (patch.getCreditCost() != null) {
                body.put("credit_cost", patch.getCreditCost());
            }
            if (patch.getExpiresAtMs() != null) {
                body.put("expires_at", JsonParsers.formatIsoUtc(patch.getExpiresAtMs()));
            }
            if (patch.getSprayDisclosure() != null) {
                body.put("spray_disclosure", patch.getSprayDisclosure());
            }
            if (patch.getVisitRules() != null) {
                body.put("visit_rules", patch.getVisitRules());
            }
            if (patch.getStatus() != null) {
                body.put("status", patch.getStatus().getWireValue());
            }
            authed("PATCH", "/v1/listings/" + enc(listingId), body,
                    (status, json) -> callback.onSuccess(JsonParsers.parseListing(json)),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the update."));
        }
    }

    @Override
    public void claimListing(String listingId, Callback<Listing> callback) {
        claimListing(listingId, ClaimRequest.single(), callback);
    }

    @Override
    public void claimListing(String listingId, ClaimRequest request, Callback<Listing> callback) {
        // Backend endpoint lands per API-135: POST /v1/listings/{id}/claims
        // with {quantity, pickupStartMs, pickupEndMs, notes}. The backend
        // checks the claimer's balance, decrements remaining_qty, and
        // assigns the claimer atomically — this is NOT a status PATCH.
        try {
            JSONObject body = new JSONObject();
            body.put("quantity", request.quantity);
            if (request.pickupStartMs != null) {
                body.put("pickupStartMs", request.pickupStartMs);
            }
            if (request.pickupEndMs != null) {
                body.put("pickupEndMs", request.pickupEndMs);
            }
            if (request.notes != null && !request.notes.trim().isEmpty()) {
                body.put("notes", request.notes.trim());
            }
            authed("POST", "/v1/listings/" + enc(listingId) + "/claims", body,
                    (status, json) -> callback.onSuccess(JsonParsers.parseListing(
                            JsonParsers.unwrap(json, "listing"))),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the claim."));
        }
    }

    @Override
    public void cancelClaim(String listingId, Callback<Listing> callback) {
        // Backend endpoint lands per API-135: POST
        // /v1/listings/{id}/claims/cancel. Restores the listing to LIVE and
        // releases the held quantity/credits server-side.
        authed("POST", "/v1/listings/" + enc(listingId) + "/claims/cancel",
                new JSONObject(),
                (status, json) -> callback.onSuccess(JsonParsers.parseListing(
                        JsonParsers.unwrap(json, "listing"))),
                callback);
    }

    @Override
    public void cancelListing(String listingId, Callback<Listing> callback) {
        authed("POST", "/v1/listings/" + enc(listingId) + "/cancel", new JSONObject(),
                (status, json) -> callback.onSuccess(JsonParsers.parseListing(json)),
                callback);
    }

    @Override
    public void listMyListings(Callback<java.util.List<Listing>> callback) {
        authed("GET", "/v1/listings/mine", null,
                (status, json) -> callback.onSuccess(JsonParsers.parseListingItems(json)),
                callback);
    }

    // ------------------------------------------------------------ Wave 2: want-list + harvest

    @Override
    public void getWantList(Callback<java.util.List<WantItem>> callback) {
        authed("GET", "/v1/want-list", null,
                (status, json) -> callback.onSuccess(JsonParsers.parseWantItems(json)),
                callback);
    }

    @Override
    public void addWant(String variety, Callback<WantItem> callback) {
        String trimmed = variety == null ? "" : variety.trim();
        if (trimmed.isEmpty()) {
            fail(callback, new ApiException("invalid_variety", "Variety is required."));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("variety", trimmed);
            authed("POST", "/v1/want-list", body,
                    (status, json) -> callback.onSuccess(JsonParsers.parseWantItem(json)),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the want."));
        }
    }

    @Override
    public void removeWant(String wantId, Callback<Void> callback) {
        authed("DELETE", "/v1/want-list/" + enc(wantId), null,
                (status, json) -> callback.onSuccess(null),
                callback);
    }

    @Override
    public void getMatches(Callback<java.util.List<Listing>> callback) {
        authed("GET", "/v1/want-list/matches", null,
                (status, json) -> callback.onSuccess(JsonParsers.parseListingItems(json)),
                callback);
    }

    @Override
    public void getHarvestEvents(String listingId,
            Callback<java.util.List<HarvestEvent>> callback) {
        authed("GET", "/v1/listings/" + enc(listingId) + "/harvest-events", null,
                (status, json) -> callback.onSuccess(JsonParsers.parseHarvestEvents(json)),
                callback);
    }

    @Override
    public void logHarvestEvent(String listingId, double delta, String note,
            Callback<HarvestEvent> callback) {
        // POST returns {listing, delta_kg, remaining_kg} — not the event row.
        // Follow with a GET of the audit log and return the newest event.
        // Note: the backend stores no note on events; it is not sent.
        try {
            JSONObject body = new JSONObject();
            body.put("listing_id", listingId);
            body.put("delta_kg", delta);
            authed("POST", "/v1/harvest-events", body,
                    (status, json) -> getHarvestEvents(listingId,
                            new Callback<java.util.List<HarvestEvent>>() {
                                @Override
                                public void onSuccess(java.util.List<HarvestEvent> events) {
                                    if (events.isEmpty()) {
                                        callback.onError(new ApiException("parse_error",
                                                "Couldn't read the pick back."));
                                    } else {
                                        callback.onSuccess(
                                                events.get(events.size() - 1));
                                    }
                                }

                                @Override
                                public void onError(ApiException e) {
                                    callback.onError(e);
                                }
                            }),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the pick."));
        }
    }

    // ------------------------------------------------------------ Wave 3: trees

    @Override
    public void getTreeDetail(String treeId, Callback<TreeListing> callback) {
        authed("GET", "/v1/listings/" + enc(treeId), null,
                (status, json) -> {
                    if (!"tree".equals(json.optString("type"))) {
                        callback.onError(new ApiException("not_tree_listing",
                                "This listing isn't a tree."));
                        return;
                    }
                    String ownerUid = json.optString("owner_uid", null);
                    if (ownerUid == null || ownerUid.isEmpty()) {
                        callback.onSuccess(JsonParsers.parseTreeListing(json, null));
                        return;
                    }
                    // Owner display name lives on the public profile endpoint.
                    authed("GET", "/v1/users/" + enc(ownerUid), null,
                            (s2, profile) -> callback.onSuccess(
                                    JsonParsers.parseTreeListing(json,
                                            profile.optString("display_name", null))),
                            new Callback<Void>() {
                                @Override
                                public void onSuccess(Void v) {
                                }

                                @Override
                                public void onError(ApiException e) {
                                    // Name is decorative; the tree is not.
                                    main.post(() -> {
                                        try {
                                            callback.onSuccess(
                                                    JsonParsers.parseTreeListing(json, null));
                                        } catch (Exception ex) {
                                            callback.onError(new ApiException("parse_error",
                                                    "Couldn't understand the server's response."));
                                        }
                                    });
                                }
                            });
                },
                callback);
    }

    @Override
    public void setRipeAlert(String treeId, boolean subscribe, Callback<Boolean> callback) {
        // Reconciled semantic drift: the backend has no per-user ripe
        // subscription. POST /v1/trees/{id}/ripe-alert is an OWNER-ONLY
        // broadcast to want-list matches. subscribe=true triggers it;
        // subscribe=false is a local no-op (documented in api-contract.md).
        if (!subscribe) {
            main.post(() -> callback.onSuccess(false));
            return;
        }
        authed("POST", "/v1/trees/" + enc(treeId) + "/ripe-alert", new JSONObject(),
                (status, json) -> callback.onSuccess(true),
                callback);
    }

    // ------------------------------------------------------------ Wave 3: economy

    @Override
    public void getWallet(Callback<Wallet> callback) {
        authed("GET", "/v1/wallet", null,
                (status, json) -> callback.onSuccess(JsonParsers.parseWallet(json)),
                callback);
    }

    @Override
    public void confirmExchange(String exchangeId, Callback<ExchangeConfirmation> callback) {
        // The exchange id IS the listing id on the wire.
        try {
            JSONObject body = new JSONObject();
            body.put("listing_id", exchangeId);
            body.put("idempotency_key", "android:" + exchangeId);
            final String uid = myUid();
            authed("POST", "/v1/exchange/confirm", body,
                    (status, json) -> callback.onSuccess(
                            JsonParsers.parseExchangeConfirmation(json, exchangeId, uid)),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error",
                    "Couldn't encode the confirmation."));
        }
    }

    // ------------------------------------------------------------ Wave 3: sitters

    @Override
    public void getSitters(String zip, Callback<java.util.List<SitterProfile>> callback) {
        // The backend directory has no geo filter yet; zip is reserved for
        // server-side filtering and currently returns all active sitters.
        authed("GET", "/v1/sitters", null,
                (status, json) -> callback.onSuccess(JsonParsers.parseSitterProfiles(json)),
                callback);
    }

    @Override
    public void getSitter(String sitterId, Callback<SitterProfile> callback) {
        authed("GET", "/v1/sitters/" + enc(sitterId), null,
                (status, json) -> callback.onSuccess(JsonParsers.parseSitterProfile(json)),
                callback);
    }

    @Override
    public void upsertSitterProfile(SitterProfileIn profile, Callback<Void> callback) {
        // Backend PUT /v1/sitters/me: opt in (or update) as a plant sitter.
        // The response is the serialized profile; the client only needs
        // success/failure, same as reportContent.
        try {
            authed("PUT", "/v1/sitters/me", profile.toJson(),
                    (status, json) -> callback.onSuccess(null),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the sitter profile."));
        }
    }

    @Override
    public void setSitterAvailability(java.util.List<String> unavailableIsoDates,
                                      Callback<Void> callback) {
        // Backend PUT /v1/sitters/me/availability: replace semantics.
        try {
            JSONObject body = new JSONObject();
            body.put("unavailable_dates",
                    new org.json.JSONArray(unavailableIsoDates));
            authed("PUT", "/v1/sitters/me/availability", body,
                    (status, json) -> callback.onSuccess(null),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the availability."));
        }
    }

    @Override
    public void requestBooking(BookingRequest request, Callback<Booking> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("sitter_uid", request.getSitterId());
            body.put("plant_count", request.getPlantCount());
            body.put("start_date", JsonParsers.formatDate(request.getStartMs()));
            body.put("end_date", JsonParsers.formatDate(request.getEndMs()));
            String notes = request.getCareInstructions();
            body.put("notes", notes == null ? "" : notes);
            authed("POST", "/v1/sitting-requests", body,
                    (status, json) -> callback.onSuccess(JsonParsers.parseBooking(json)),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error",
                    "Couldn't encode the booking request."));
        }
    }

    @Override
    public void submitReview(String bookingId, Review review, Callback<Void> callback) {
        // bookingId is the sitting-request id; review tags have no wire
        // field and are not sent.
        try {
            JSONObject body = new JSONObject();
            body.put("rating", review.getRating());
            String text = review.getText();
            body.put("comment", text == null ? "" : text);
            authed("POST", "/v1/sitting-requests/" + enc(bookingId) + "/reviews", body,
                    (status, json) -> callback.onSuccess(null),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the review."));
        }
    }

    // ------------------------------------------------------------ PRD parity (planned)

    @Override
    public void getFeed(FeedRequest request, Callback<java.util.List<Listing>> callback) {
        // Backend endpoint lands per API-123: GET /v1/feed?way={way}&limit={n}.
        // The backend owns freshness-first ranking; the client renders in
        // wire order ({ "items": [...] } like the other list endpoints).
        StringBuilder path = new StringBuilder("/v1/feed?limit=");
        path.append(Math.max(1, request.limit));
        if (request.way != null && !request.way.trim().isEmpty()) {
            path.append("&way=").append(enc(request.way.trim()));
        }
        authed("GET", path.toString(), null,
                (status, json) -> callback.onSuccess(JsonParsers.parseListingItems(json)),
                callback);
    }

    @Override
    public void listTrees(Callback<java.util.List<TreeListing>> callback) {
        // Backend endpoint lands per API-126: GET /v1/trees
        // ({ "items": [...] } of tree listing rows).
        authed("GET", "/v1/trees", null,
                (status, json) -> callback.onSuccess(JsonParsers.parseTreeItems(json)),
                callback);
    }

    @Override
    public void reportContent(ReportRequest request, Callback<Void> callback) {
        // Backend endpoint lands per API-143: POST /v1/reports with
        // {targetType, targetId, category, details}. The backend triages;
        // the client shows a confirmation only.
        try {
            JSONObject body = new JSONObject();
            body.put("targetType", request.targetType);
            body.put("targetId", request.targetId);
            body.put("category", request.category);
            if (request.details != null && !request.details.trim().isEmpty()) {
                body.put("details", request.details.trim());
            }
            authed("POST", "/v1/reports", body,
                    (status, json) -> callback.onSuccess(null),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the report."));
        }
    }

    // ------------------------------------------------------------ Wave 3: chat

    @Override
    public void getThreads(Callback<java.util.List<ChatThread>> callback) {
        authed("GET", "/v1/threads", null,
                (status, json) -> callback.onSuccess(JsonParsers.parseChatThreads(json)),
                callback);
    }

    @Override
    public void getMessages(String threadId,
            Callback<java.util.List<ChatMessage>> callback) {
        final String uid = myUid();
        authed("GET", "/v1/threads/" + enc(threadId) + "/messages?limit=100", null,
                (status, json) -> callback.onSuccess(
                        JsonParsers.parseChatMessages(json, uid)),
                callback);
    }

    @Override
    public void sendMessage(String threadId, String text,
            Callback<ChatMessage> callback) {
        if (text == null || text.trim().isEmpty()) {
            fail(callback, new ApiException("empty_message", "Write something first."));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("body", text.trim());
            final String uid = myUid();
            authed("POST", "/v1/threads/" + enc(threadId) + "/messages", body,
                    (status, json) -> callback.onSuccess(
                            JsonParsers.parseChatMessage(json, uid)),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the message."));
        }
    }

    // ------------------------------------------------------------ PRD parity (r2)

    @Override
    public void acceptClaim(String listingId, String claimId, Callback<Listing> callback) {
        // Owner accepts a pending claim; the backend completes the claim
        // atomically. Defensive envelope: accept {listing: {...}} or the
        // bare listing object.
        try {
            JSONObject body = new JSONObject();
            body.put("claimId", claimId);
            authed("POST", "/v1/listings/" + enc(listingId) + "/claims/accept", body,
                    (status, json) -> callback.onSuccess(
                            JsonParsers.parseListing(
                                    JsonParsers.unwrap(json, "listing"))),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the claim."));
        }
    }

    @Override
    public void declineClaim(String listingId, String claimId, Callback<Listing> callback) {
        // Owner declines a pending claim; the backend returns the listing to
        // LIVE and releases the held quantity/credits.
        try {
            JSONObject body = new JSONObject();
            body.put("claimId", claimId);
            authed("POST", "/v1/listings/" + enc(listingId) + "/claims/decline", body,
                    (status, json) -> callback.onSuccess(
                            JsonParsers.parseListing(
                                    JsonParsers.unwrap(json, "listing"))),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error", "Couldn't encode the claim."));
        }
    }

    @Override
    public void listBookings(String role, boolean completedOnly,
            Callback<java.util.List<Booking>> callback) {
        String path = "/v1/bookings?role=" + enc(role == null ? "" : role)
                + "&completed=" + (completedOnly ? "true" : "false");
        authed("GET", path, null,
                (status, json) -> callback.onSuccess(JsonParsers.parseBookings(json)),
                callback);
    }

    @Override
    public void listTreeSlots(String treeId, Callback<java.util.List<Slot>> callback) {
        authed("GET", "/v1/trees/" + enc(treeId) + "/slots", null,
                (status, json) -> callback.onSuccess(JsonParsers.parseSlots(json)),
                callback);
    }

    @Override
    public void claimTreeSlot(String treeId, String slotId, Callback<Slot> callback) {
        authed("POST", "/v1/trees/" + enc(treeId) + "/slots/" + enc(slotId)
                        + "/claim", new JSONObject(),
                (status, json) -> callback.onSuccess(
                        JsonParsers.parseSlot(JsonParsers.unwrap(json, "slot"))),
                callback);
    }

    @Override
    public void sendAttachment(String threadId, String uploadKey,
            Callback<ChatMessage> callback) {
        String trimmed = uploadKey == null ? "" : uploadKey.trim();
        if (trimmed.isEmpty()) {
            fail(callback, new ApiException("empty_attachment", "Pick a photo first."));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("uploadKey", trimmed);
            final String uid = myUid();
            authed("POST", "/v1/threads/" + enc(threadId) + "/attachments", body,
                    (status, json) -> callback.onSuccess(
                            JsonParsers.parseChatMessage(
                                    JsonParsers.unwrap(json, "message"), uid)),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error",
                    "Couldn't encode the attachment."));
        }
    }

    @Override
    public void getCreditExpiry(Callback<CreditExpiry> callback) {
        authed("GET", "/v1/users/me/credit-expiry", null,
                (status, json) -> callback.onSuccess(
                        JsonParsers.parseCreditExpiry(json)),
                callback);
    }

    @Override
    public void getNotificationPrefs(Callback<NotificationPrefs> callback) {
        authed("GET", "/v1/users/me/notification-prefs", null,
                (status, json) -> callback.onSuccess(
                        JsonParsers.parseNotificationPrefs(json)),
                callback);
    }

    @Override
    public void updateNotificationPrefs(NotificationPrefs prefs,
            Callback<NotificationPrefs> callback) {
        try {
            JSONObject body = new JSONObject();
            JSONObject categories = new JSONObject();
            categories.put("harvestAlerts", prefs.isHarvestAlerts());
            categories.put("wantMatches", prefs.isWantMatches());
            categories.put("expiryNudges", prefs.isExpiryNudges());
            categories.put("creditWarnings", prefs.isCreditWarnings());
            categories.put("bookingReminders", prefs.isBookingReminders());
            body.put("categories", categories);
            if (prefs.getQuietHoursStart() != null || prefs.getQuietHoursEnd() != null) {
                JSONObject quiet = new JSONObject();
                if (prefs.getQuietHoursStart() != null) {
                    quiet.put("start", prefs.getQuietHoursStart());
                }
                if (prefs.getQuietHoursEnd() != null) {
                    quiet.put("end", prefs.getQuietHoursEnd());
                }
                body.put("quietHours", quiet);
            }
            authed("PUT", "/v1/users/me/notification-prefs", body,
                    (status, json) -> callback.onSuccess(
                            JsonParsers.parseNotificationPrefs(json)),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error",
                    "Couldn't encode the preferences."));
        }
    }

    @Override
    public void createSittingPaymentIntent(String bookingId,
            Callback<PaymentIntent> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("bookingId", bookingId);
            authed("POST", "/v1/payments/sitting-intent", body,
                    (status, json) -> callback.onSuccess(
                            JsonParsers.parsePaymentIntent(json)),
                    callback);
        } catch (Exception e) {
            fail(callback, new ApiException("encode_error",
                    "Couldn't encode the payment intent."));
        }
    }
}
