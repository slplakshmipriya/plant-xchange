package com.gardenswap.test.brand;

import com.gardenswap.test.R;

/**
 * Single source of brand identity for this build (Gardenia v2
 * configurability test). The backend, database, API payloads, and package
 * identity are unchanged from the base app; only the client-side
 * brand/theme differs.
 *
 * <p>The user-visible display name lives in {@code strings.xml}
 * ({@code app_name}) so the launcher label and in-app text can never drift;
 * Java call sites read it via {@link #APP_NAME_RES} with a Context. A future
 * vertical is a new string + theme tokens + {@link #VERTICAL_ID} — never
 * renamed API keys, enum values, or error codes.
 */
public final class BrandConfig {

    /** Vertical/use-case key for this build. Client-side only. */
    public static final String VERTICAL_ID = "gardenia";

    /** Display-name resource; resolve with a Context, never hardcode. */
    public static final int APP_NAME_RES = R.string.app_name;

    /** In-app title mark drawable (also the launcher foreground). */
    public static final int TITLE_MARK_RES = R.drawable.ic_gardenia;

    private BrandConfig() {
    }
}
