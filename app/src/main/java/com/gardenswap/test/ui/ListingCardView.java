package com.gardenswap.test.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.util.ImageLoader;
import com.gardenswap.test.util.ListingCardLogic;

/**
 * Reusable listing card (UID-011): photo area, kind tag, title, credit
 * cost, distance, urgency cue. Matches the garden-swap-app-ui-design
 * prototype: 18dp card, surface background, ink text.
 *
 * <p>Shows the first listing photo via {@link ImageLoader} when the listing
 * has one; otherwise the styled leaf-glyph placeholder.
 */
public class ListingCardView extends LinearLayout {

    private final TextView kindTag;
    private final TextView titleView;
    private final TextView metaView;
    private final TextView cueView;
    private final ImageView photoView;
    private final ImageView photoGlyph;
    private final TextView photoCountBadge;

    public ListingCardView(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundResource(R.drawable.card_bg);
        setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Photo area: real photo when the listing has one, else the styled
        // leaf-glyph placeholder.
        FrameLayout photo = new FrameLayout(context);
        photo.setBackgroundResource(R.drawable.photo_placeholder_bg);
        photo.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 140)));

        photoGlyph = new ImageView(context);
        photoGlyph.setImageResource(R.drawable.ic_leaf);
        int glyphSize = Ui.dp(context, 56);
        photoGlyph.setLayoutParams(new FrameLayout.LayoutParams(
                glyphSize, glyphSize, Gravity.CENTER));
        photo.addView(photoGlyph);

        photoView = new ImageView(context);
        photoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photoView.setVisibility(View.GONE);
        photo.addView(photoView);

        kindTag = Ui.chip(context, "");
        kindTag.setClickable(false);
        kindTag.setFocusable(false);
        FrameLayout.LayoutParams tagParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        int tagMargin = Ui.dp(context, 12);
        tagParams.setMargins(tagMargin, tagMargin, tagMargin, tagMargin);
        kindTag.setLayoutParams(tagParams);
        photo.addView(kindTag);

        // Photo-count badge (bottom-end) when the listing has multiple photos.
        photoCountBadge = Ui.chip(context, "");
        photoCountBadge.setClickable(false);
        photoCountBadge.setFocusable(false);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        badgeParams.setMargins(tagMargin, tagMargin, tagMargin, tagMargin);
        photoCountBadge.setLayoutParams(badgeParams);
        photoCountBadge.setVisibility(View.GONE);
        photo.addView(photoCountBadge);

        addView(photo);

        // Text content.
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        int sidePadding = Ui.dp(context, 16);
        body.setPadding(sidePadding, Ui.dp(context, 12), sidePadding, sidePadding);
        body.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        titleView = Ui.title(context, "");
        body.addView(titleView);

        metaView = Ui.body(context, "");
        body.addView(metaView);

        cueView = Ui.caption(context, "");
        cueView.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_orange, context.getTheme()));
        cueView.setVisibility(View.GONE);
        body.addView(cueView);

        addView(body);
    }

    /** Binds the listing; distance row hidden when {@code distanceText} is null/empty. */
    public void bind(Listing listing, String distanceText) {
        if (listing == null) {
            return;
        }
        kindTag.setText(ListingCardLogic.kindLabel(listing.getType()));
        titleView.setText(listing.getVariety() != null ? listing.getVariety() : "");

        String cost = listing.isFree()
                ? "Free"
                : listing.getCreditCost() + (listing.getCreditCost() == 1 ? " credit" : " credits");
        String meta = cost;
        if (distanceText != null && !distanceText.trim().isEmpty()) {
            meta += "  ·  " + distanceText.trim();
        }
        metaView.setText(meta);

        String cue = ListingCardLogic.urgencyCue(listing, System.currentTimeMillis());
        cueView.setVisibility(cue != null ? View.VISIBLE : View.GONE);
        if (cue != null) {
            cueView.setText(cue);
        }

        // Photo: show the first listing photo; fall back to the leaf placeholder.
        // A count badge signals when there are more photos to see in detail.
        java.util.List<String> photos = listing.getPhotos();
        boolean hasPhoto = photos != null && !photos.isEmpty()
                && photos.get(0) != null && !photos.get(0).trim().isEmpty();
        photoView.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        photoGlyph.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
        if (hasPhoto) {
            ImageLoader.loadFirstInto(photoView, photos);
        }
        boolean multiPhoto = photos != null && photos.size() > 1;
        photoCountBadge.setVisibility(multiPhoto ? View.VISIBLE : View.GONE);
        if (multiPhoto) {
            photoCountBadge.setText(photos.size() + " photos");
        }
    }

    public void bind(Listing listing) {
        bind(listing, null);
    }
}
