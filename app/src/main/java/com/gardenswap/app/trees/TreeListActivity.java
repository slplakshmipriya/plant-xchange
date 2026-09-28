package com.gardenswap.app.trees;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.TreeListing;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.RipeWindow;

import java.util.List;

/**
 * Pick-your-own tree list (AND-149).
 *
 * <p>Lists trees open for picking. Tapping a row opens
 * {@link TreeDetailActivity} for that tree. SEC-010: the owner's exact
 * address is never on the wire, so each row shows the address-gating state
 * instead of a location.
 */
public class TreeListActivity extends AppCompatActivity {

    private TextView statusText;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout content = Ui.column(this, 16);
        content.addView(Ui.headline(this, "Pick your own"));
        Ui.gap(content, this, 4);
        content.addView(Ui.caption(this, "Trees near you that are open for picking."));
        Ui.gap(content, this, 8);
        statusText = Ui.status(this);
        content.addView(statusText);
        Ui.gap(content, this, 8);
        list = Ui.column(this, 0);
        content.addView(list);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(Ui.stickyHeaderScreen(this, Ui.appTitleRow(this), scroll));

        load();
    }

    private void load() {
        statusText.setText("Loading trees…");
        ApiProvider.get().listTrees(new GardenSwapApi.Callback<List<TreeListing>>() {
            @Override
            public void onSuccess(List<TreeListing> trees) {
                statusText.setText("");
                render(trees);
            }

            @Override
            public void onError(ApiException e) {
                statusText.setText("Couldn't load trees (" + e.getCode() + ").");
            }
        });
    }

    private void render(List<TreeListing> trees) {
        list.removeAllViews();
        if (trees == null || trees.isEmpty()) {
            list.addView(Ui.body(this,
                    "No pick-your-own trees yet. Check back when growers add theirs."));
            return;
        }
        for (TreeListing tree : trees) {
            list.addView(row(tree));
            Ui.gap(list, this, 12);
        }
    }

    private LinearLayout row(TreeListing tree) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.headline(this, tree.getVariety()));
        Ui.gap(card, this, 4);
        // No approximate location field on the wire — show the address-gating
        // state instead (SEC-010).
        card.addView(Ui.caption(this, tree.isAddressUnlocked()
                ? "Pickup location unlocked for your confirmed visit."
                : "Exact location hidden until your visit is confirmed."));
        Ui.gap(card, this, 4);
        card.addView(Ui.caption(this, RipeWindow.countdownText(
                tree.getRipeStartMs(), tree.getRipeEndMs(), System.currentTimeMillis())));
        card.setClickable(true);
        card.setFocusable(true);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                ripple, true);
        card.setForeground(ResourcesCompat.getDrawable(
                getResources(), ripple.resourceId, getTheme()));
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, TreeDetailActivity.class);
            intent.putExtra(TreeDetailActivity.EXTRA_TREE_ID, tree.getTreeId());
            startActivity(intent);
        });
        return card;
    }
}
