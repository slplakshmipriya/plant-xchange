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
import com.gardenswap.test.util.ListingCardLogic;

/**
 * Reusable listing card (UID-011): photo area, kind tag, title, credit
 * cost, distance, urgency cue. Matches the garden-swap-app-ui-design
 * prototype: 18dp card, surface background, ink text.
 *
 * <p>No image-loading dependency exists in the app, so the photo area is
 * always a styled placeholder (leaf glyph on leaf green). When an image
 * loader is added, load {@code listing.getPhotos()} here instead.
 */
public class ListingCardView extends LinearLayout {

    private final TextView kindTag;
    private final TextView titleView;
    private final TextView metaView;
    private final TextView cueView;

    public ListingCardView(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundResource(R.drawable.card_bg);
        setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Photo area: placeholder box with leaf glyph and overlaid kind tag.
        FrameLayout photo = new FrameLayout(context);
        photo.setBackgroundResource(R.drawable.photo_placeholder_bg);
        photo.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 140)));

        ImageView glyph = new ImageView(context);
        glyph.setImageResource(R.drawable.ic_leaf);
        int glyphSize = Ui.dp(context, 56);
        glyph.setLayoutParams(new FrameLayout.LayoutParams(
                glyphSize, glyphSize, Gravity.CENTER));
        photo.addView(glyph);

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
    }

    public void bind(Listing listing) {
        bind(listing, null);
    }
}
