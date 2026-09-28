package com.gardenswap.app.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.OnboardingValidator;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

/**
 * Onboarding step 1 of 3 (AND-010): phone authentication via Firebase
 * {@link PhoneAuthProvider}.
 *
 * <p>Handles instant auto-retrieval, manual code entry, resend, and failure
 * states (invalid number, quota exceeded, wrong code).
 */
public class PhoneAuthActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;

    private EditText phoneInput;
    private EditText codeInput;
    private TextView statusText;
    private Button sendCodeButton;
    private Button verifyButton;
    private Button resendButton;
    private Button googleSignInButton;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // Route through handleGoogleSignInResult whenever we have an
                // intent: a failed sign-in still carries the ApiException
                // status (e.g. DEVELOPER_ERROR) in its extras, and surfacing
                // it beats reporting every failure as "cancelled".
                if (result.getData() != null) {
                    handleGoogleSignInResult(
                            GoogleSignIn.getSignedInAccountFromIntent(result.getData()));
                } else {
                    setStatus("Google sign-in cancelled.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            goToProfileForm();
            return;
        }
        googleSignInClient = buildGoogleSignInClient();

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.display(this, "Welcome to Garden Swap");
        TextView subtitle = Ui.body(this,
                "Sign in with your phone number to start swapping plants.");
        phoneInput = Ui.input(this, "Phone number (e.g. +1 555 010 2030)",
                InputType.TYPE_CLASS_PHONE);
        sendCodeButton = Ui.primaryButton(this, "Continue");
        codeInput = Ui.input(this, "6-digit code", InputType.TYPE_CLASS_NUMBER);
        codeInput.setVisibility(View.GONE);
        verifyButton = Ui.primaryButton(this, "Verify code");
        verifyButton.setVisibility(View.GONE);
        resendButton = Ui.secondaryButton(this, "Resend code");
        resendButton.setVisibility(View.GONE);
        statusText = Ui.status(this);

        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(subtitle);
        Ui.gap(root, this, 24);
        root.addView(phoneInput);
        Ui.gap(root, this, 12);
        root.addView(sendCodeButton);
        Ui.gap(root, this, 16);
        root.addView(codeInput);
        Ui.gap(root, this, 8);
        root.addView(verifyButton);
        Ui.gap(root, this, 8);
        root.addView(resendButton);
        Ui.gap(root, this, 24);
        TextView divider = Ui.caption(this, "— or —");
        divider.setGravity(Gravity.CENTER);
        root.addView(divider);
        Ui.gap(root, this, 12);
        googleSignInButton = Ui.secondaryButton(this, "Continue with Google");
        if (googleSignInClient == null) {
            // google-services.json has no web OAuth client; hide Google sign-in.
            divider.setVisibility(View.GONE);
            googleSignInButton.setVisibility(View.GONE);
        } else {
            googleSignInButton.setOnClickListener(v -> startGoogleSignIn());
        }
        root.addView(googleSignInButton);
        Ui.gap(root, this, 16);
        root.addView(statusText);
        setContentView(root);

        callbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                setStatus("Verified automatically — signing in…");
                signInWithCredential(credential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                sendCodeButton.setEnabled(true);
                setStatus("Verification failed: " + friendlyError(e));
            }

            @Override
            public void onCodeSent(@NonNull String vid,
                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                verificationId = vid;
                resendToken = token;
                codeInput.setVisibility(View.VISIBLE);
                verifyButton.setVisibility(View.VISIBLE);
                resendButton.setVisibility(View.VISIBLE);
                setStatus("Code sent. Enter the 6-digit code.");
            }
        };

        sendCodeButton.setOnClickListener(v -> sendCode(false));
        verifyButton.setOnClickListener(v -> verifyCode());
        resendButton.setOnClickListener(v -> sendCode(true));

        if (savedInstanceState != null) {
            verificationId = savedInstanceState.getString("verificationId");
            if (verificationId != null) {
                codeInput.setVisibility(View.VISIBLE);
                verifyButton.setVisibility(View.VISIBLE);
                resendButton.setVisibility(View.VISIBLE);
                setStatus("Code sent. Enter the 6-digit code.");
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("verificationId", verificationId);
    }

    /**
     * Builds the Google sign-in client, or null when the web OAuth client ID
     * isn't available. The google-services Gradle plugin generates
     * {@code default_web_client_id} from the {@code oauth_client} entry in
     * google-services.json; if it's missing, re-download the JSON from the
     * Firebase console (the web client is auto-created with the project).
     */
    private GoogleSignInClient buildGoogleSignInClient() {
        int webClientIdRes =
                getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (webClientIdRes == 0) {
            return null;
        }
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(webClientIdRes))
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(this, gso);
    }

    private void startGoogleSignIn() {
        setStatus("Opening Google sign-in…");
        googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> task) {
        final GoogleSignInAccount account;
        try {
            account = task.getResult(ApiException.class);
        } catch (ApiException e) {
            setStatus("Google sign-in failed: " + googleSignInError(e));
            return;
        }
        String idToken = account.getIdToken();
        if (idToken == null) {
            setStatus("Google sign-in failed: no ID token returned.");
            return;
        }
        setStatus("Signing in…");
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener(this, signInTask -> {
            if (signInTask.isSuccessful()) {
                goToProfileForm();
            } else {
                Exception failure = signInTask.getException();
                setStatus("Sign-in failed: "
                        + (failure == null ? "unknown error" : failure.getMessage()));
            }
        });
    }

    private static String googleSignInError(ApiException e) {
        if (e.getStatusCode() == 12501) { // SIGN_IN_CANCELLED
            return "cancelled.";
        }
        if (e.getStatusCode() == 7) { // NETWORK_ERROR
            return "network error — check your connection.";
        }
        if (e.getStatusCode() == 10) { // DEVELOPER_ERROR
            return "configuration error (code 10) — the app's SHA-1 "
                    + "fingerprint is probably not registered in the Firebase "
                    + "console for this Android app.";
        }
        if (e.getStatusCode() == 12500) { // SIGN_IN_FAILED
            return "sign-in failed (code 12500) — check the SHA-1 in the "
                    + "Firebase console and that the OAuth consent screen is set up.";
        }
        return e.getMessage() == null ? "unknown error." : e.getMessage();
    }

    private void sendCode(boolean resend) {
        String e164 = OnboardingValidator.normalizePhoneToE164(
                phoneInput.getText().toString());
        if (e164 == null) {
            setStatus("Enter a valid phone number.");
            return;
        }
        if (resend && resendToken == null) {
            return;
        }
        sendCodeButton.setEnabled(false);
        setStatus(resend ? "Resending code…" : "Sending code…");
        PhoneAuthOptions.Builder builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks);
        if (resend) {
            builder.setForceResendingToken(resendToken);
        }
        PhoneAuthProvider.verifyPhoneNumber(builder.build());
    }

    private void verifyCode() {
        String code = codeInput.getText().toString().trim();
        if (verificationId == null || code.length() != 6) {
            setStatus("Enter the 6-digit code we sent.");
            return;
        }
        setStatus("Verifying…");
        signInWithCredential(PhoneAuthProvider.getCredential(verificationId, code));
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                goToProfileForm();
            } else {
                sendCodeButton.setEnabled(true);
                Exception failure = task.getException();
                if (failure instanceof FirebaseAuthInvalidCredentialsException) {
                    setStatus("That code didn't match. Check and try again.");
                } else {
                    setStatus("Sign-in failed: "
                            + (failure == null ? "unknown error" : failure.getMessage()));
                }
            }
        });
    }

    private String friendlyError(FirebaseException e) {        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "That phone number format isn't valid.";
        }
        if (isQuotaExceeded(e)) {
            return "Too many attempts — try again later.";
        }
        String message = e.getMessage();
        return message == null ? "unknown error" : message;
    }

    /**
     * Detects SMS quota-exceeded failures without referencing
     * {@code FirebaseTooManyRequestsException}, which was removed in
     * firebase-auth 23.x. Matches by class name so this keeps working
     * on older SDK versions and degrades gracefully on newer ones.
     */
    private static boolean isQuotaExceeded(FirebaseException e) {
        for (Class<?> c = e.getClass(); c != null; c = c.getSuperclass()) {
            if ("FirebaseTooManyRequestsException".equals(c.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private void goToProfileForm() {
        startActivity(new Intent(this, ProfileFormActivity.class));
        finish();
    }
}
