package com.gardenswap.app.profile;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.ui.Nav;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.NavRouter;

/**
 * Profile placeholder (UID-004).
 *
 * <p>Temporary destination for the bottom-nav PROFILE tab until UID-023
 * builds the real Profile + My swaps screens.
 */
public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 24);
        root.addView(Ui.headline(this, "Profile"));
        Ui.gap(root, this, 8);
        root.addView(Ui.body(this,
                "Avatar, verification badge, and stats land here — coming in UID-023."));
        setContentView(root);
        Nav.attach(this, NavRouter.Tab.PROFILE);
    }
}
