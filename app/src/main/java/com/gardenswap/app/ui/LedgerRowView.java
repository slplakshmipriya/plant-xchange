package com.gardenswap.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.app.R;
import com.gardenswap.app.api.LedgerEntry;
import com.gardenswap.app.util.LedgerFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * One ledger row (UID-015): description + date on the left, signed amount on
 * the right. Amount formatting is delegated to {@link LedgerFormatter} so its
 * behavior stays the single source of truth.
 */
public class LedgerRowView extends LinearLayout {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, yyyy", Locale.US);

    private final TextView descriptionView;
    private final TextView dateView;
    private final TextView amountView;

    public LedgerRowView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.card_bg);
        int padding = Ui.dp(context, 12);
        setPadding(padding, padding, padding, padding);

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(VERTICAL);
        descriptionView = Ui.body(context, "");
        dateView = Ui.caption(context, "");
        textColumn.addView(descriptionView);
        textColumn.addView(dateView);

        amountView = Ui.title(context, "");

        addView(textColumn, new LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addView(amountView);
    }

    public void bind(LedgerEntry entry) {
        String reason = entry.getReason() == null || entry.getReason().isEmpty()
                ? entry.getKind().name().toLowerCase()
                : entry.getReason();
        descriptionView.setText(reason);
        dateView.setText(DATE_FORMAT.format(new Date(entry.getCreatedAtMs())));
        amountView.setText(LedgerFormatter.formatDelta(entry.getDelta()));

        int colorRes;
        if (entry.getDelta() > 0) {
            colorRes = R.color.garden_leaf;
        } else if (entry.getDelta() < 0) {
            colorRes = R.color.garden_red;
        } else {
            colorRes = R.color.garden_muted;
        }
        amountView.setTextColor(ResourcesCompat.getColor(
                getResources(), colorRes, getContext().getTheme()));
    }
}
