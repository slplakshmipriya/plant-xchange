package com.gardenswap.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.IdvStatus;
import com.gardenswap.app.chat.ChatActivity;
import com.gardenswap.app.chat.ThreadListActivity;
import com.gardenswap.app.harvest.HarvestLogActivity;
import com.gardenswap.app.idv.IdvActivity;
import com.gardenswap.app.listings.CreateListingActivity;
import com.gardenswap.app.listings.ListingDetailActivity;
import com.gardenswap.app.onboarding.PhoneAuthActivity;
import com.gardenswap.app.sitters.ReviewActivity;
import com.gardenswap.app.sitters.SitterListActivity;
import com.gardenswap.app.sitters.SitterProfileActivity;
import com.gardenswap.app.trees.TreeDetailActivity;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.ui.VerifiedBadgeView;
import com.gardenswap.app.util.IdvStatusMapper;
import com.gardenswap.app.wallet.ConfirmExchangeActivity;
import com.gardenswap.app.wallet.WalletActivity;
import com.gardenswap.app.wantlist.WantListActivity;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Launcher + router (AND-001/AND-010/AND-011). Unauthenticated users are sent
 * to onboarding; signed-in users get the home placeholder (the real feed
 * lands with EPIC-MVP-3) plus their verification badge and an IDV entry point.
 *
 * <p>DEBUG BRANCH ONLY: when {@link #BYPASS_AUTH} is true the login screen is
 * skipped and a debug menu opens instead, linking to every screen with mock
 * data so the app can be exercised with no sign-in. Never merge to main.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * DEBUG BRANCH ONLY — never merge to main. When true, the auth gate is
     * skipped and a debug menu replaces the home screen. For testing flows
     * past the login screen without signing in.
     */
    private static final boolean BYPASS_AUTH = true;

    private VerifiedBadgeView badgeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BYPASS_AUTH) {
            showDebugMenu();
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, PhoneAuthActivity.class));
            finish();
            return;
        }

        LinearLayout root = Ui.column(this, 24);
        TextView placeholder = Ui.label(this, "Garden Swap — coming soon");
        placeholder.setTextSize(20);
        placeholder.setGravity(Gravity.CENTER);
        badgeView = new VerifiedBadgeView(this);
        Button verifyButton = Ui.button(this, "Verify identity");
        verifyButton.setOnClickListener(
                v -> startActivity(new Intent(this, IdvActivity.class)));

        root.addView(placeholder);
        Ui.gap(root, this, 16);
        root.addView(badgeView);
        Ui.gap(root, this, 16);
        root.addView(verifyButton);
        setContentView(root);
    }

    /** DEBUG BRANCH ONLY — a menu of every screen, wired with mock IDs. */
    private void showDebugMenu() {
        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Garden Swap — debug menu");
        title.setTextSize(20);
        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(Ui.label(this, "No sign-in. All screens use mock data."));
        Ui.gap(root, this, 16);

        addMenuItem(root, "Create listing", new Intent(this, CreateListingActivity.class));
        addMenuItem(root, "Listing detail (sample)", intent(ListingDetailActivity.class)
                .putExtra(ListingDetailActivity.EXTRA_LISTING_ID, "sample-1"));
        addMenuItem(root, "Want list", new Intent(this, WantListActivity.class));
        addMenuItem(root, "Harvest log (sample)", intent(HarvestLogActivity.class)
                .putExtra(HarvestLogActivity.EXTRA_LISTING_ID, "sample-2"));
        addMenuItem(root, "Tree detail", intent(TreeDetailActivity.class)
                .putExtra(TreeDetailActivity.EXTRA_TREE_ID, "tree-1"));
        addMenuItem(root, "Wallet", new Intent(this, WalletActivity.class));
        addMenuItem(root, "Confirm exchange", intent(ConfirmExchangeActivity.class)
                .putExtra(ConfirmExchangeActivity.EXTRA_EXCHANGE_ID, "xchg-1")
                .putExtra(ConfirmExchangeActivity.EXTRA_CREDIT_COST, 2));
        addMenuItem(root, "Sitter list", new Intent(this, SitterListActivity.class));
        addMenuItem(root, "Sitter profile", intent(SitterProfileActivity.class)
                .putExtra(SitterProfileActivity.EXTRA_SITTER_ID, "s1"));
        addMenuItem(root, "Leave a review", intent(ReviewActivity.class)
                .putExtra(ReviewActivity.EXTRA_BOOKING_ID, "b1")
                .putExtra(ReviewActivity.EXTRA_BOOKING_STATUS, "COMPLETED"));
        addMenuItem(root, "Chat threads", new Intent(this, ThreadListActivity.class));
        addMenuItem(root, "Chat (thread t1)", intent(ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_THREAD_ID, "t1")
                .putExtra(ChatActivity.EXTRA_OTHER_NAME, "Priya")
                .putExtra(ChatActivity.EXTRA_CONTEXT, "Cherokee Purple tomato · 2 credits"));
        addMenuItem(root, "Verify identity (stub)", new Intent(this, IdvActivity.class));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private Intent intent(Class<?> cls) {
        return new Intent(this, cls);
    }

    private void addMenuItem(LinearLayout root, String label, Intent intent) {
        Button button = Ui.button(this, label);
        button.setOnClickListener(v -> startActivity(intent));
        root.addView(button);
        Ui.gap(root, this, 8);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (badgeView == null) {
            return;
        }
        ApiProvider.get().getIdvStatus(new GardenSwapApi.Callback<IdvStatus>() {
            @Override
            public void onSuccess(IdvStatus status) {
                badgeView.setState(IdvStatusMapper.map(status));
            }

            @Override
            public void onError(ApiException e) {
                // Keep the last-known badge state; the IDV screen retries.
            }
        });
    }
}
