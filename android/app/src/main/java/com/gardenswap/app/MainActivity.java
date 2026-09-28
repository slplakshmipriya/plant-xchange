package com.gardenswap.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.api.ApiException;
import com.gardenswap.app.api.ApiProvider;
import com.gardenswap.app.api.GardenSwapApi;
import com.gardenswap.app.api.IdvStatus;
import com.gardenswap.app.idv.IdvActivity;
import com.gardenswap.app.onboarding.PhoneAuthActivity;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.ui.VerifiedBadgeView;
import com.gardenswap.app.util.IdvStatusMapper;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Launcher + router (AND-001/AND-010/AND-011). Unauthenticated users are sent
 * to onboarding; signed-in users get the home placeholder (the real feed
 * lands with EPIC-MVP-3) plus their verification badge and an IDV entry point.
 */
public class MainActivity extends AppCompatActivity {

    private VerifiedBadgeView badgeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
