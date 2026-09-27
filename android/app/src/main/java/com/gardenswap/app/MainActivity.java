package com.gardenswap.app;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Placeholder launcher activity (AND-001). Replaced by the real home/feed
 * screen once EPIC-MVP-3 listing UI lands.
 */
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView placeholder = new TextView(this);
        placeholder.setText("Garden Swap — coming soon");
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setTextSize(20);
        setContentView(placeholder);
    }
}
