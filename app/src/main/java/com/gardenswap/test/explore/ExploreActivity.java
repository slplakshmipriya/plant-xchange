package com.gardenswap.test.explore;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.FeedRequest;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.Listing;
import com.gardenswap.test.api.ListingType;
import com.gardenswap.test.api.UserProfile;
import com.gardenswap.test.api.Wallet;
import com.gardenswap.test.listings.CreateListingActivity;
import com.gardenswap.test.listings.ListingDetailActivity;
import com.gardenswap.test.sitters.SitterListActivity;
import com.gardenswap.test.ui.FilterChipRow;
import com.gardenswap.test.ui.ListingCardView;
import com.gardenswap.test.ui.Nav;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.ExploreLogic;
import com.gardenswap.test.util.ListingFilter;
import com.gardenswap.test.util.NavRouter;
import com.gardenswap.test.wallet.WalletActivity;
import com.gardenswap.test.wantlist.WantListActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Explore home screen (UID-010): hero header, four "way" cards, credit
 * balance panel, want-list match panel, and the nearby-listings feed with
 * working filter chips. The feed loads from the API contract
 * ({@code getFeed}); every callback is null-safe.
 */
public class ExploreActivity extends AppCompatActivity {

    private final List<Listing> allListings = new ArrayList<>();
    private LinearLayout feedList;
    private FilterChipRow chipRow;
    private TextView emptyState;
    private TextView heroSubline;
    private TextView walletLine;
    private TextView wantLine;

    /**
     * Create/detail launcher: refreshes the feed when the user published,
     * claimed, or cancelled a listing (those activities set RESULT_OK).
     */
    private final ActivityResultLauncher<Intent> refreshLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            refresh();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sticky title bar: the leaf + wordmark row never scrolls away.
        // A tiny reload icon sits at the top right for manual refresh.
        LinearLayout header = Ui.column(this, 20);
        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand = Ui.appTitleRow(this);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        brandRow.addView(brand);
        ImageButton refreshButton = new ImageButton(this);
        refreshButton.setImageResource(android.R.drawable.ic_popup_sync);
        refreshButton.setImageTintList(ColorStateList.valueOf(
                ResourcesCompat.getColor(getResources(),
                        R.color.garden_leaf, getTheme())));
        refreshButton.setBackground(null);
        refreshButton.setContentDescription("Refresh listings");
        int iconSize = Ui.dp(this, 36);
        refreshButton.setLayoutParams(
                new LinearLayout.LayoutParams(iconSize, iconSize));
        int iconPad = Ui.dp(this, 6);
        refreshButton.setPadding(iconPad, iconPad, iconPad, iconPad);
        refreshButton.setOnClickListener(v -> refresh());
        brandRow.addView(refreshButton);
        header.addView(brandRow);
        int pad = Ui.dp(this, 20);
        header.setPadding(pad, pad, pad, 0);

        LinearLayout root = Ui.column(this, 20);
        root.setPadding(pad, 0, pad, pad);
        heroSubline = Ui.body(this, "Find fresh swaps near you");
        root.addView(heroSubline);
        Ui.gap(root, this, 20);

        root.addView(Ui.eyebrow(this, "Ways to swap"));
        Ui.gap(root, this, 8);
        root.addView(wayCardRow(
                ExploreLogic.WAY_SEEDLINGS, "Baby plants from neighbors",
                ExploreLogic.WAY_PICK, "Harvest from local trees"));
        Ui.gap(root, this, 12);
        root.addView(wayCardRow(
                ExploreLogic.WAY_HARVEST, "Fresh-picked produce",
                ExploreLogic.WAY_CARE, "Find a plant sitter"));
        Ui.gap(root, this, 20);

        root.addView(walletPanel());
        Ui.gap(root, this, 20);

        root.addView(wantPanel());
        Ui.gap(root, this, 20);

        LinearLayout listingsHeader = new LinearLayout(this);
        listingsHeader.setOrientation(LinearLayout.HORIZONTAL);
        listingsHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView listingsTitle = Ui.headline(this, "Nearby listings");
        listingsTitle.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button newListing = Ui.rowButton(this, "+ Create your listing", false);
        newListing.setOnClickListener(v ->
                refreshLauncher.launch(new Intent(this, CreateListingActivity.class)));
        listingsHeader.addView(listingsTitle);
        listingsHeader.addView(newListing);
        root.addView(listingsHeader);
        Ui.gap(root, this, 8);
        chipRow = new FilterChipRow(this);
        chipRow.setOnFilterChanged(this::applyFilter);
        chipRow.setOnCareSelected(() ->
                startActivity(new Intent(this, SitterListActivity.class)));
        root.addView(chipRow);
        Ui.gap(root, this, 8);
        // The feed is a plain vertical view group inside the ScrollView, not a
        // RecyclerView: a RecyclerView with wrap_content inside a ScrollView
        // is a known-fragile pattern (measurement/touch fights between the
        // two scrollers), and the feed page is capped at 50 items, so view
        // recycling buys nothing here.
        feedList = new LinearLayout(this);
        feedList.setOrientation(LinearLayout.VERTICAL);
        root.addView(feedList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        emptyState = Ui.caption(this, "No listings nearby yet — try another filter.");
        emptyState.setVisibility(TextView.GONE);
        root.addView(emptyState);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(Ui.stickyHeaderScreen(this, header, scroll));
        Nav.attach(this, NavRouter.Tab.EXPLORE);

        loadProfile();
        loadWallet();
        loadWantMatches();
        loadFeed();
    }

    /** Two way cards side by side. */
    private LinearLayout wayCardRow(String leftLabel, String leftSub,
                                    String rightLabel, String rightSub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(wayCard(leftLabel, leftSub),
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightParams.leftMargin = Ui.dp(this, 12);
        row.addView(wayCard(rightLabel, rightSub), rightParams);
        return row;
    }

    private LinearLayout wayCard(String label, String subtitle) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, label));
        Ui.gap(card, this, 4);
        card.addView(Ui.caption(this, subtitle));
        card.setClickable(true);
        card.setFocusable(true);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                ripple, true);
        card.setForeground(ResourcesCompat.getDrawable(
                getResources(), ripple.resourceId, getTheme()));
        card.setOnClickListener(v -> onWayCardTap(label));
        return card;
    }

    private void onWayCardTap(String label) {
        if (ExploreLogic.WAY_PICK.equals(label)) {
            // TreeListActivity is owned by the pyo track; start it by
            // explicit component name so this compiles before that class
            // lands on the branch.
            Intent pick = new Intent();
            pick.setClassName(this, "com.gardenswap.test.trees.TreeListActivity");
            startActivity(pick);
            return;
        }
        ListingType filter = ExploreLogic.filterForWayCard(label);
        if (filter == null) {
            // Plant care has no listing filter; it opens the sitter flow.
            startActivity(new Intent(this, SitterListActivity.class));
            return;
        }
        chipRow.select(ExploreLogic.chipIndexForWayCard(label));
    }

    /** Acid-highlighted credit balance panel; tap opens the wallet. */
    private LinearLayout walletPanel() {
        LinearLayout panel = Ui.card(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 18));
        bg.setColor(ResourcesCompat.getColor(
                getResources(), R.color.garden_acid, getTheme()));
        panel.setBackground(bg);
        TextView balanceEyebrow = Ui.eyebrow(this, "Credit balance");
        Ui.textColor(this, balanceEyebrow, R.color.garden_turquoise);
        panel.addView(balanceEyebrow);
        Ui.gap(panel, this, 4);
        walletLine = Ui.title(this, "— credits");
        Ui.textColor(this, walletLine, R.color.garden_turquoise);
        panel.addView(walletLine);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setOnClickListener(
                v -> startActivity(new Intent(this, WalletActivity.class)));
        return panel;
    }

    /** Want-list match panel; tap opens the want list. */
    private LinearLayout wantPanel() {
        LinearLayout panel = Ui.card(this);
        panel.addView(Ui.eyebrow(this, "Want list"));
        Ui.gap(panel, this, 4);
        wantLine = Ui.body(this, "Checking for matches…");
        panel.addView(wantLine);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setOnClickListener(
                v -> startActivity(new Intent(this, WantListActivity.class)));
        return panel;
    }

    private void applyFilter(ListingType filter) {
        List<Listing> shown = ListingFilter.filter(allListings, filter);
        feedList.removeAllViews();
        int margin = Ui.dp(this, 12);
        for (Listing listing : shown) {
            ListingCardView card = new ListingCardView(this);
            card.bind(listing);
            card.setOnClickListener(
                    v -> refreshLauncher.launch(
                            ListingDetailActivity.intentFor(this, listing.getId())));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = margin;
            feedList.addView(card, params);
        }
        if (shown.isEmpty()) {
            emptyState.setText("No listings nearby yet — try another filter.");
            emptyState.setVisibility(TextView.VISIBLE);
        } else {
            emptyState.setVisibility(TextView.GONE);
        }
    }

    private void loadProfile() {
        ApiProvider.get().getMe(new GardenSwapApi.Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (profile == null) {
                    return;
                }
                String name = profile.getDisplayName();
                String zip = profile.getHomeZip();
                StringBuilder line = new StringBuilder("Find fresh swaps near you");
                if (name != null && !name.isEmpty()) {
                    line.insert(0, name + " · ");
                }
                if (zip != null && !zip.isEmpty()) {
                    line.append(" · near ").append(zip);
                }
                heroSubline.setText(line.toString());
            }

            @Override
            public void onError(ApiException e) {
                // Keep the default subline.
            }
        });
    }

    private void loadWallet() {
        ApiProvider.get().getWallet(new GardenSwapApi.Callback<Wallet>() {
            @Override
            public void onSuccess(Wallet wallet) {
                if (wallet == null) {
                    return;
                }
                int balance = wallet.getBalance();
                walletLine.setText(balance == 1 ? "1 credit" : balance + " credits");
            }

            @Override
            public void onError(ApiException e) {
                // Keep the placeholder; the wallet screen retries.
            }
        });
    }

    private void loadWantMatches() {
        ApiProvider.get().getMatches(new GardenSwapApi.Callback<List<Listing>>() {
            @Override
            public void onSuccess(List<Listing> matches) {
                int count = matches == null ? 0 : matches.size();
                if (count == 0) {
                    wantLine.setText("No matches yet — add plants to your want list.");
                } else {
                    wantLine.setText(count == 1
                            ? "1 listing matches your want list."
                            : count + " listings match your want list.");
                }
            }

            @Override
            public void onError(ApiException e) {
                wantLine.setText("Couldn't load matches — tap to view your want list.");
            }
        });
    }

    /**
     * Manual + automatic refresh: reloads the feed and the wallet balance
     * (claims move credits). Called from the refresh button and when a
     * create/detail flow returns RESULT_OK.
     */
    private void refresh() {
        loadFeed();
        loadWallet();
    }

    private void loadFeed() {
        String way = ExploreLogic.wayForFilter(chipRow.getSelectedFilter());
        ApiProvider.get().getFeed(new FeedRequest(way, 50),
                new GardenSwapApi.Callback<List<Listing>>() {
                    @Override
                    public void onSuccess(List<Listing> feed) {
                        allListings.clear();
                        if (feed != null) {
                            allListings.addAll(
                                    ExploreLogic.sortByFreshness(feed));
                        }
                        emptyState.setOnClickListener(null);
                        emptyState.setClickable(false);
                        applyFilter(chipRow.getSelectedFilter());
                    }

                    @Override
                    public void onError(ApiException e) {
                        allListings.clear();
                        feedList.removeAllViews();
                        emptyState.setText(
                                "Couldn't load listings — tap to retry.");
                        emptyState.setVisibility(TextView.VISIBLE);
                        emptyState.setClickable(true);
                        emptyState.setOnClickListener(v -> loadFeed());
                    }
                });
    }
}
