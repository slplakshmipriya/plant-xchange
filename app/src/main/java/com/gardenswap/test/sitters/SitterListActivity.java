package com.gardenswap.test.sitters;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.SitterProfile;
import com.gardenswap.test.api.UserProfile;
import com.gardenswap.test.ui.Nav;
import com.gardenswap.test.ui.SitterCardAdapter;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.NavRouter;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

/**
 * Sitter discovery list (AND-070), restyled per the design prototype
 * (UID-017).
 *
 * <p>Searches sitters by the signed-in user's home zip and renders them
 * as {@link SitterCardAdapter} rows. Tapping a card opens
 * {@link SitterProfileActivity} with {@link SitterProfileActivity#EXTRA_SITTER_ID}.
 */
public class SitterListActivity extends AppCompatActivity {

    private TextView statusText;
    private SitterCardAdapter adapter;
    private Button sitterActionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sticky brand bar, same as Explore; the screen title sits below it
        // while the sitter list scrolls.
        LinearLayout header = Ui.column(this, 24);
        header.addView(Ui.appTitleRow(this));
        int pad = Ui.dp(this, 24);
        header.setPadding(pad, pad, pad, 0);

        LinearLayout titleBlock = Ui.column(this, 24);
        titleBlock.setPadding(pad, 0, pad, 0);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.headline(this, "Find a plant sitter");
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button becomeSitter = Ui.rowButton(this, "+ Become a sitter", false);
        sitterActionButton = becomeSitter;
        titleRow.addView(title);
        titleRow.addView(becomeSitter);
        titleBlock.addView(titleRow);
        Ui.gap(titleBlock, this, 4);
        titleBlock.addView(Ui.body(this, "Local sitters for watering, repotting, and vacation care."));
        Ui.gap(titleBlock, this, 12);
        statusText = Ui.status(this);
        titleBlock.addView(statusText);
        Ui.gap(titleBlock, this, 8);

        LinearLayout listWrap = Ui.column(this, 24);
        listWrap.setPadding(pad, 0, pad, pad);
        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SitterCardAdapter(null);
        adapter.setOnSitterClickListener(sitter -> {
            Intent intent = new Intent(this, SitterProfileActivity.class);
            intent.putExtra(SitterProfileActivity.EXTRA_SITTER_ID, sitter.getSitterId());
            startActivity(intent);
        });
        list.setAdapter(adapter);
        listWrap.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(titleBlock);
        content.addView(listWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(Ui.stickyHeaderScreen(this, header, content));
        Nav.attach(this, NavRouter.Tab.CARE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload here (not just onCreate) so a sitter profile created via
        // BecomeSitterActivity shows up when returning to this screen.
        // Search around the signed-in user's own home ZIP (was a hardcoded
        // mock ZIP).
        load();
    }

    private void load() {
        statusText.setText("Finding sitters near you…");
        ApiProvider.get().getMe(new GardenSwapApi.Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                String zip = profile.getHomeZip();
                if (zip == null || zip.isEmpty()) {
                    statusText.setText(
                            "Set your home ZIP in your profile to find sitters nearby.");
                    render(new ArrayList<>());
                    return;
                }
                load(zip);
            }

            @Override
            public void onError(ApiException e) {
                statusText.setText("Couldn't load your profile (" + e.getCode() + ").");
            }
        });
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
        String myUid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Never show the signed-in user in their own sitter directory; if
        // they have an active sitter profile it becomes their entry point
        // for viewing/editing it instead.
        boolean iAmSitter = false;
        List<SitterProfile> others = new ArrayList<>();
        if (sitters != null) {
            for (SitterProfile s : sitters) {
                if (myUid != null && myUid.equals(s.getSitterId())) {
                    iAmSitter = true;
                } else {
                    others.add(s);
                }
            }
        }

        Button becomeSitter = sitterActionButton;
        if (iAmSitter) {
            becomeSitter.setText("Your sitter profile");
            becomeSitter.setOnClickListener(v -> {
                Intent intent = new Intent(this, SitterProfileActivity.class);
                intent.putExtra(SitterProfileActivity.EXTRA_SITTER_ID, myUid);
                startActivity(intent);
            });
        } else {
            becomeSitter.setText("+ Become a sitter");
            becomeSitter.setOnClickListener(v ->
                    startActivity(new Intent(this, BecomeSitterActivity.class)));
        }

        if (others.isEmpty()) {
            statusText.setText(iAmSitter
                    ? "No other sitters nearby yet — check back soon."
                    : "No sitters nearby yet — check back soon.");
        } else {
            statusText.setText("");
        }
        adapter.setSitters(others);
    }
}
