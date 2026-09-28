package com.gardenswap.app.sitters;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.SitterProfile;
import com.gardenswap.app.ui.Nav;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.ui.VerifiedBadgeView;
import com.gardenswap.app.ui.BadgeState;
import com.gardenswap.app.util.NavRouter;
import com.gardenswap.app.util.ReviewGuard;

import java.util.List;

/**
 * Sitter discovery list (AND-070).
 *
 * <p>Searches sitters by the signed-in user's home zip. Each card shows the
 * rate, coverage radius, ID-verification badge, and Bayesian-smoothed
 * rating. Tapping a card opens {@link SitterProfileActivity}.
 */
public class SitterListActivity extends AppCompatActivity {

    private TextView statusText;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Find a plant sitter");
        title.setTextSize(20);
        statusText = Ui.status(this);
        list = Ui.column(this, 0);

        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(statusText);
        Ui.gap(root, this, 8);
        root.addView(list);
        setContentView(root);
        Nav.attach(this, NavRouter.Tab.CARE);

        load("85281"); // mock zip; real flow reads the profile's homeZip
    }

    private void load(String zip) {
        statusText.setText("Searching sitters near " + zip + "…");
        ApiProvider.get().getSitters(zip, new GardenSwapApi.Callback<List<SitterProfile>>() {
            @Override
            public void onSuccess(List<SitterProfile> sitters) {
                render(sitters);
            }

            @Override
            public void onError(ApiException e) {
                statusText.setText("Couldn't load sitters (" + e.getCode() + ").");
            }
        });
    }

    private void render(List<SitterProfile> sitters) {
        statusText.setText("");
        list.removeAllViews();
        if (sitters.isEmpty()) {
            list.addView(Ui.label(this, "No sitters nearby yet — check back soon."));
            return;
        }
        for (SitterProfile sitter : sitters) {
            list.addView(sitterCard(sitter));
            Ui.gap(list, this, 12);
        }
    }

    private LinearLayout sitterCard(SitterProfile sitter) {
        LinearLayout card = Ui.column(this, 12);

        TextView name = Ui.label(this, sitter.getDisplayName());
        name.setTextSize(16);
        card.addView(name);

        VerifiedBadgeView badge = new VerifiedBadgeView(this);
        badge.setState(sitter.isIdVerified() ? BadgeState.ID_VERIFIED : BadgeState.UNVERIFIED);
        card.addView(badge);

        card.addView(Ui.label(this,
                ReviewGuard.formatPrice(sitter.getRatePerVisitCents()) + " / visit · "
                        + sitter.getRadiusMiles() + " mi radius"));
        card.addView(Ui.label(this,
                ReviewGuard.ratingLine(sitter.getRating(), sitter.getReviewCount())
                        + " · " + sitter.getCompletedSits() + " sits completed"));
        card.addView(Ui.label(this, "Services: " + TextUtils.join(", ", sitter.getServices())));

        android.widget.Button open = Ui.button(this, "View profile");
        open.setOnClickListener(v -> {
            Intent intent = new Intent(this, SitterProfileActivity.class);
            intent.putExtra(SitterProfileActivity.EXTRA_SITTER_ID, sitter.getSitterId());
            startActivity(intent);
        });
        card.addView(open);
        return card;
    }
}
