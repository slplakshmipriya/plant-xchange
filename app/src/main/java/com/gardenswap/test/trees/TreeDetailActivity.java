package com.gardenswap.test.trees;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.Slot;
import com.gardenswap.test.api.TreeListing;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.RipeWindow;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

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
    private LinearLayout slotsSection;
    private Button alertButton;
    private TreeListing tree;
    private String treeId;

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
        Ui.gap(root, this, 12);
        slotsSection = Ui.column(this, 0);
        root.addView(slotsSection);
        setContentView(root);

        treeId = getIntent().getStringExtra(EXTRA_TREE_ID);
        if (treeId == null) {
            treeId = "mock-tree-1";
        }
        load(treeId);
        loadSlots(treeId);
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

        renderSprayDisclosure();

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
        Ui.gap(content, this, 12);
        renderVisitSection();
    }

    /**
     * Post-visit confirm (AND-050): an lbs-taken input plus a Confirm button.
     * No visit-confirm endpoint exists in {@link GardenSwapApi} yet — API-060's
     * {@code confirmExchange} is two-sided credit-exchange confirmation and its
     * semantics do not fit a tree visit — so the button reports the backend gap
     * instead of inventing an API call.
     */
    private void renderVisitSection() {
        TextView title = Ui.label(this, "Confirm visit");
        title.setTextSize(14);
        content.addView(title);
        Ui.gap(content, this, 4);
        content.addView(Ui.caption(this, "Log what you picked after your visit."));
        Ui.gap(content, this, 4);

        final EditText lbsInput = Ui.input(this, "Pounds picked",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        content.addView(lbsInput);
        Ui.gap(content, this, 8);

        Button confirm = Ui.button(this, "Confirm visit");
        confirm.setOnClickListener(v -> {
            String raw = lbsInput.getText().toString().trim();
            if (raw.isEmpty()) {
                Toast.makeText(this, "Enter pounds picked first", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                Double.parseDouble(raw);
            } catch (NumberFormatException nfe) {
                Toast.makeText(this, "Enter a number for pounds picked", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this,
                    "Visit confirmation needs the backend visit API",
                    Toast.LENGTH_SHORT).show();
        });
        content.addView(confirm);
        Ui.gap(content, this, 12);
    }

    /**
     * Pick-your-own slots (r2 contract: {@code GET /v1/trees/{id}/slots},
     * {@code POST /v1/trees/{treeId}/slots/{slotId}/claim}). Endpoints may 404
     * until the backend lands — the error path shows a one-line note and
     * never crashes.
     */
    private void loadSlots(String id) {
        slotsSection.removeAllViews();
        ApiProvider.get().listTreeSlots(id, new GardenSwapApi.Callback<List<Slot>>() {
            @Override
            public void onSuccess(List<Slot> result) {
                renderSlots(result);
            }

            @Override
            public void onError(ApiException e) {
                slotsSection.removeAllViews();
                slotsSection.addView(Ui.caption(TreeDetailActivity.this,
                        "Slots not available yet"));
            }
        });
    }

    private void renderSlots(List<Slot> slots) {
        slotsSection.removeAllViews();
        TextView title = Ui.label(this, "Pick-your-own slots");
        title.setTextSize(14);
        slotsSection.addView(title);
        Ui.gap(slotsSection, this, 4);
        if (slots == null || slots.isEmpty()) {
            slotsSection.addView(Ui.caption(this, "No slots posted for this tree."));
            return;
        }
        for (Slot slot : slots) {
            addSlotRow(slotsSection, slot);
            Ui.gap(slotsSection, this, 8);
        }
    }

    private void addSlotRow(LinearLayout parent, Slot slot) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView when = Ui.label(this, slotDateTime(slot));
        final TextView detail = Ui.caption(this, slotDetail(slot));
        textCol.addView(when);
        textCol.addView(detail);
        row.addView(textCol);

        final Button claim = Ui.button(this, "Claim");
        claim.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final Slot[] current = {slot};
        claim.setOnClickListener(v -> {
            claim.setEnabled(false);
            ApiProvider.get().claimTreeSlot(treeId, current[0].getId(),
                    new GardenSwapApi.Callback<Slot>() {
                        @Override
                        public void onSuccess(Slot updated) {
                            current[0] = updated;
                            refreshSlotRow(updated, when, detail, claim);
                            Toast.makeText(TreeDetailActivity.this,
                                    "Slot claimed", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(ApiException e) {
                            claim.setEnabled(current[0].getRemainingCount() > 0);
                            Toast.makeText(TreeDetailActivity.this,
                                    "Couldn't claim slot (" + e.getCode() + ").",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });
        refreshSlotRow(slot, when, detail, claim);
        row.addView(claim);
        parent.addView(row);
    }

    private void refreshSlotRow(Slot slot, TextView when, TextView detail, Button claim) {
        when.setText(slotDateTime(slot));
        detail.setText(slotDetail(slot));
        claim.setEnabled(slot.getRemainingCount() > 0);
    }

    private static String slotDateTime(Slot slot) {
        SimpleDateFormat day = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        SimpleDateFormat time = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return day.format(slot.getDayMs()) + " - "
                + time.format(slot.getStartMs()) + " - " + time.format(slot.getEndMs());
    }

    private static String slotDetail(Slot slot) {
        StringBuilder sb = new StringBuilder();
        sb.append(slot.getRemainingCount()).append(" of ")
                .append(slot.getMaxPickers()).append(" pickers left - ")
                .append(slot.getCreditCost()).append(" credit(s)");
        Integer cashCents = slot.getCashCents();
        if (cashCents != null) {
            sb.append(" - $").append(String.format(Locale.US, "%.2f", cashCents / 100.0));
        }
        return sb.toString();
    }

    /**
     * Spray disclosure is mandatory and prominent (PRD §6: no silent
     * pesticide use). The stored string is mapped onto the required
     * four-option field ({@link TreeListing#SPRAY_NONE},
     * {@link TreeListing#SPRAY_ORGANIC}, {@link TreeListing#SPRAY_SYNTHETIC},
     * {@link TreeListing#SPRAY_UNKNOWN}) rendered as chips with the matching
     * one highlighted. Blank/unknown data shows an explicit undisclosed
     * state; free-text detail beyond the four options is kept visible
     * underneath.
     */
    private void renderSprayDisclosure() {
        TextView sprayTitle = Ui.label(this, "Spray disclosure");
        sprayTitle.setTextSize(14);
        content.addView(sprayTitle);
        Ui.gap(content, this, 4);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] values = {
                TreeListing.SPRAY_NONE,
                TreeListing.SPRAY_ORGANIC,
                TreeListing.SPRAY_SYNTHETIC,
                TreeListing.SPRAY_UNKNOWN
        };
        String[] labels = {"None", "Organic", "Synthetic", "Unknown"};
        String mapped = mapSpray(tree.getSprayDisclosure());
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        Ui.dp(this, 8), ViewGroup.LayoutParams.WRAP_CONTENT));
                chips.addView(spacer);
            }
            TextView chip = Ui.chip(this, labels[i]);
            Ui.setChipSelected(this, chip, values[i].equals(mapped));
            chips.addView(chip);
        }
        content.addView(chips);
        Ui.gap(content, this, 4);

        String raw = tree.getSprayDisclosure();
        if (mapped.equals(TreeListing.SPRAY_UNKNOWN)
                && (raw == null || raw.trim().isEmpty()
                    || raw.trim().equalsIgnoreCase(TreeListing.SPRAY_UNKNOWN))) {
            content.addView(Ui.label(this, "Spray history not disclosed."));
        } else if (raw != null && !isSprayConstant(raw)) {
            content.addView(Ui.caption(this, raw.trim()));
        }
        Ui.gap(content, this, 12);
    }

    /** Maps the stored spray string onto one of the four SPRAY_* constants. */
    private static String mapSpray(String raw) {
        if (isSprayConstant(raw)) {
            return raw.trim().toLowerCase();
        }
        return TreeListing.SPRAY_UNKNOWN;
    }

    private static boolean isSprayConstant(String raw) {
        if (raw == null) {
            return false;
        }
        String v = raw.trim().toLowerCase();
        return v.equals(TreeListing.SPRAY_NONE)
                || v.equals(TreeListing.SPRAY_ORGANIC)
                || v.equals(TreeListing.SPRAY_SYNTHETIC)
                || v.equals(TreeListing.SPRAY_UNKNOWN);
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
