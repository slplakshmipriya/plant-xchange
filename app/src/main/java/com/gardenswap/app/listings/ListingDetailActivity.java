package com.gardenswap.app.listings;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.app.R;
import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.Listing;
import com.gardenswap.app.api.ListingStatus;
import com.gardenswap.app.ui.ClaimBottomSheet;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.ui.VerifiedBadgeView;
import com.gardenswap.app.util.DetailViewLogic;
import com.gardenswap.app.util.ListingCardLogic;
import com.gardenswap.app.util.ListingDetailLogic;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

/**
 * Listing detail restyle (UID-012). Matches the garden-swap-app-ui-design
 * prototype: full-width photo header, eyebrow detail sections, visit-rules
 * card, owner row, and a primary Claim CTA.
 *
 * <p>Input contract is unchanged: {@link #EXTRA_LISTING_ID} carries the
 * listing id (missing id toasts and finishes), loading is still via
 * {@code ApiProvider.get().getListing}, and the action row visibility is
 * still derived from {@link ListingDetailLogic} — only the presentation
 * changed. Claim routes through {@link #openClaimSheet()}, which opens the
 * UID-013 {@code ClaimBottomSheet}.
 *
 * <p>No bottom-nav attach: detail is not a top-level tab.
 */
public class ListingDetailActivity extends AppCompatActivity {

    public static final String EXTRA_LISTING_ID = "listing_id";

    private String viewerUid;
    private Listing listing;
    private LinearLayout page;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        viewerUid = user == null ? null : user.getUid();

        String listingId = getIntent().getStringExtra(EXTRA_LISTING_ID);
        if (listingId == null) {
            Toast.makeText(this, "Missing listing id", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout loading = Ui.column(this, 20);
        statusView = Ui.status(this);
        loading.addView(statusView);
        page.addView(loading);

        setContentView(scroll);
        load(listingId);
    }

    private void load(String listingId) {
        statusView.setText("Loading…");
        ApiProvider.get().getListing(listingId, new GardenSwapApi.Callback<Listing>() {
            @Override
            public void onSuccess(Listing result) {
                listing = result;
                render();
            }

            @Override
            public void onError(ApiException error) {
                statusView.setText("Could not load listing: " + error.getMessage());
            }
        });
    }

    private void render() {
        page.removeAllViews();
        page.addView(photoHeader());

        LinearLayout body = Ui.column(this, 20);
        page.addView(body);

        TextView statusChip = Ui.chip(this, ListingDetailLogic.statusLabel(listing.getStatus()));
        statusChip.setClickable(false);
        statusChip.setFocusable(false);
        body.addView(statusChip);
        Ui.gap(body, this, 8);

        body.addView(Ui.display(this,
                listing.getVariety() == null ? "Listing" : listing.getVariety()));
        Ui.gap(body, this, 4);
        body.addView(Ui.body(this,
                DetailViewLogic.creditLine(listing.isFree(), listing.getCreditCost())));
        body.addView(Ui.caption(this, "Freshness: " + ListingDetailLogic.formatCountdown(
                listing.getExpiresAtMs(), System.currentTimeMillis())));
        Ui.gap(body, this, 8);

        // 012-T2: detail sections.
        addSection(body, "Variety", listing.getVariety());
        addSection(body, "Quantity",
                DetailViewLogic.quantityLine(listing.getQuantity(), listing.getUnit()));
        addSection(body, "Spray disclosure", listing.getSprayDisclosure());
        Ui.gap(body, this, 4);

        // 012-T3: visit-rules panel.
        body.addView(visitRulesPanel());
        Ui.gap(body, this, 12);

        // 012-T4: owner row.
        body.addView(ownerRow());
        Ui.gap(body, this, 4);

        // SEC-010: only fuzzed coordinates ever reach the client.
        body.addView(Ui.caption(this, "Pickup: approximate location (±0.5 mi)"));
        Ui.gap(body, this, 16);

        // Action row — visibility rules unchanged (DetailViewLogic).
        if (DetailViewLogic.isClaimCtaVisible(
                listing.getStatus(), listing.getOwnerUid(), viewerUid)) {
            Button claimButton = Ui.primaryButton(this, "Claim");
            claimButton.setOnClickListener(v -> openClaimSheet());
            body.addView(claimButton);
            Ui.gap(body, this, 8);
        }
        if (DetailViewLogic.isCancelCtaVisible(
                listing.getStatus(), listing.getOwnerUid(), viewerUid)) {
            Button cancelButton = Ui.secondaryButton(this, "Cancel listing");
            cancelButton.setOnClickListener(v -> confirmCancel());
            body.addView(cancelButton);
            Ui.gap(body, this, 8);
        }
        // AND-133: claimer-side cancellation — only the claimer sees this,
        // gated on CLAIMED status + claimerUid match client-side (the server
        // enforces it again on the cancel endpoint).
        if (listing.getStatus() == ListingStatus.CLAIMED
                && viewerUid != null
                && viewerUid.equals(listing.getClaimerUid())) {
            Button cancelClaimButton = Ui.secondaryButton(this, "Cancel claim");
            cancelClaimButton.setOnClickListener(v -> confirmCancelClaim());
            body.addView(cancelClaimButton);
            Ui.gap(body, this, 8);
        }
        if (isOwnHarvestListing()) {
            Button logButton = Ui.secondaryButton(this, "Harvest log");
            logButton.setOnClickListener(v ->
                    com.gardenswap.app.harvest.HarvestLogActivity.open(this, listing.getId()));
            body.addView(logButton);
            Ui.gap(body, this, 8);
        }
        if (DetailViewLogic.showTerminalNotice(listing.getStatus())) {
            TextView terminal = Ui.caption(this,
                    "This listing is " + ListingDetailLogic.statusLabel(listing.getStatus()).toLowerCase()
                            + " and read-only.");
            terminal.setGravity(Gravity.CENTER);
            body.addView(terminal);
        }
    }

    /** 012-T1: full-width photo header reusing the card's placeholder treatment. */
    private View photoHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setBackgroundResource(R.drawable.photo_placeholder_bg);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 220)));

        ImageView glyph = new ImageView(this);
        glyph.setImageResource(R.drawable.ic_leaf);
        int glyphSize = Ui.dp(this, 72);
        glyph.setLayoutParams(new FrameLayout.LayoutParams(
                glyphSize, glyphSize, Gravity.CENTER));
        header.addView(glyph);

        TextView kindTag = Ui.chip(this, ListingCardLogic.kindLabel(listing.getType()));
        kindTag.setClickable(false);
        kindTag.setFocusable(false);
        FrameLayout.LayoutParams tagParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        int margin = Ui.dp(this, 16);
        tagParams.setMargins(margin, margin, margin, margin);
        kindTag.setLayoutParams(tagParams);
        header.addView(kindTag);

        TextView photoCount = Ui.caption(this,
                DetailViewLogic.photoCountLabel(listing.getPhotos().size()));
        photoCount.setTextColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_nav_ink, getTheme()));
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        countParams.setMargins(margin, margin, margin, margin);
        photoCount.setLayoutParams(countParams);
        header.addView(photoCount);

        return header;
    }

    /** 012-T2: eyebrow + body detail section. */
    private void addSection(LinearLayout parent, String eyebrow, String value) {
        parent.addView(Ui.eyebrow(this, eyebrow));
        String text = (value == null || value.trim().isEmpty()) ? "—" : value;
        parent.addView(Ui.body(this, text));
        Ui.gap(parent, this, 12);
    }

    /** 012-T3: visit-rules card; one bullet row per rule line. */
    private View visitRulesPanel() {
        LinearLayout panel = Ui.card(this);
        panel.addView(Ui.eyebrow(this, "Visit rules"));
        Ui.gap(panel, this, 8);
        List<String> rules = DetailViewLogic.visitRuleItems(listing.getVisitRules());
        if (rules.isEmpty()) {
            panel.addView(Ui.body(this, "No visit rules provided."));
        } else {
            for (String rule : rules) {
                panel.addView(Ui.body(this, "\u2022 " + rule));
                Ui.gap(panel, this, 4);
            }
        }
        return panel;
    }

    /**
     * 012-T4: owner row — avatar placeholder, name, verification badge.
     * The API exposes no per-owner profile endpoint (only {@code getMe}),
     * so the name is a generic label until owner profiles are available.
     */
    private View ownerRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = new TextView(this);
        avatar.setText("G");
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_nav_ink, getTheme()));
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_leaf, getTheme()));
        avatar.setBackground(avatarBg);
        int avatarSize = Ui.dp(this, 44);
        LinearLayout.LayoutParams avatarParams =
                new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarParams.setMarginEnd(Ui.dp(this, 12));
        avatar.setLayoutParams(avatarParams);
        row.addView(avatar);

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.addView(Ui.title(this, "Giver"));
        nameCol.addView(new VerifiedBadgeView(this));
        row.addView(nameCol);

        return row;
    }

    /**
     * 012-T5 / 013-T3: claim entry point — opens the claim bottom sheet.
     * On a successful claim the returned listing replaces the local one and
     * the detail re-renders, mirroring the pre-012 dialog's
     * {@code listing = result; render();}.
     */
    private void openClaimSheet() {
        ClaimBottomSheet.show(this, listing.getId(), result -> {
            listing = result;
            render();
        });
    }

    private void confirmCancel() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel this listing?")
                .setMessage("It will no longer be visible to swappers.")
                .setPositiveButton("Cancel listing", (dialog, which) ->
                        ApiProvider.get().cancelListing(listing.getId(),
                                new GardenSwapApi.Callback<Listing>() {
                                    @Override
                                    public void onSuccess(Listing result) {
                                        listing = result;
                                        Toast.makeText(ListingDetailActivity.this,
                                                "Listing cancelled", Toast.LENGTH_SHORT).show();
                                        render();
                                    }

                                    @Override
                                    public void onError(ApiException e) {
                                        Toast.makeText(ListingDetailActivity.this,
                                                e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                }))
                .setNegativeButton("Keep", null)
                .show();
    }

    /**
     * AND-133: claimer-side claim cancellation. The button only renders for
     * the claimer (CLAIMED status + claimerUid match); the server enforces
     * the same rule on the cancel endpoint. On success the returned listing
     * replaces the local one and the detail re-renders.
     */
    private void confirmCancelClaim() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel your claim?")
                .setMessage("The listing will become available to other swappers again.")
                .setPositiveButton("Cancel claim", (dialog, which) ->
                        ApiProvider.get().cancelClaim(listing.getId(),
                                new GardenSwapApi.Callback<Listing>() {
                                    @Override
                                    public void onSuccess(Listing result) {
                                        listing = result;
                                        Toast.makeText(ListingDetailActivity.this,
                                                "Claim cancelled", Toast.LENGTH_SHORT).show();
                                        render();
                                    }

                                    @Override
                                    public void onError(ApiException e) {
                                        String message = e.getMessage();
                                        Toast.makeText(ListingDetailActivity.this,
                                                message == null || message.trim().isEmpty()
                                                        ? "Couldn't cancel the claim. Try again."
                                                        : message,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .setNegativeButton("Keep claim", null)
                .show();
    }

    private boolean isOwnHarvestListing() {
        return listing.getType() == com.gardenswap.app.api.ListingType.HARVEST
                && viewerUid != null
                && viewerUid.equals(listing.getOwnerUid());
    }

    /** Convenience launcher. */
    public static void open(android.content.Context context, String listingId) {
        Intent intent = new Intent(context, ListingDetailActivity.class);
        intent.putExtra(EXTRA_LISTING_ID, listingId);
        context.startActivity(intent);
    }
}
