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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, PhoneAuthActivity.class));
            finish();
            return;
        }

        startActivity(new Intent(this, ExploreActivity.class));
        finish();
    }
}
