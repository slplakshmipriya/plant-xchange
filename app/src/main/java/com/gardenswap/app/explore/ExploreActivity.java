package com.gardenswap.app.explore;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gardenswap.app.R;
import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.Listing;
import com.gardenswap.app.api.ListingType;
import com.gardenswap.app.api.MockGardenSwapApi;
import com.gardenswap.app.api.UserProfile;
import com.gardenswap.app.api.Wallet;
import com.gardenswap.app.listings.ListingDetailActivity;
import com.gardenswap.app.sitters.SitterListActivity;
import com.gardenswap.app.ui.FilterChipRow;
import com.gardenswap.app.ui.ListingCardAdapter;
import com.gardenswap.app.ui.Nav;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.ExploreLogic;
import com.gardenswap.app.util.ListingFilter;
import com.gardenswap.app.util.NavRouter;
import com.gardenswap.app.wallet.WalletActivity;
import com.gardenswap.app.wantlist.WantListActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Explore home screen (UID-010): hero header, four "way" cards, credit
 * balance panel, want-list match panel, and the nearby-listings feed with
 * working filter chips. All data comes from the mock API (UID-010-T7);
 * every callback is null-safe.
 */
public class ExploreActivity extends AppCompatActivity {

    private final List<Listing> allListings = new ArrayList<>();
    private ListingCardAdapter listingsAdapter;
    private FilterChipRow chipRow;
    private TextView emptyState;
    private TextView heroSubline;
    private TextView walletLine;
    private TextView wantLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sticky title bar: the leaf + wordmark row never scrolls away.
        LinearLayout header = Ui.column(this, 20);
        header.addView(Ui.appTitleRow(this));
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

        root.addView(Ui.headline(this, "Nearby listings"));
        Ui.gap(root, this, 8);
        chipRow = new FilterChipRow(this);
        chipRow.setOnFilterChanged(this::applyFilter);
        root.addView(chipRow);
        Ui.gap(root, this, 8);
        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setNestedScrollingEnabled(false);
        listingsAdapter = new ListingCardAdapter(new ArrayList<>());
        listingsAdapter.setOnListingClickListener(
                listing -> ListingDetailActivity.open(this, listing.getId()));
        list.setAdapter(listingsAdapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        emptyState = Ui.caption(this, "No listings here yet — check back soon.");
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
        listingsAdapter.setListings(shown);
        emptyState.setVisibility(shown.isEmpty() ? TextView.VISIBLE : TextView.GONE);
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

    private void loadFeed() {
        GardenSwapApi api = ApiProvider.get();
        if (!(api instanceof MockGardenSwapApi)) {
            // No feed endpoint in the API contract yet; nothing to show.
            applyFilter(null);
            return;
        }
        ((MockGardenSwapApi) api).getFeed(new GardenSwapApi.Callback<List<Listing>>() {
            @Override
            public void onSuccess(List<Listing> feed) {
                allListings.clear();
                if (feed != null) {
                    allListings.addAll(feed);
                }
                applyFilter(chipRow.getSelectedFilter());
            }

            @Override
            public void onError(ApiException e) {
                applyFilter(null);
            }
        });
    }
}
