package com.gardenswap.test.trees;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.ChatThread;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.Slot;
import com.gardenswap.test.api.TreeListing;
import com.gardenswap.test.chat.ChatActivity;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.RipeWindow;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

    // Visit-confirm section state: the slot the section targets (the
    // caller's earliest unconfirmed claim — see chooseVisitTarget), the
    // caller's claimed slots so the target can be re-picked as claims and
    // confirmations land, the section's widgets so async slot loads can
    // refresh them, and an in-flight flag so a refresh cannot re-enable
    // the Confirm button mid-request.
    private Slot mySlot;
    private final List<Slot> myClaimedSlots = new ArrayList<>();
    private boolean confirmInFlight;
    private TextView visitStateText;
    private EditText visitLbsInput;
    private Button visitConfirmButton;
    private String viewerUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        viewerUid = user == null ? null : user.getUid();

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
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

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
            // The owner messaging themselves is pointless (the thread
            // would show the other party as "Neighbor") — mirror
            // ListingDetailActivity, which only offers chat to the other
            // party.
            if (!isOwnTree()) {
                Button contact = Ui.button(this, "Message " + tree.getOwnerDisplayName());
                contact.setOnClickListener(v -> openChat());
                content.addView(contact);
            }
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
     * Opens (or creates) the chat thread with this tree's owner so the
     * visitor can arrange a time — before any claim; the thread endpoint is
     * idempotent per listing. Threads key off the listing id, and a tree's
     * id IS its listing id (the same id the slot endpoints use), so this
     * mirrors {@code ListingDetailActivity.openChat()} unchanged.
     */
    private void openChat() {
        ApiProvider.get().openThread(treeId,
                new GardenSwapApi.Callback<ChatThread>() {
                    @Override
                    public void onSuccess(ChatThread thread) {
                        Intent intent = new Intent(TreeDetailActivity.this,
                                ChatActivity.class);
                        intent.putExtra(ChatActivity.EXTRA_THREAD_ID,
                                thread.getThreadId());
                        intent.putExtra(ChatActivity.EXTRA_OTHER_NAME,
                                thread.getOtherPartyName());
                        intent.putExtra(ChatActivity.EXTRA_OTHER_AVATAR,
                                thread.getOtherAvatarUrl());
                        intent.putExtra(ChatActivity.EXTRA_PARTICIPANT_ID,
                                thread.getParticipantUserId());
                        intent.putExtra(ChatActivity.EXTRA_CONTEXT, contextLabel());
                        startActivity(intent);
                    }

                    @Override
                    public void onError(ApiException e) {
                        Toast.makeText(TreeDetailActivity.this,
                                "Couldn't open chat.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String contextLabel() {
        String variety = tree == null || tree.getVariety() == null
                ? "Pick-your-own" : tree.getVariety();
        return variety + " · Pick-your-own visit";
    }

    /**
     * Post-visit confirm (AND-050): an lbs-taken input plus a Confirm button,
     * wired to {@link GardenSwapApi#confirmTreeVisit}
     * ({@code POST /v1/trees/{id}/slots/{slotId}/confirm-visit}). Only the
     * claimer of a slot may confirm, so the section follows the caller's
     * claim state (see {@link #refreshVisitSection()}): with no claim held,
     * the button explains that a slot must be claimed first and never calls
     * the API; with a claim held it posts the entered lbs; once confirmed,
     * it shows the recorded amount with the input prefilled, and confirming
     * again updates the amount (idempotent on the backend). No credits move
     * here — they settled at claim time.
     */
    private void renderVisitSection() {
        TextView title = Ui.label(this, "Confirm visit");
        title.setTextSize(14);
        content.addView(title);
        Ui.gap(content, this, 4);
        visitStateText = Ui.caption(this, "Log what you picked after your visit.");
        content.addView(visitStateText);
        Ui.gap(content, this, 4);

        visitLbsInput = Ui.input(this, "Pounds picked",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        content.addView(visitLbsInput);
        Ui.gap(content, this, 8);

        visitConfirmButton = Ui.button(this, "Confirm visit");
        visitConfirmButton.setOnClickListener(v -> onConfirmVisit());
        content.addView(visitConfirmButton);
        Ui.gap(content, this, 12);
        refreshVisitSection();
    }

    private void onConfirmVisit() {
        if (mySlot == null) {
            Toast.makeText(this,
                    "Claim a slot first — you can log your pick after the visit.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String raw = visitLbsInput.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "Enter pounds picked first", Toast.LENGTH_SHORT).show();
            return;
        }
        final double lbs;
        try {
            lbs = Double.parseDouble(raw);
        } catch (NumberFormatException nfe) {
            Toast.makeText(this, "Enter a number for pounds picked",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // Mirror the server/mock rule (finite, > 0, <= 1000) locally: it
        // fails fast with a friendly message, and "NaN" never reaches
        // JSONObject encoding, where it would die as an encode error.
        if (!Double.isFinite(lbs) || lbs <= 0 || lbs > 1000) {
            Toast.makeText(this, "Enter pounds picked (up to 1000)",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        confirmInFlight = true;
        visitConfirmButton.setEnabled(false);
        ApiProvider.get().confirmTreeVisit(treeId, mySlot.getId(), lbs,
                new GardenSwapApi.Callback<Slot>() {
                    @Override
                    public void onSuccess(Slot updated) {
                        confirmInFlight = false;
                        mySlot = updated;
                        upsertClaim(updated);
                        refreshVisitSection();
                        Toast.makeText(TreeDetailActivity.this,
                                "Visit confirmed — " + formatLbs(lbs) + " lbs logged",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(ApiException e) {
                        confirmInFlight = false;
                        visitConfirmButton.setEnabled(true);
                        Toast.makeText(TreeDetailActivity.this,
                                friendlyMessage(e), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Keeps the visit section in step with the caller's claim state: no
     * claim yet (log after claiming), claim held (ready to log), or visit
     * confirmed (recorded amount shown, input prefilled for an update).
     * Safe to call before the section is rendered (widgets still null).
     */
    private void refreshVisitSection() {
        if (visitStateText == null) {
            return;
        }
        // Never re-enable Confirm while a confirm request is in flight —
        // the server call is idempotent, but duplicate POSTs are noise.
        if (mySlot == null) {
            visitStateText.setText("Log what you picked after your visit.");
            visitConfirmButton.setText("Confirm visit");
            visitConfirmButton.setEnabled(!confirmInFlight);
        } else if (mySlot.isVisitConfirmed()) {
            Double lbs = mySlot.getLbsPicked();
            visitStateText.setText("Slot " + slotDateTime(mySlot) + " · "
                    + (lbs == null
                            ? "Visit confirmed."
                            : "Visit confirmed · " + formatLbs(lbs) + " lbs picked"));
            visitConfirmButton.setText("Update amount");
            visitConfirmButton.setEnabled(!confirmInFlight);
            if (lbs != null && visitLbsInput.getText().toString().trim().isEmpty()) {
                visitLbsInput.setText(formatLbs(lbs));
            }
        } else {
            visitStateText.setText("Slot " + slotDateTime(mySlot)
                    + " — log what you picked after your visit.");
            visitConfirmButton.setText("Confirm visit");
            visitConfirmButton.setEnabled(!confirmInFlight);
        }
    }

    /** Whole amounts read "5"; fractional ones read "5.5". */
    private static String formatLbs(double lbs) {
        if (lbs == Math.floor(lbs) && !Double.isInfinite(lbs)) {
            return String.valueOf((long) lbs);
        }
        return String.format(Locale.US, "%.1f", lbs);
    }

    /**
     * Human-readable error text for toasts: the server's plain-language
     * message when it sent one, never a raw error code (same pattern as
     * ChatActivity's send errors).
     */
    private static String friendlyMessage(ApiException e) {
        String serverMessage = e.getMessage();
        if (serverMessage != null && !serverMessage.trim().isEmpty()
                && !serverMessage.equals(e.getCode())) {
            return serverMessage;
        }
        return "Please try again.";
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
                // The slot list annotates the caller's own claims; collect
                // them so the visit section can target the right one (with
                // several claims on one tree, logging against the wrong
                // slot confirms a visit that never happened there).
                myClaimedSlots.clear();
                if (result != null) {
                    for (Slot slot : result) {
                        if (slot.isClaimedByMe()) {
                            myClaimedSlots.add(slot);
                        }
                    }
                }
                mySlot = chooseVisitTarget(myClaimedSlots);
                refreshVisitSection();
            }

            @Override
            public void onError(ApiException e) {
                slotsSection.removeAllViews();
                slotsSection.addView(Ui.caption(TreeDetailActivity.this,
                        "Slots not available yet"));
            }
        });
    }

    /**
     * Picks the slot the visit section should act on: the caller's
     * earliest unconfirmed claim (the visit still waiting to be logged),
     * or the earliest claim overall when all are confirmed (so its
     * recorded amount can be reviewed/updated). Null when the caller
     * holds no claim. Slot order is by scheduled day, then start time.
     */
    private static Slot chooseVisitTarget(List<Slot> claimed) {
        Slot earliest = null;
        Slot earliestUnconfirmed = null;
        for (Slot slot : claimed) {
            if (earliest == null || comesBefore(slot, earliest)) {
                earliest = slot;
            }
            if (!slot.isVisitConfirmed()
                    && (earliestUnconfirmed == null
                        || comesBefore(slot, earliestUnconfirmed))) {
                earliestUnconfirmed = slot;
            }
        }
        return earliestUnconfirmed != null ? earliestUnconfirmed : earliest;
    }

    private static boolean comesBefore(Slot a, Slot b) {
        if (a.getDayMs() != b.getDayMs()) {
            return a.getDayMs() < b.getDayMs();
        }
        return a.getStartMs() < b.getStartMs();
    }

    /** Adds a claimed slot (or replaces the same-id entry) in the tracked set. */
    private void upsertClaim(Slot slot) {
        for (int i = 0; i < myClaimedSlots.size(); i++) {
            if (myClaimedSlots.get(i).getId().equals(slot.getId())) {
                myClaimedSlots.set(i, slot);
                return;
            }
        }
        myClaimedSlots.add(slot);
    }

    /** True when the signed-in user owns this tree (uid from the listing wire). */
    private boolean isOwnTree() {
        return viewerUid != null && tree != null
                && viewerUid.equals(tree.getOwnerUid());
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
                            // Claim responses carry no caller annotation; the
                            // claim is the caller's by definition. Track it
                            // among the caller's claims and re-pick the
                            // visit target (earliest unconfirmed wins).
                            upsertClaim(updated.isClaimedByMe()
                                    ? updated : updated.withClaimedByMe());
                            mySlot = chooseVisitTarget(myClaimedSlots);
                            refreshVisitSection();
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
