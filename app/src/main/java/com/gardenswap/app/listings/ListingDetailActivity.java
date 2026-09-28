package com.gardenswap.app.listings;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.Listing;
import com.gardenswap.app.api.ListingStatus;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.ListingDetailLogic;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Listing detail + claim flow (AND-021).
 *
 * <p>State-driven: the action row is derived from {@link ListingStatus} via
 * {@link ListingDetailLogic} — claim when live and not the owner, cancel/edit
 * for the owner while editable, terminal states render read-only. Illegal
 * transitions are rejected server-side (422); the client never offers them.
 */
public class ListingDetailActivity extends AppCompatActivity {

    public static final String EXTRA_LISTING_ID = "listing_id";

    private String viewerUid;
    private Listing listing;
    private LinearLayout root;
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

        root = Ui.column(this, 20);
        statusView = Ui.status(this);
        root.addView(statusView);
        setContentView(root);
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
        root.removeAllViews();

        TextView title = Ui.label(this, listing.getVariety() == null ? "Listing" : listing.getVariety());
        title.setTextSize(20);
        root.addView(title);

        TextView statusChip = Ui.label(this, ListingDetailLogic.statusLabel(listing.getStatus()));
        statusChip.setTextSize(13);
        root.addView(statusChip);
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, listing.getPhotos().size() + " photo(s)"));
        Ui.gap(root, this, 8);

        addRow("Type", capitalize(listing.getType().name()));
        if (listing.isFree()) {
            TextView free = Ui.label(this, "FREE — no credits needed");
            free.setTextSize(16);
            root.addView(free);
        } else {
            addRow("Credit cost", listing.getCreditCost()
                    + (listing.getCreditCost() == 1 ? " credit" : " credits"));
        }
        if (listing.getQuantity() != null) {
            String quantity = String.valueOf(listing.getQuantity());
            if (listing.getUnit() != null) {
                quantity += " " + listing.getUnit();
            }
            addRow("Quantity", quantity);
        }
        addRow("Freshness", ListingDetailLogic.formatCountdown(
                listing.getExpiresAtMs(), System.currentTimeMillis()));
        Ui.gap(root, this, 8);

        root.addView(Ui.label(this, "Spray disclosure"));
        TextView spray = Ui.label(this, listing.getSprayDisclosure() == null
                ? "—" : listing.getSprayDisclosure());
        root.addView(spray);
        Ui.gap(root, this, 8);

        // SEC-010: only fuzzed coordinates ever reach the client.
        root.addView(Ui.label(this, "Pickup: approximate location (±0.5 mi)"));
        Ui.gap(root, this, 12);

        if (ListingDetailLogic.canClaim(listing.getStatus(), listing.getOwnerUid(), viewerUid)) {
            Button claimButton = Ui.button(this, "Claim this listing");
            claimButton.setOnClickListener(v -> confirmClaim());
            root.addView(claimButton);
            Ui.gap(root, this, 8);
        }
        if (ListingDetailLogic.canCancel(listing.getStatus(), listing.getOwnerUid(), viewerUid)) {
            Button cancelButton = Ui.button(this, "Cancel listing");
            cancelButton.setOnClickListener(v -> confirmCancel());
            root.addView(cancelButton);
            Ui.gap(root, this, 8);
        }
        if (isOwnHarvestListing()) {
            Button logButton = Ui.button(this, "Harvest log");
            logButton.setOnClickListener(v ->
                    com.gardenswap.app.harvest.HarvestLogActivity.open(this, listing.getId()));
            root.addView(logButton);
            Ui.gap(root, this, 8);
        }
        if (listing.getStatus() != null && listing.getStatus().isTerminal()) {
            TextView terminal = Ui.label(this,
                    "This listing is " + ListingDetailLogic.statusLabel(listing.getStatus()).toLowerCase()
                            + " and read-only.");
            terminal.setGravity(Gravity.CENTER);
            root.addView(terminal);
        }

        setContentView(root);
    }

    private void addRow(String label, String value) {
        TextView view = Ui.label(this, label + ": " + value);
        view.setTextSize(14);
        root.addView(view);
    }

    private void confirmClaim() {
        // The backend claims whole listings (no partial-claim endpoint);
        // harvest picks are tracked via the harvest log after claiming.
        new AlertDialog.Builder(this)
                .setTitle("Claim this listing?")
                .setMessage("The giver will be notified. " + creditText()
                        + " moves when you both confirm the exchange.")
                .setPositiveButton("Claim", (dialog, which) -> ApiProvider.get()
                        .claimListing(listing.getId(), claimCallback("Claimed")))
                .setNegativeButton("Not now", null)
                .show();
    }

    private GardenSwapApi.Callback<Listing> claimCallback(final String doneMessage) {
        return new GardenSwapApi.Callback<Listing>() {
            @Override
            public void onSuccess(Listing result) {
                listing = result;
                Toast.makeText(ListingDetailActivity.this,
                        doneMessage + " \u2014 chat opens here", Toast.LENGTH_SHORT).show();
                render();
            }

            @Override
            public void onError(ApiException e) {
                Toast.makeText(ListingDetailActivity.this,
                        e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
    }

    private String creditText() {
        if (listing.isFree()) {
            return "No credits";
        }
        return listing.getCreditCost() + (listing.getCreditCost() == 1 ? " credit" : " credits");
    }

    private boolean isOwnHarvestListing() {
        return listing.getType() == com.gardenswap.app.api.ListingType.HARVEST
                && viewerUid != null
                && viewerUid.equals(listing.getOwnerUid());
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
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

    private static String capitalize(String value) {
        String lower = value.toLowerCase(java.util.Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Convenience launcher. */
    public static void open(android.content.Context context, String listingId) {
        Intent intent = new Intent(context, ListingDetailActivity.class);
        intent.putExtra(EXTRA_LISTING_ID, listingId);
        context.startActivity(intent);
    }
}
