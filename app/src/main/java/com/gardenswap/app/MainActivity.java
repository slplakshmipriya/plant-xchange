package com.gardenswap.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.explore.ExploreActivity;
import com.gardenswap.app.onboarding.PhoneAuthActivity;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Launcher + router (AND-001/AND-010/AND-011). Unauthenticated users are sent
 * to onboarding; signed-in users go to the Explore home screen (UID-010).
 */
public class MainActivity extends AppCompatActivity {

    /**
     * DEBUG BRANCH ONLY — never merge to main or prd-parity. When true, the
     * Firebase auth gate is skipped and the app opens straight into Explore
     * with no login, for testing flows past the auth screens.
     */
    private static final boolean BYPASS_AUTH = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BYPASS_AUTH && FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, PhoneAuthActivity.class));
            finish();
            return;
        }

        startActivity(new Intent(this, ExploreActivity.class));
        finish();
    }
}
