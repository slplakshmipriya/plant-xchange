package com.gardenswap.app.profile;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.ui.Nav;
import com.gardenswap.app.ui.Ui;
import com.gardenswap.app.util.NavRouter;

/**
 * My swaps placeholder (UID-004).
 *
 * <p>Temporary destination for the bottom-nav SWAPS tab until UID-023
 * builds the real Profile + My swaps screens.
 */
public class MySwapsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this, 24);
        root.addView(Ui.headline(this, "My swaps"));
        Ui.gap(root, this, 8);
        root.addView(Ui.body(this,
                "Active and completed swaps land here — coming in UID-023."));
        setContentView(root);
        Nav.attach(this, NavRouter.Tab.SWAPS);
    }
}
