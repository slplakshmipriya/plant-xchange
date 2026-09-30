package com.gardenswap.test.onboarding;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
        root.addView(Ui.eyebrow(this, "Step 3 of 3"));
        Ui.gap(root, this, 8);
        root.addView(Ui.headline(this, "Where do you garden?"));
        Ui.gap(root, this, 8);
        root.addView(Ui.body(this,
                "Your ZIP sets the search radius for nearby swaps. "
                        + "Only your approximate area is ever shown to others."));
        Ui.gap(root, this, 24);
        root.addView(Ui.eyebrow(this, "Home ZIP code"));
        Ui.gap(root, this, 8);
        zipInput = Ui.input(this, "ZIP code (5 digits)", InputType.TYPE_CLASS_NUMBER);
        root.addView(zipInput);
        Ui.gap(root, this, 24);
        continueButton = Ui.primaryButton(this, "Finish");
        continueButton.setOnClickListener(v -> onFinish());
        root.addView(continueButton);
        Ui.gap(root, this, 16);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress);
        statusText = Ui.status(this);
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
        String avatarUri = getIntent().getStringExtra(ProfileFormActivity.EXTRA_AVATAR);
        if (avatarUri != null) {
            // Upload the photo first, then save the profile with the public URL.
            statusText.setText("Uploading your photo...");
            uploadAvatarThenSave(name, avatarUri, zip.trim());
        } else {
            saveProfile(name, null, zip.trim());
        }
    }

    private void uploadAvatarThenSave(String name, String avatarUri, String zip) {
        new Thread(() -> {
            try {
                byte[] bytes;
                String contentType;
                try (InputStream in = getContentResolver().openInputStream(Uri.parse(avatarUri));
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    if (in == null) {
                        throw new IOException("Couldn't open the photo.");
                    }
                    contentType = getContentResolver().getType(Uri.parse(avatarUri));
                    if (contentType == null) {
                        contentType = "image/jpeg";
                    }
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                    bytes = out.toByteArray();
                }
                if (bytes.length > 8 * 1024 * 1024) {
                    runOnUiThread(() -> {
                        setBusy(false);
                        statusText.setText(
                                "That photo is too large (8 MB max). Pick a smaller one.");
                    });
                    return;
                }
                final String ct = contentType;
                runOnUiThread(() -> ApiProvider.get().uploadAvatar(bytes, ct,
                        new GardenSwapApi.Callback<String>() {
                            @Override
                            public void onSuccess(String publicUrl) {
                                saveProfile(name, publicUrl, zip);
                            }

                            @Override
                            public void onError(ApiException e) {
                                setBusy(false);
                                // database_waking: cold start after idle (Neon
                                // resume) — the backend already retried the
                                // checkout; one more tap will go through.
                                String msg = "database_waking".equals(e.getCode())
                                        ? "Our servers are waking up — try again in a moment, or skip the photo for now."
                                        : "Couldn't upload your photo ("
                                                + e.getCode() + "). Try again or skip the photo.";
                                statusText.setText(msg);
                            }
                        }));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    statusText.setText("Couldn't read your photo. Try again or skip it.");
                });
            }
        }).start();
    }

    private void saveProfile(String name, String avatarUrl, String zip) {
        // Age attestation: onboarding is 13+ only; the explicit checkbox UI
        // lands with the Terms screen, until then attest inline.
        ProfileUpdate update = new ProfileUpdate(name, avatarUrl, zip, true);
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
                statusText.setText("Couldn't save your profile. Try again.");
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
