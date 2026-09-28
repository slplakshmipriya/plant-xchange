package com.gardenswap.app.trees;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.TreeListing;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.RipeWindow;

/**
 * Pick-your-own tree detail (AND-050).
 *
 * <p>Shows variety, ripe countdown, spray disclosure (prominent, PRD §6),
 * per-picker limit, and visit rules. The exact pickup address is gated:
 * {@link TreeListing#isAddressUnlocked()} is false until an exchange is
 * confirmed, so we show a contact prompt instead of an address (SEC-010).
 * The ripe-alert toggle subscribes the user to window-open alerts (API-050).
 */
public class TreeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TREE_ID = "tree_id";

    private TextView statusText;
    private LinearLayout content;
    private Button alertButton;
    private TreeListing tree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Pick your own");
        title.setTextSize(20);
        statusText = Ui.status(this);
        content = Ui.column(this, 0);

        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(statusText);
        Ui.gap(root, this, 8);
        root.addView(content);
        setContentView(root);

        String treeId = getIntent().getStringExtra(EXTRA_TREE_ID);
        if (treeId == null) {
            treeId = "mock-tree-1";
        }
        load(treeId);
    }

    private void load(String treeId) {
        statusText.setText("Loading tree…");
        ApiProvider.get().getTreeDetail(treeId, new GardenSwapApi.Callback<TreeListing>() {
            @Override
            public void onSuccess(TreeListing result) {
                tree = result;
                render();
            }

            @Override
            public void onError(ApiException e) {
                statusText.setText("Couldn't load this tree (" + e.getCode() + ").");
            }
        });
    }

    private void render() {
        statusText.setText("");
        content.removeAllViews();

        TextView variety = Ui.label(this, tree.getVariety());
        variety.setTextSize(18);
        content.addView(variety);
        Ui.gap(content, this, 4);

        long now = System.currentTimeMillis();
        TextView countdown = Ui.label(this, RipeWindow.countdownText(
                tree.getRipeStartMs(), tree.getRipeEndMs(), now));
        content.addView(countdown);
        Ui.gap(content, this, 12);

        // Spray disclosure is mandatory and prominent (PRD §6: no silent pesticide use).
        TextView sprayTitle = Ui.label(this, "Spray disclosure");
        sprayTitle.setTextSize(14);
        content.addView(sprayTitle);
        content.addView(Ui.label(this, tree.getSprayDisclosure()));
        Ui.gap(content, this, 12);

        content.addView(Ui.label(this, "Per-picker limit: " + tree.getPerPickerLimit()));
        Ui.gap(content, this, 8);
        content.addView(Ui.label(this, "Visit rules"));
        content.addView(Ui.label(this, tree.getPickupRules()));
        Ui.gap(content, this, 12);

        // Owner contact gating: no exact address until an exchange is confirmed.
        if (tree.isAddressUnlocked()) {
            content.addView(Ui.label(this, "Pickup address unlocked — see your confirmed exchange."));
        } else {
            content.addView(Ui.label(this,
                    "The exact pickup address stays hidden until your visit is confirmed. "
                            + "Message " + tree.getOwnerDisplayName() + " to arrange a time."));
            Button contact = Ui.button(this, "Message " + tree.getOwnerDisplayName());
            contact.setOnClickListener(v -> statusText.setText(
                    "Chat opens after you claim a slot (Wave 3 mock)."));
            content.addView(contact);
        }
        Ui.gap(content, this, 12);

        alertButton = Ui.button(this, alertLabel());
        alertButton.setOnClickListener(v -> toggleAlert());
        content.addView(alertButton);

        if (!RipeWindow.isRipe(tree.getRipeStartMs(), tree.getRipeEndMs(), now)) {
            TextView gate = Ui.label(this,
                    "First 3 visits: the owner must be present (per design board).");
            content.addView(gate);
        }
    }

    private String alertLabel() {
        return tree.isRipeAlertsSubscribed()
                ? "Ripe alerts: ON — tap to mute"
                : "Notify me when ripe";
    }

    private void toggleAlert() {
        final boolean target = !tree.isRipeAlertsSubscribed();
        alertButton.setEnabled(false);
        ApiProvider.get().setRipeAlert(tree.getTreeId(), target,
                new GardenSwapApi.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean subscribed) {
                        tree = tree.toBuilder().ripeAlertsSubscribed(subscribed).build();
                        alertButton.setText(alertLabel());
                        alertButton.setEnabled(true);
                        // Client-side toggle only; the server emits harvest_alert_sent
                        // on fan-out (ANL taxonomy). No client event forced here.
                    }

                    @Override
                    public void onError(ApiException e) {
                        statusText.setText("Couldn't update alerts (" + e.getCode() + ").");
                        alertButton.setEnabled(true);
                    }
                });
    }
}
