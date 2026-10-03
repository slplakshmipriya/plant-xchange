package com.gardenswap.test.util;

import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.gardenswap.test.BuildConfig;
import com.gardenswap.test.R;

import java.util.List;

/**
 * Central image loading (listing photos, profile avatars).
 *
 * <p>Photos uploaded through {@code /v1/uploads} come back as absolute
 * https public URLs on the photo bucket; this loader fetches them with
 * Glide, showing the leaf-glyph placeholder while loading and on failure
 * so a broken URL never leaves a blank hole in the UI.
 *
 * <p>Release builds only load first-party URLs (the storage bucket or
 * the API host): API-supplied URLs are user-generated content, and an
 * arbitrary off-platform URL would turn every viewer's device into a
 * fetch oracle (IP/timing disclosure). Debug builds allow any https
 * host so mock/sample imagery still renders.
 */
public final class ImageLoader {

    private static final String TAG = "ImageLoader";
    private static final String PHOTO_HOST = "storage.googleapis.com";

    private ImageLoader() {
    }

    /** Loads {@code url} into {@code view}; no-op when the URL is blank. */
    public static void loadInto(ImageView view, String url) {
        if (view == null || url == null || url.trim().isEmpty()) {
            return;
        }
        String trimmed = url.trim();
        if (!isAllowed(trimmed)) {
            Log.w(TAG, "blocked non-first-party image URL");
            return;
        }
        Glide.with(view.getContext())
                .load(trimmed)
                .placeholder(R.drawable.ic_leaf)
                .error(R.drawable.ic_leaf)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .into(view);
    }

    /** Loads the first URL in {@code urls} into {@code view}; no-op when empty. */
    public static void loadFirstInto(ImageView view, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        loadInto(view, urls.get(0));
    }

    /** Circular variant for profile avatars. */
    public static void loadCircularInto(ImageView view, String url) {
        if (view == null || url == null || url.trim().isEmpty()) {
            return;
        }
        String trimmed = url.trim();
        if (!isAllowed(trimmed)) {
            Log.w(TAG, "blocked non-first-party image URL");
            return;
        }
        Glide.with(view.getContext())
                .load(trimmed)
                .placeholder(R.drawable.ic_leaf)
                .error(R.drawable.ic_leaf)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .circleCrop()
                .into(view);
    }

    /**
     * First-party check: https only, on the photo bucket or the API host.
     * Debug builds accept any https host (mock/sample imagery).
     */
    private static boolean isAllowed(String url) {
        if (!url.startsWith("https://")) {
            return false;
        }
        if (BuildConfig.DEBUG) {
            return true;
        }
        String host = Uri.parse(url).getHost();
        if (host == null) {
            return false;
        }
        if (PHOTO_HOST.equals(host)) {
            return true;
        }
        String apiHost = Uri.parse(BuildConfig.API_BASE_URL).getHost();
        return apiHost != null && apiHost.equals(host);
    }
}
