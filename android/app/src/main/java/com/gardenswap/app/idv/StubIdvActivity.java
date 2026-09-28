package com.gardenswap.app.idv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gardenswap.app.BuildConfig;
import com.gardenswap.app.api.IdvStatus;
import com.gardenswap.app.ui.Ui;

/**
 * Fake provider UI for {@link StubIdvProvider} (AND-011). Shows a PENDING
 * state, then auto-reports the configured outcome after a short delay.
 * Debug builds expose harness buttons to force verified/failed.
 */
public class StubIdvActivity extends AppCompatActivity {

    static final String EXTRA_OUTCOME = "extra_outcome";
    static final String EXTRA_SESSION_ID = "extra_session_id";

    private static final long PENDING_DELAY_MS = 2000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IdvStatus outcome = IdvStatus.fromString(
                getIntent().getStringExtra(EXTRA_OUTCOME));

        LinearLayout root = Ui.column(this, 24);
        TextView title = Ui.label(this, "Identity verification");
        title.setTextSize(20);
        TextView expl = Ui.label(this,
                "Stub provider — simulating the IDV web flow.\nStatus: PENDING…");
        root.addView(title);
        Ui.gap(root, this, 8);
        root.addView(expl);
        Ui.gap(root, this, 16);

        if (BuildConfig.DEBUG) {
            root.addView(Ui.label(this, "Test harness (debug builds only):"));
            Ui.gap(root, this, 8);
            Button forceVerified = Ui.button(this, "Simulate verified");
            forceVerified.setOnClickListener(v -> finishWith(IdvStatus.VERIFIED));
            Button forceFailed = Ui.button(this, "Simulate failed");
            forceFailed.setOnClickListener(v -> finishWith(IdvStatus.FAILED));
            root.addView(forceVerified);
            Ui.gap(root, this, 8);
            root.addView(forceFailed);
            Ui.gap(root, this, 8);
        }

        Button cancel = Ui.button(this, "Cancel");
        cancel.setOnClickListener(v -> cancel());
        root.addView(cancel);
        setContentView(root);

        handler.postDelayed(() -> finishWith(outcome), PENDING_DELAY_MS);
    }

    private void finishWith(IdvStatus status) {
        if (finished) {
            return;
        }
        finished = true;
        StubIdvProvider.deliverResult(status);
        finish();
    }

    private void cancel() {
        if (!finished) {
            finished = true;
            StubIdvProvider.deliverCancel();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        // Back press or system kill: don't strand the caller in "verifying…".
        handler.removeCallbacksAndMessages(null);
        if (!finished) {
            finished = true;
            StubIdvProvider.deliverCancel();
        }
        super.onDestroy();
    }
}
