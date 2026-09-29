package com.gardenswap.test.util;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.gardenswap.test.R;

import java.util.List;

/**
 * Central image loading (listing photos, profile avatars).
 *
 * <p>Photos uploaded through {@code /v1/uploads} come back as absolute
 * http(s) public URLs; this loader fetches them with Glide, showing the
 * leaf-glyph placeholder while loading and on failure so a broken URL
 * never leaves a blank hole in the UI.
 */
public final class ImageLoader {

    private ImageLoader() {
    }

    /** Loads {@code url} into {@code view}; no-op when the URL is blank. */
    public static void loadInto(ImageView view, String url) {
        if (view == null || url == null || url.trim().isEmpty()) {
            return;
        }
        Glide.with(view.getContext())
                .load(url.trim())
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
        Glide.with(view.getContext())
                .load(url.trim())
                .placeholder(R.drawable.ic_leaf)
                .error(R.drawable.ic_leaf)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .circleCrop()
                .into(view);
    }
}
