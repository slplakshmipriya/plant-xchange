package com.gardenswap.test.profile;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.Swap;
import com.gardenswap.test.api.UserProfile;
import com.gardenswap.test.api.Wallet;
import com.gardenswap.test.notifications.NotificationPrefsActivity;
import com.gardenswap.test.ui.BadgeState;
import com.gardenswap.test.ui.Nav;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.ui.VerifiedBadgeView;
import com.gardenswap.test.util.IdvStatusMapper;
import com.gardenswap.test.util.ImageLoader;
import com.gardenswap.test.util.NavRouter;
import com.gardenswap.test.util.SwapLogic;

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
    private TextView avatarInitialView;
    private ImageView avatarPhotoView;
    private TextView locationView;
    private VerifiedBadgeView badgeView;
    private TextView creditsView;
    private TextView activeView;
    private TextView completedView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sticky brand bar, same as Explore; the screen title scrolls below.
        LinearLayout header = Ui.column(this, 24);
        header.addView(Ui.appTitleRow(this));
        int pad = Ui.dp(this, 24);
        header.setPadding(pad, pad, pad, 0);

        LinearLayout root = Ui.column(this, 24);
        root.setPadding(pad, 0, pad, pad);
        root.addView(Ui.headline(this, "Profile"));
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
        Ui.gap(root, this, 8);

        TextView prefsButton = Ui.secondaryButton(this, "Notification preferences");
        prefsButton.setOnClickListener(v -> startActivity(
                new Intent(this, NotificationPrefsActivity.class)));
        root.addView(prefsButton);

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

        // Avatar: initial-letter disc, with the real profile photo layered on
        // top when the user has one.
        FrameLayout avatarFrame = new FrameLayout(this);
        int size = Ui.dp(this, 64);
        avatarFrame.setLayoutParams(
                new LinearLayout.LayoutParams(size, size));

        avatarInitialView = new TextView(this);
        TextView avatar = avatarInitialView;
        avatar.setLayoutParams(new FrameLayout.LayoutParams(size, size));
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
        avatarFrame.addView(avatar);

        avatarPhotoView = new ImageView(this);
        avatarPhotoView.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        avatarPhotoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarPhotoView.setVisibility(View.GONE);
        avatarFrame.addView(avatarPhotoView);
        row.addView(avatarFrame);

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
        ApiProvider.get().getSwaps(new GardenSwapApi.Callback<java.util.List<Swap>>() {
            @Override
            public void onSuccess(java.util.List<Swap> swaps) {
                SwapLogic.Partition partition = SwapLogic.partition(swaps);
                activeView.setText(String.valueOf(partition.active().size()));
                completedView.setText(String.valueOf(partition.completed().size()));
            }

            @Override
            public void onError(ApiException e) {
                activeView.setText("–");
                completedView.setText("–");
            }
        });

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
        avatarInitialView.setText(name.substring(0, 1).toUpperCase());
        // Real profile photo when the user uploaded one; else the initial disc.
        String avatarUrl = profile.getAvatarUrl();
        boolean hasAvatar = avatarUrl != null && !avatarUrl.trim().isEmpty();
        avatarPhotoView.setVisibility(hasAvatar ? View.VISIBLE : View.GONE);
        if (hasAvatar) {
            ImageLoader.loadCircularInto(avatarPhotoView, avatarUrl);
        }

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
