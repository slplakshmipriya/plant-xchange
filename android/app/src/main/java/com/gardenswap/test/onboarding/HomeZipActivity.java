package com.gardenswap.test.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.MainActivity;
import com.gardenswap.test.api.ApiException;
import com.gardenswap.test.api.ApiProvider;
import com.gardenswap.test.api.GardenSwapApi;
import com.gardenswap.test.api.ProfileUpdate;
import com.gardenswap.test.api.UserProfile;
import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.OnboardingValidator;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Onboarding step 3 of 3 (AND-010): home ZIP.
 *
 * <p>On submit, upserts the profile via {@link GardenSwapApi} (mock until the
 * Wave 1 integration checkpoint), logs {@code onboarding_complete}, then
 * lands on the home placeholder.
 */
public class HomeZipActivity extends AppCompatActivity {

    private static final String TAG = "HomeZipActivity";

    private EditText zipInput;
    private Button continueButton;
    private ProgressBar progress;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, PhoneAuthActivity.class));
            finish();
            return;
        }

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Where do you garden?");
        title.setTextSize(20);
        TextView expl = Ui.label(this,
                "Your ZIP sets the search radius for nearby swaps. "
                        + "Only your approximate area is ever shown to others.");
        zipInput = Ui.input(this, "ZIP code (5 digits)", InputType.TYPE_CLASS_NUMBER);
        continueButton = Ui.button(this, "Finish");
        continueButton.setOnClickListener(v -> onFinish());
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        statusText = Ui.status(this);

        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(expl);
        Ui.gap(root, this, 16);
        root.addView(zipInput);
        Ui.gap(root, this, 16);
        root.addView(continueButton);
        Ui.gap(root, this, 16);
        root.addView(progress);
        root.addView(statusText);
        setContentView(root);
    }

    private void onFinish() {
        String zip = zipInput.getText().toString();
        if (!OnboardingValidator.isValidZip(zip)) {
            statusText.setText("Enter a valid 5-digit ZIP code.");
            return;
        }
        setBusy(true);
        String name = getIntent().getStringExtra(ProfileFormActivity.EXTRA_NAME);
        String avatar = getIntent().getStringExtra(ProfileFormActivity.EXTRA_AVATAR);
        // Avatar byte upload lands with the photo pipeline (API-022, Wave 3);
        // until then the local URI/path is passed through as the avatar ref.
        ProfileUpdate update = new ProfileUpdate(name, avatar, zip.trim());
        ApiProvider.get().upsertProfile(update, new GardenSwapApi.Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                FirebaseAnalytics.getInstance(HomeZipActivity.this)
                        .logEvent("onboarding_complete", null);
                reregisterFcmToken();
                Intent intent = new Intent(HomeZipActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(ApiException e) {
                setBusy(false);
                statusText.setText(
                        "Couldn't save your profile (" + e.getCode() + "). Try again.");
            }
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        continueButton.setEnabled(!busy);
        zipInput.setEnabled(!busy);
    }

    /** Re-register the FCM token now that the signed-in user id is known (AND-002). */
    private void reregisterFcmToken() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "FCM token fetch failed", task.getException());
                return;
            }
            ApiProvider.get().registerFcmToken(user.getUid(), task.getResult(),
                    new GardenSwapApi.Callback<Void>() {
                        @Override
                        public void onSuccess(Void ignored) {
                        }

                        @Override
                        public void onError(ApiException e) {
                            Log.w(TAG, "FCM re-register failed: " + e.getCode());
                        }
                    });
        });
    }
}
