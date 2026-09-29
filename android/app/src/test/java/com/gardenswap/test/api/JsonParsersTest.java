package com.gardenswap.test.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class JsonParsersTest {

    @Test
    public void parseUserProfile() throws Exception {
        JSONObject o = new JSONObject()
                .put("uid", "alice")
                .put("display_name", "Alice")
                .put("avatar_url", "https://example.com/a.png")
                .put("home_zip", "85281")
                .put("idv_status", "verified")
                .put("created_at", "2026-09-27T00:00:00Z");
        UserProfile p = JsonParsers.parseUserProfile(o, true);
        assertEquals("alice", p.getUserId());
        assertEquals("Alice", p.getDisplayName());
        assertEquals("https://example.com/a.png", p.getAvatarUrl());
        assertEquals("85281", p.getHomeZip());
        assertTrue(p.isPhoneVerified());
        assertEquals(IdvStatus.VERIFIED, p.getIdvStatus());
    }

    @Test
    public void parseUserProfileToleratesNullsAndUnknownStatus() throws Exception {
        JSONObject o = new JSONObject()
                .put("uid", "bob")
                .put("idv_status", "something-new");
        UserProfile p = JsonParsers.parseUserProfile(o, false);
        assertEquals("bob", p.getUserId());
        assertEquals(null, p.getDisplayName());
        assertFalse(p.isPhoneVerified());
        assertEquals(IdvStatus.UNVERIFIED, p.getIdvStatus()); // forward-compatible
    }

    @Test
    public void parseIdvSession() throws Exception {
        JSONObject o = new JSONObject()
                .put("session_url", "https://idv-stub.local/sessions/alice-abc123")
                .put("expires_in", 900);
        IdvSession s = JsonParsers.parseIdvSession(o);
        assertEquals("https://idv-stub.local/sessions/alice-abc123", s.getProviderUrl());
        assertEquals("alice-abc123", s.getSessionId());
    }

    @Test
    public void extractSessionIdHandlesEdgeCases() {
        assertEquals("tok", JsonParsers.extractSessionId("https://x/s/tok"));
        assertEquals("tok", JsonParsers.extractSessionId("https://x/s/tok/"));
        assertEquals("", JsonParsers.extractSessionId(null));
    }

    @Test
    public void parseErrorReadsEnvelope() {
        ApiException e = JsonParsers.parseError(409,
                "{\"detail\":{\"code\":\"phone_in_use\",\"message\":\"Already registered.\"}}");
        assertEquals("phone_in_use", e.getCode());
        assertEquals("Already registered.", e.getMessage());
    }

    @Test
    public void parseErrorFallsBackOnGarbage() {
        ApiException e = JsonParsers.parseError(500, "<html>nope</html>");
        assertEquals("http_500", e.getCode());
    }

    @Test
    public void parseErrorHandlesNullBody() {
        ApiException e = JsonParsers.parseError(401, null);
        assertEquals("http_401", e.getCode());
    }
}
