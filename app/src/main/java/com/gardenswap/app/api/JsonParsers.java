package com.gardenswap.app.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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
    public static ApiException parseError(int httpStatus, String body) {        if (body != null) {
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

    // ------------------------------------------------------------ Wave 2/3

    private static final String[] ISO_PATTERNS = {
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
    };

    /** Parse an ISO-8601 instant to epoch millis; 0 when absent/unparseable. */
    public static long parseIsoMs(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0;
        }
        for (String pattern : ISO_PATTERNS) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(pattern, Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                fmt.setLenient(false);
                Date d = fmt.parse(iso);
                if (d != null) {
                    return d.getTime();
                }
            } catch (ParseException e) {
                // try the next pattern
            }
        }
        return 0;
    }

    /** Format epoch millis as an ISO-8601 UTC instant for request bodies. */
    public static String formatIsoUtc(long epochMs) {
        SimpleDateFormat fmt =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(epochMs));
    }

    /** Parse {@code GET /v1/listings/{id}} / {@code /v1/listings/mine} items. */
    public static Listing parseListing(JSONObject o) throws JSONException {
        ListingType type = ListingType.fromString(o.optString("type", null));
        if (type == null) {
            throw new JSONException("unknown listing type: " + o.optString("type"));
        }
        ListingStatus status = ListingStatus.fromString(o.optString("status", null));
        Listing.Builder b = Listing.builder(o.getString("id"))
                .ownerUid(o.optString("owner_uid", null))
                .type(type)
                .variety(o.optString("variety", null))
                .sprayDisclosure(o.optString("spray_disclosure", null))
                .status(status == null ? ListingStatus.DRAFT : status)
                .claimerUid(o.optString("claimer_uid", null))
                .visitRules(o.optString("visit_rules", null));
        long createdAt = parseIsoMs(o.optString("created_at", null));
        if (createdAt > 0) {
            b.createdAtMs(createdAt);
        }
        if (!o.isNull("photos")) {
            JSONArray photos = o.optJSONArray("photos");
            List<String> list = new ArrayList<>();
            if (photos != null) {
                for (int i = 0; i < photos.length(); i++) {
                    list.add(photos.optString(i));
                }
            }
            b.photos(list);
        }
        if (!o.isNull("quantity")) {
            b.quantity(o.optDouble("quantity"));
        }
        b.unit(o.optString("unit", null));
        b.creditCost(o.optInt("credit_cost", 1));
        JSONObject window = o.optJSONObject("pickup_window");
        if (window != null) {
            long start = parseIsoMs(window.optString("start", null));
            long end = parseIsoMs(window.optString("end", null));
            if (start > 0 && end > 0) {
                b.pickupWindow(start, end);
            }
        }
        long expiresAt = parseIsoMs(o.optString("expires_at", null));
        if (expiresAt > 0) {
            b.expiresAtMs(expiresAt);
        }
        if (!o.isNull("geo_lat") && !o.isNull("geo_lon")) {
            b.geo(o.optDouble("geo_lat"), o.optDouble("geo_lon"));
        }
        if (!o.isNull("remaining_qty")) {
            b.remainingQty(o.optDouble("remaining_qty"));
        }
        // The backend has no free/zero-credit concept (credit_cost is 1..3);
        // free is always false on the wire. See docs/api-contract.md.
        b.free(false);
        return b.build();
    }

    /** Parse {@code {"items": [...]}} listing envelopes. */
    public static List<Listing> parseListingItems(JSONObject o) throws JSONException {
        List<Listing> out = new ArrayList<>();
        JSONArray items = o.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                out.add(parseListing(items.getJSONObject(i)));
            }
        }
        return out;
    }

    /** Parse a {@code /v1/want-list} entry. */
    public static WantItem parseWantItem(JSONObject o) throws JSONException {
        return new WantItem(o.getString("id"), o.optString("variety", ""),
                parseIsoMs(o.optString("created_at", null)));
    }

    /** Parse {@code GET /v1/want-list} ({@code {"items": [...]}}). */
    public static List<WantItem> parseWantItems(JSONObject o) throws JSONException {
        List<WantItem> out = new ArrayList<>();
        JSONArray items = o.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                out.add(parseWantItem(items.getJSONObject(i)));
            }
        }
        return out;
    }

    /** Parse a harvest event row ({@code delta_kg}; no note on the wire). */
    public static HarvestEvent parseHarvestEvent(JSONObject o) throws JSONException {
        return new HarvestEvent(o.getString("id"), o.optString("listing_id", ""),
                o.optDouble("delta_kg", 0), "",
                parseIsoMs(o.optString("created_at", null)));
    }

    /** Parse {@code GET /v1/listings/{id}/harvest-events}. */
    public static List<HarvestEvent> parseHarvestEvents(JSONObject o) throws JSONException {
        List<HarvestEvent> out = new ArrayList<>();
        JSONArray events = o.optJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                out.add(parseHarvestEvent(events.getJSONObject(i)));
            }
        }
        return out;
    }

    /**
     * Parse a tree listing. The backend stores no ripe window, per-picker
     * limit, or address-unlock state — those stay at their unset defaults
     * (documented in docs/api-contract.md).
     */
    public static TreeListing parseTreeListing(JSONObject o, String ownerDisplayName)
            throws JSONException {
        return TreeListing.builder(o.getString("id"))
                .variety(o.optString("variety", ""))
                .ripeWindow(0, 0)
                .perPickerLimit(null)
                .pickupRules(o.optString("visit_rules", null))
                .sprayDisclosure(o.optString("spray_disclosure", null))
                .ownerDisplayName(ownerDisplayName)
                .addressUnlocked(false)
                .ripeAlertsSubscribed(false)
                .build();
    }

    /** Parse {@code GET /v1/trees} ({@code {"items": [...]}}). */
    public static List<TreeListing> parseTreeItems(JSONObject o) throws JSONException {
        List<TreeListing> out = new ArrayList<>();
        JSONArray items = o.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                out.add(parseTreeListing(items.getJSONObject(i), null));
            }
        }
        return out;
    }

    /** Parse {@code GET /v1/wallet}. */
    public static Wallet parseWallet(JSONObject o) throws JSONException {
        List<LedgerEntry> entries = new ArrayList<>();
        JSONArray arr = o.optJSONArray("entries");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                int delta = e.optInt("delta", 0);
                LedgerEntry.Kind kind =
                        delta > 0 ? LedgerEntry.Kind.ISSUED : LedgerEntry.Kind.SPENT;
                entries.add(new LedgerEntry(e.optString("id", ""), delta, kind,
                        e.optString("reason", ""), e.optString("ref_id", null),
                        parseIsoMs(e.optString("created_at", null)), null));
            }
        }
        // No seasonal expiry on the wire yet: nextExpiryMs null, earnedThisWeek 0.
        return new Wallet(o.optInt("balance", 0), null, 0, entries);
    }

    /**
     * Parse a sitter profile. {@code rate_credits} (credits/visit) maps 1:1
     * onto {@code ratePerVisitCents} until fiat pricing lands — the value is
     * denominated in credits, not cents (docs/api-contract.md). The backend
     * has no services taxonomy: services is always empty.
     */
    public static SitterProfile parseSitterProfile(JSONObject o) throws JSONException {
        double rating = o.isNull("rating_avg") ? 0.0 : o.optDouble("rating_avg", 0.0);
        return new SitterProfile(o.getString("uid"),
                o.optString("display_name", "Sitter"),
                new String[0],
                o.optInt("rate_credits", 1),
                (int) Math.round(o.optDouble("service_radius_miles", 5)),
                o.optBoolean("idv_verified", false),
                rating,
                o.optInt("rating_count", 0),
                o.optInt("completed_sits", 0));
    }

    /** Parse {@code GET /v1/sitters} ({@code {"sitters": [...]}}). */
    public static List<SitterProfile> parseSitterProfiles(JSONObject o) throws JSONException {
        List<SitterProfile> out = new ArrayList<>();
        JSONArray arr = o.optJSONArray("sitters");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                out.add(parseSitterProfile(arr.getJSONObject(i)));
            }
        }
        return out;
    }

    /**
     * Backend request states ({@code requested/accepted/declined/...}) onto
     * the client enum: accepted → CONFIRMED, declined → CANCELLED.
     */
    public static BookingStatus parseBookingStatus(String wire) {
        if (wire == null) {
            return BookingStatus.REQUESTED;
        }
        switch (wire.trim().toLowerCase(Locale.US)) {
            case "accepted":
                return BookingStatus.CONFIRMED;
            case "declined":
                return BookingStatus.CANCELLED;
            default:
                return BookingStatus.fromString(wire);
        }
    }

    /**
     * Parse a sitting request. The backend prices sits in credits and has no
     * platform-fee concept yet; totalCents/feeCents are client-side estimates
     * (flat mock-era numbers) until the fee decision lands server-side.
     */
    public static Booking parseBooking(JSONObject o) throws JSONException {
        int total = 1500;
        int fee = Math.round(total * 0.18f);
        return new Booking(o.getString("id"), o.optString("sitter_uid", ""),
                parseBookingStatus(o.optString("status", null)),
                parseDateMs(o.optString("start_date", null)),
                parseDateMs(o.optString("end_date", null)),
                new String[0], total, fee, o.optString("notes", ""));
    }

    /** Parse a {@code YYYY-MM-DD} date to epoch millis (start of day, local). */
    public static long parseDateMs(String date) {
        if (date == null || date.isEmpty()) {
            return 0;
        }
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            fmt.setLenient(false);
            Date d = fmt.parse(date);
            return d == null ? 0 : d.getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    /** Format epoch millis as {@code YYYY-MM-DD} (local) for request bodies. */
    public static String formatDate(long epochMs) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return fmt.format(new Date(epochMs));
    }

    /** Parse {@code GET /v1/threads} items into {@link ChatThread}. */
    public static ChatThread parseChatThread(JSONObject o) throws JSONException {
        String variety = o.optString("listing_variety", "Listing");
        int credits = o.optInt("listing_credit_cost", 0);
        String summary = credits > 0
                ? variety + " \u00b7 " + credits + (credits == 1 ? " credit" : " credits")
                : variety;
        return new ChatThread(o.getString("id"), summary,
                o.optString("listing_status", ""),
                o.optString("other_display_name", "Neighbor"),
                o.optString("last_message_preview", ""),
                parseIsoMs(o.optString("last_message_at", null)),
                0); // no read receipts on the wire; unread is always 0
    }

    /** Parse {@code GET /v1/threads} ({@code {"threads": [...]}}). */
    public static List<ChatThread> parseChatThreads(JSONObject o) throws JSONException {
        List<ChatThread> out = new ArrayList<>();
        JSONArray arr = o.optJSONArray("threads");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                out.add(parseChatThread(arr.getJSONObject(i)));
            }
        }
        return out;
    }

    /** Parse thread messages; {@code myUid} decides {@code isMine}. */
    public static ChatMessage parseChatMessage(JSONObject o, String myUid)
            throws JSONException {
        String senderUid = o.optString("sender_uid", "");
        boolean mine = myUid != null && myUid.equals(senderUid);
        String name = o.optString("sender_display_name", null);
        if (name == null || name.isEmpty()) {
            name = mine ? "You" : "Neighbor";
        }
        return new ChatMessage(o.getString("id"), o.optString("thread_id", ""),
                name, mine, ChatMessage.Kind.TEXT, o.optString("body", ""),
                parseIsoMs(o.optString("created_at", null)));
    }

    /** Parse {@code GET /v1/threads/{id}/messages}. */
    public static List<ChatMessage> parseChatMessages(JSONObject o, String myUid)
            throws JSONException {
        List<ChatMessage> out = new ArrayList<>();
        JSONArray arr = o.optJSONArray("messages");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                out.add(parseChatMessage(arr.getJSONObject(i), myUid));
            }
        }
        return out;
    }

    /**
     * Parse {@code POST /v1/exchange/confirm}. The exchange id IS the listing
     * id on the wire; {@code already_confirmed} replays count as confirmed.
     */
    public static ExchangeConfirmation parseExchangeConfirmation(JSONObject o,
            String exchangeId, String myUid) throws JSONException {
        boolean completed = "completed".equalsIgnoreCase(o.optString("status", ""));
        boolean already = "already_confirmed".equalsIgnoreCase(o.optString("status", ""));
        JSONArray confirmedBy = o.optJSONArray("confirmed_by");
        boolean mine = false;
        boolean other = false;
        if (confirmedBy != null) {
            for (int i = 0; i < confirmedBy.length(); i++) {
                String uid = confirmedBy.optString(i);
                if (myUid != null && myUid.equals(uid)) {
                    mine = true;
                } else {
                    other = true;
                }
            }
        }
        JSONObject listing = o.optJSONObject("listing");
        int cost = listing != null ? listing.optInt("credit_cost", 0) : 0;
        return new ExchangeConfirmation(exchangeId, mine || already,
                other || already, cost, completed || already);
    }
}
