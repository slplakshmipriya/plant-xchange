package com.gardenswap.test.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.test.ui.Ui;
import com.gardenswap.test.util.OnboardingValidator;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseTooManyRequestsException;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            goToProfileForm();
            return;
        }

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Welcome to Garden Swap");
        title.setTextSize(20);
        phoneInput = Ui.input(this, "Phone number (e.g. +1 555 010 2030)",
                InputType.TYPE_CLASS_PHONE);
        sendCodeButton = Ui.button(this, "Send code");
        codeInput = Ui.input(this, "6-digit code", InputType.TYPE_CLASS_NUMBER);
        codeInput.setVisibility(View.GONE);
        verifyButton = Ui.button(this, "Verify code");
        verifyButton.setVisibility(View.GONE);
        resendButton = Ui.button(this, "Resend code");
        resendButton.setVisibility(View.GONE);
        statusText = Ui.status(this);

        root.addView(title);
        Ui.gap(root, this, 16);
        root.addView(phoneInput);
        Ui.gap(root, this, 8);
        root.addView(sendCodeButton);
        Ui.gap(root, this, 16);
        root.addView(codeInput);
        Ui.gap(root, this, 8);
        root.addView(verifyButton);
        Ui.gap(root, this, 8);
        root.addView(resendButton);
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

    private String friendlyError(FirebaseException e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "That phone number format isn't valid.";
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts — try again later.";
        }
        String message = e.getMessage();
        return message == null ? "unknown error" : message;
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private void goToProfileForm() {
        startActivity(new Intent(this, ProfileFormActivity.class));
        finish();
    }
}
