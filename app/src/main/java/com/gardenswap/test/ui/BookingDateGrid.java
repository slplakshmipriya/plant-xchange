package com.gardenswap.test.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.gardenswap.test.R;

/**
 * 30-day date picker for the booking sheet.
 *
 * <p>Only dates the sitter marked available are tappable; everything else is
 * dimmed. A selected date gets a GREEN TICK highlight — deliberately distinct
 * from the blue outline {@link AvailabilityStrip} uses for the sitter's own
 * availability strip (that strip stays read-only on profiles).
 *
 * <p>The returned {@link Grid} exposes the currently selected ISO dates.
 */
public final class BookingDateGrid {

    /** Days shown, starting today. */
    public static final int DAYS = 30;

    private static final SimpleDateFormat DAY_NAME_FORMAT =
            new SimpleDateFormat("EEE", Locale.US);
    private static final SimpleDateFormat DAY_NUMBER_FORMAT =
            new SimpleDateFormat("d", Locale.US);
    private static final SimpleDateFormat ISO_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private BookingDateGrid() {
    }

    /** ISO date (yyyy-MM-dd) for the given day offset from today. */
    public static String isoForOffset(int offsetDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, offsetDays);
        return ISO_FORMAT.format(cal.getTime());
    }

    /**
     * Renders the picker into {@code parent}. {@code availableDates} are the
     * sitter's available ISO dates; {@code initiallySelected} pre-selects
     * (intersected with the available set); {@code onChanged} runs after
     * every toggle.
     */
    public static Grid render(Context context, LinearLayout parent,
                              Set<String> availableDates,
                              Set<String> initiallySelected,
                              final Runnable onChanged) {
        final Set<String> available = availableDates == null
                ? new LinkedHashSet<String>() : availableDates;
        final Set<String> selected = new LinkedHashSet<>();
        if (initiallySelected != null) {
            for (String iso : initiallySelected) {
                if (available.contains(iso)) {
                    selected.add(iso);
                }
            }
        }
        List<LinearLayout> rows = buildRows(context, parent);
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < DAYS; i++) {
            final String iso = ISO_FORMAT.format(cal.getTime());
            final boolean open = available.contains(iso);
            final boolean[] picked = {selected.contains(iso)};
            final LinearLayout[] cellHolder = new LinearLayout[1];
            final TextView[] numberHolder = new TextView[1];
            LinearLayout cell = dayCell(context, cal, open, picked[0],
                    cellHolder, numberHolder);
            if (open) {
                cell.setOnClickListener(v -> {
                    picked[0] = !picked[0];
                    if (picked[0]) {
                        selected.add(iso);
                    } else {
                        selected.remove(iso);
                    }
                    setSelected(cellHolder[0], numberHolder[0], picked[0]);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
            }
            rows.get(i / 7).addView(cell);
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new Grid(selected);
    }

    /** Rows of seven day cells (30 days → 5 rows). */
    private static List<LinearLayout> buildRows(Context context, LinearLayout parent) {
        List<LinearLayout> rows = new ArrayList<>();
        int rowCount = (DAYS + 6) / 7;
        for (int r = 0; r < rowCount; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            parent.addView(row);
            if (r < rowCount - 1) {
                Ui.gap(parent, context, 8);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * One day cell: day name over the date number. Unavailable days are
     * dimmed and inert; available days get the green-tick highlight when
     * selected.
     */
    private static LinearLayout dayCell(Context context, Calendar day,
                                        boolean open, boolean picked,
                                        final LinearLayout[] cellOut,
                                        final TextView[] numberOut) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        int pad = Ui.dp(context, 6);
        cell.setPadding(pad, pad, pad, pad);

        TextView name = Ui.caption(context, DAY_NAME_FORMAT.format(day.getTime()));
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        cell.addView(name);

        TextView number = Ui.body(context, DAY_NUMBER_FORMAT.format(day.getTime()));
        number.setGravity(Gravity.CENTER);
        number.setTypeface(number.getTypeface(), Typeface.BOLD);
        number.setSingleLine(true);
        cell.addView(number);
        // Raw day number; the selected state renders "✓ 12" from it.
        number.setTag(DAY_NUMBER_FORMAT.format(day.getTime()));

        setSelected(cell, number, picked);
        if (open) {
            cell.setClickable(true);
            cell.setFocusable(true);
        } else {
            cell.setClickable(false);
            cell.setFocusable(false);
            cell.setAlpha(0.35f);
        }
        if (cellOut != null && cellOut.length > 0) {
            cellOut[0] = cell;
        }
        if (numberOut != null && numberOut.length > 0) {
            numberOut[0] = number;
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(Ui.dp(context, 4));
        cell.setLayoutParams(params);
        return cell;
    }

    /** Green outline + "✓" tick = selected; neutral cell otherwise. */
    private static void setSelected(LinearLayout cell, TextView number, boolean picked) {
        cell.setBackgroundResource(picked
                ? R.drawable.booking_cell_selected
                : R.drawable.availability_cell_bg);
        String raw = number.getTag() == null ? "" : number.getTag().toString();
        number.setText(picked ? "\u2713 " + raw : raw);
    }

    /** Handle on the rendered picker. */
    public static final class Grid {
        private final Set<String> selected;

        private Grid(Set<String> selected) {
            this.selected = selected;
        }

        /** Copy of the currently-selected ISO dates. */
        public Set<String> getSelected() {
            return new LinkedHashSet<>(selected);
        }
    }
}
