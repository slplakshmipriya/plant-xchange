package com.gardenswap.app.onboarding;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.OnboardingValidator;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Onboarding step 2 of 3 (AND-010): display name + avatar.
 *
 * <p>Avatar via camera (thumbnail persisted to the app cache) or the gallery
 * picker. The gallery uses {@code ACTION_GET_CONTENT}, so no storage
 * permission is required; the camera path requests {@code CAMERA} at runtime.
 */
public class ProfileFormActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_AVATAR = "extra_avatar";

    private ImageView avatarView;
    private EditText nameInput;
    private TextView statusText;

    /** Content URI string (gallery) or absolute cache path (camera). */
    private String avatarRef;

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, PhoneAuthActivity.class));
            finish();
            return;
        }

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        avatarRef = uri.toString();
                        avatarView.setImageURI(uri);
                    }
                });
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        launchCamera();
                    } else {
                        setStatus("Camera permission denied — pick from gallery instead.");
                    }
                });
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        Bitmap thumb = extras == null ? null : (Bitmap) extras.get("data");
                        if (thumb != null) {
                            String path = saveAvatarThumb(thumb);
                            if (path != null) {
                                avatarRef = path;
                                avatarView.setImageBitmap(thumb);
                            }
                        }
                    }
                });

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Set up your profile");
        title.setTextSize(20);
        avatarView = new ImageView(this);
        int avatarSize = Ui.dp(this, 96);
        avatarView.setLayoutParams(new LinearLayout.LayoutParams(avatarSize, avatarSize));
        avatarView.setContentDescription("Profile photo");
        Button cameraButton = Ui.button(this, "Take photo");
        cameraButton.setOnClickListener(
                v -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA));
        Button galleryButton = Ui.button(this, "Choose from gallery");
        galleryButton.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        nameInput = Ui.input(this, "Display name", InputType.TYPE_CLASS_TEXT);
        Button continueButton = Ui.button(this, "Continue");
        continueButton.setOnClickListener(v -> onContinue());
        statusText = Ui.status(this);

        root.addView(title);
        Ui.gap(root, this, 16);
        root.addView(avatarView);
        Ui.gap(root, this, 8);
        root.addView(cameraButton);
        Ui.gap(root, this, 8);
        root.addView(galleryButton);
        Ui.gap(root, this, 16);
        root.addView(nameInput);
        Ui.gap(root, this, 16);
        root.addView(continueButton);
        Ui.gap(root, this, 16);
        root.addView(statusText);
        setContentView(root);
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(intent);
        } else {
            setStatus("No camera app found on this device.");
        }
    }

    /** Persist the camera thumbnail to the app cache; returns its absolute path. */
    private String saveAvatarThumb(Bitmap bitmap) {
        File file = new File(getCacheDir(), "avatar.jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            return file.getAbsolutePath();
        } catch (IOException e) {
            setStatus("Couldn't save the photo — try again.");
            return null;
        }
    }

    private void onContinue() {
        String name = nameInput.getText().toString();
        if (!OnboardingValidator.isValidDisplayName(name)) {
            setStatus("Enter a display name (2–40 characters).");
            return;
        }
        Intent intent = new Intent(this, HomeZipActivity.class);
        intent.putExtra(EXTRA_NAME, name.trim());
        if (avatarRef != null) {
            intent.putExtra(EXTRA_AVATAR, avatarRef);
        }
        startActivity(intent);
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }
}
