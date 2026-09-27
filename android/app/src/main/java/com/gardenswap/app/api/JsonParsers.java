package com.gardenswap.app.api;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pure parsing of backend Wave 1 JSON into client models (AND-090).
 * Uses org.json (Android SDK on device; real artifact in JVM unit tests).
 *
 * <p>Reconciled contract drift (backend Wave 1 vs the client-proposed
 * contract in docs/api-contract.md):
 * <ul>
 *   <li>{@code phoneVerified}: the backend exposes no such field (PII
 *       minimization, SEC-010). The client derives it from
 *       {@code FirebaseUser.getPhoneNumber() != null} — see
 *       {@link HttpGardenSwapApi}.</li>
 *   <li>{@code IdvSession}: {@code POST /v1/idv/session} returns
 *       {@code session_url} only, no session id. The client uses the
 *       trailing URL token as the provisional session id until the backend
 *       adds an explicit field.</li>
 * </ul>
 */
public final class JsonParsers {

    private JsonParsers() {
    }

    /** Parse {@code GET /v1/users/me} (OwnerProfile) into a {@link UserProfile}. */
    public static UserProfile parseUserProfile(JSONObject o, boolean phoneVerified)
            throws JSONException {
        return UserProfile.builder(o.getString("uid"))
                .displayName(o.optString("display_name", null))
                .avatarUrl(o.optString("avatar_url", null))
                .homeZip(o.optString("home_zip", null))
                .phoneVerified(phoneVerified)
                .idvStatus(IdvStatus.fromString(o.optString("idv_status", null)))
                .build();
    }

    /** Parse {@code POST /v1/idv/session} into an {@link IdvSession}. */
    public static IdvSession parseIdvSession(JSONObject o) throws JSONException {
        String url = o.getString("session_url");
        return new IdvSession(extractSessionId(url), url);
    }

    /**
     * Provisional session id: trailing path token of the provider session
     * URL. Replaced by an explicit backend field when available.
     */
    public static String extractSessionId(String sessionUrl) {
        if (sessionUrl == null) {
            return "";
        }
        String trimmed = sessionUrl.endsWith("/")
                ? sessionUrl.substring(0, sessionUrl.length() - 1)
                : sessionUrl;
        int slash = trimmed.lastIndexOf('/');
        return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }

    /** Parse the backend error envelope {@code {code, message, ...}}. */
    public static ApiException parseError(int httpStatus, String body) {
        if (body != null) {
            try {
                JSONObject o = new JSONObject(body);
                JSONObject detail = o.optJSONObject("detail");
                if (detail != null) {
                    o = detail; // FastAPI HTTPException detail nests {code, message}
                }
                String code = o.optString("code", "http_" + httpStatus);
                String message = o.optString("message", "Request failed.");
                return new ApiException(code, message);
            } catch (JSONException e) {
                // fall through to the generic error below
            }
        }
        return new ApiException("http_" + httpStatus, "Request failed (" + httpStatus + ").");
    }
}
