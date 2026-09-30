package com.gardenswap.test.ui;

import android.content.Context;
import android.text.TextUtils;
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

/**
 * 14-day availability strip for sitter profiles, in two modes:
 *
 * <ul>
 * <li>VIEW ({@link #renderView}) — read-only day cells showing Open/Busy
 * from the sitter's {@code unavailable_dates}.</li>
 * <li>EDIT ({@link #renderEditor}) — tappable chips; filled = unavailable.
 * The returned {@link Editor} exposes the edited set.</li>
 * </ul>
 *
 * <p>State labels are deliberately short ("Open"/"Busy") and single-line so
 * they can never wrap inside the narrow day cells.
 */
public final class AvailabilityStrip {

    /** Days shown, starting today. */
    public static final int DAYS = 14;

    private static final SimpleDateFormat DAY_FORMAT =
            new SimpleDateFormat("EEE d", Locale.US);
    private static final SimpleDateFormat ISO_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private AvailabilityStrip() {
    }

    /** ISO date (yyyy-MM-dd) for the given day offset from today. */
    public static String isoForOffset(int offsetDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, offsetDays);
        return ISO_FORMAT.format(cal.getTime());
    }

    /** Read-only strip. */
    public static void renderView(Context context, LinearLayout parent,
                                  Set<String> unavailableDates) {
        Set<String> unavailable = unavailableDates == null
                ? new LinkedHashSet<String>() : unavailableDates;
        List<LinearLayout> rows = buildRows(context, parent);
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < DAYS; i++) {
            boolean busy = unavailable.contains(ISO_FORMAT.format(cal.getTime()));
            rows.get(i / 7).addView(dayCell(context,
                    DAY_FORMAT.format(cal.getTime()), busy, null, null));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    /** Editable strip; tap a day to toggle unavailable. */
    public static Editor renderEditor(Context context, LinearLayout parent,
                                      Set<String> unavailableDates) {
        final Set<String> edited = new LinkedHashSet<>();
        if (unavailableDates != null) {
            edited.addAll(unavailableDates);
        }
        List<LinearLayout> rows = buildRows(context, parent);
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < DAYS; i++) {
            final String iso = ISO_FORMAT.format(cal.getTime());
            final boolean[] busy = {edited.contains(iso)};
            final TextView[] chipHolder = new TextView[1];
            LinearLayout cell = dayCell(context, DAY_FORMAT.format(cal.getTime()),
                    busy[0], chipHolder, new Runnable() {
                        @Override
                        public void run() {
                            busy[0] = !busy[0];
                            if (busy[0]) {
                                edited.add(iso);
                            } else {
                                edited.remove(iso);
                            }
                            Ui.setChipSelected(context, chipHolder[0], busy[0]);
                            chipHolder[0].setText(busy[0] ? "Busy" : "Open");
                        }
                    });
            rows.get(i / 7).addView(cell);
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new Editor(edited);
    }

    /** Two rows of seven day cells. */
    private static List<LinearLayout> buildRows(Context context, LinearLayout parent) {
        List<LinearLayout> rows = new ArrayList<>();
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            parent.addView(row);
            if (r == 0) {
                Ui.gap(parent, context, 8);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * One day cell: day label over a short single-line state chip.
     * {@code onToggle} null = read-only; {@code chipOut} receives the chip.
     */
    private static LinearLayout dayCell(Context context, String dayLabel,
                                        boolean busy, final TextView[] chipOut,
                                        final Runnable onToggle) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        TextView day = Ui.caption(context, dayLabel);
        day.setGravity(Gravity.CENTER);
        day.setSingleLine(true);
        day.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(day);
        TextView chip = Ui.chip(context, busy ? "Busy" : "Open");
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        Ui.setChipSelected(context, chip, busy);
        if (chipOut != null && chipOut.length > 0) {
            chipOut[0] = chip;
        }
        if (onToggle != null) {
            chip.setOnClickListener(v -> onToggle.run());
        } else {
            chip.setClickable(false);
            chip.setFocusable(false);
        }
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chip.setLayoutParams(chipParams);
        cell.addView(chip);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(Ui.dp(context, 4));
        cell.setLayoutParams(params);
        return cell;
    }

    /** Handle on an editable strip. */
    public static final class Editor {
        private final Set<String> unavailable;

        private Editor(Set<String> unavailable) {
            this.unavailable = unavailable;
        }

        /** Copy of the currently-unavailable ISO dates. */
        public Set<String> getUnavailable() {
            return new LinkedHashSet<>(unavailable);
        }
    }
}
