package com.gardenswap.app.profile;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.app.R;
import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.UserProfile;
import com.gardenswap.app.api.Wallet;
import com.gardenswap.app.ui.BadgeState;
import com.gardenswap.app.ui.Nav;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.ui.VerifiedBadgeView;
import com.gardenswap.app.util.IdvStatusMapper;
import com.gardenswap.app.util.NavRouter;
import com.gardenswap.app.util.SwapLogic;

/**
 * Profile screen (UID-023).
 *
 * <p>Header (avatar placeholder, name, verification badge, home ZIP),
 * stats row (credits, active swaps, completed swaps), and a link to
 * My swaps. Profile and wallet come from the API; swap counts come from
 * {@link SwapSamples} (placeholder) until the swap-history API lands.
 */
public class ProfileActivity extends AppCompatActivity {

    private TextView nameView;
    private TextView avatarView;
    private TextView locationView;
    private VerifiedBadgeView badgeView;
    private TextView creditsView;
    private TextView activeView;
    private TextView completedView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sticky title bar: "Profile" stays fixed while cards scroll.
        LinearLayout header = Ui.column(this, 24);
        header.addView(Ui.headline(this, "Profile"));
        int pad = Ui.dp(this, 24);
        header.setPadding(pad, pad, pad, 0);

        LinearLayout root = Ui.column(this, 24);
        root.setPadding(pad, 0, pad, pad);
        Ui.gap(root, this, 16);

        statusView = Ui.status(this);
        root.addView(statusView);
        Ui.gap(root, this, 8);

        root.addView(headerCard());
        Ui.gap(root, this, 16);
        root.addView(statsCard());
        Ui.gap(root, this, 16);

        TextView swapsButton = Ui.secondaryButton(this, "View my swaps");
        swapsButton.setOnClickListener(v -> startActivity(
                new Intent(this, MySwapsActivity.class)));
        root.addView(swapsButton);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(Ui.stickyHeaderScreen(this, header, scroll));
        Nav.attach(this, NavRouter.Tab.PROFILE);

        load();
    }

    /** 023-T1: avatar placeholder, name, badge, ZIP/location line. */
    private LinearLayout headerCard() {
        LinearLayout card = Ui.card(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        avatarView = new TextView(this);
        TextView avatar = avatarView;        int size = Ui.dp(this, 64);
        avatar.setLayoutParams(
                new LinearLayout.LayoutParams(size, size));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ResourcesCompat.getColor(getResources(),
                R.color.garden_acid, getTheme()));
        avatar.setBackground(bg);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextSize(24);
        avatar.setTypeface(ResourcesCompat.getFont(this, R.font.dm_sans),
                Typeface.BOLD);
        avatar.setTextColor(ResourcesCompat.getColor(getResources(),
                R.color.garden_ink, getTheme()));
        avatar.setText("?");
        row.addView(avatar);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int margin = Ui.dp(this, 16);
        textParams.setMarginStart(margin);
        text.setLayoutParams(textParams);

        nameView = Ui.title(this, "Gardener");
        text.addView(nameView);
        badgeView = new VerifiedBadgeView(this);
        text.addView(badgeView);
        locationView = Ui.caption(this, "ZIP not set");
        text.addView(locationView);
        row.addView(text);

        card.addView(row);
        return card;
    }

    /** 023-T2: credits balance, active count, completed count. */
    private LinearLayout statsCard() {
        LinearLayout card = Ui.card(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        creditsView = statCell(row, "Credits");
        activeView = statCell(row, "Active swaps");
        completedView = statCell(row, "Completed");

        card.addView(row);
        return card;
    }

    private TextView statCell(LinearLayout row, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = Ui.headline(this, "–");
        value.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView caption = Ui.caption(this, label);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.addView(value);
        cell.addView(caption);
        row.addView(cell);
        return value;
    }

    private void load() {
        statusView.setText("Loading profile…");
        SwapLogic.Partition partition =
                SwapLogic.partition(SwapSamples.swaps());
        activeView.setText(String.valueOf(partition.active().size()));
        completedView.setText(String.valueOf(partition.completed().size()));

        ApiProvider.get().getMe(new GardenSwapApi.Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                renderProfile(profile);
            }

            @Override
            public void onError(ApiException e) {
                statusView.setText(
                        "Couldn't load your profile (" + e.getCode() + ").");
            }
        });
        ApiProvider.get().getWallet(new GardenSwapApi.Callback<Wallet>() {
            @Override
            public void onSuccess(Wallet wallet) {
                statusView.setText("");
                creditsView.setText(String.valueOf(wallet.getBalance()));
            }

            @Override
            public void onError(ApiException e) {
                statusView.setText(
                        "Couldn't load your wallet (" + e.getCode() + ").");
            }
        });
    }

    private void renderProfile(UserProfile profile) {
        statusView.setText("");
        String name = profile.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            name = "Gardener";
        }
        nameView.setText(name);
        avatarView.setText(name.substring(0, 1).toUpperCase());

        String zip = profile.getHomeZip();
        locationView.setText(zip == null || zip.trim().isEmpty()
                ? "ZIP not set" : "Home ZIP " + zip.trim());

        BadgeState state = IdvStatusMapper.map(profile.getIdvStatus());
        if (state == BadgeState.UNVERIFIED && profile.isPhoneVerified()) {
            state = BadgeState.PHONE_VERIFIED;
        }
        badgeView.setState(state);
    }
}
