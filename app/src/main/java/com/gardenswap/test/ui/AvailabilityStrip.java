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
 * 14-day availability grid for sitter profiles, in two modes:
 *
 * <ul>
 * <li>VIEW ({@link #renderView}) — read-only day cells; a blue rectangle
 * outline marks days the sitter blocked out.</li>
 * <li>EDIT ({@link #renderEditor}) — the whole day cell is the tap target;
 * tapping toggles the blue rectangle outline (blocked out). The returned
 * {@link Editor} exposes the edited set.</li>
 * </ul>
 *
 * <p>Each cell shows the day name over the date number. No Open/Busy text:
 * the blue outline is the only state signal.</p>
 */
public final class AvailabilityStrip {

    /** Days shown, starting today. */
    public static final int DAYS = 14;

    private static final SimpleDateFormat DAY_NAME_FORMAT =
            new SimpleDateFormat("EEE", Locale.US);
    private static final SimpleDateFormat DAY_NUMBER_FORMAT =
            new SimpleDateFormat("d", Locale.US);
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

    /** Read-only grid; blue outline = blocked out. */
    public static void renderView(Context context, LinearLayout parent,
                                  Set<String> unavailableDates) {
        Set<String> unavailable = unavailableDates == null
                ? new LinkedHashSet<String>() : unavailableDates;
        List<LinearLayout> rows = buildRows(context, parent);
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < DAYS; i++) {
            boolean busy = unavailable.contains(ISO_FORMAT.format(cal.getTime()));
            rows.get(i / 7).addView(dayCell(context, cal, busy, null));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    /** Editable grid; tap a day to toggle it blocked out. */
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
            final LinearLayout[] cellHolder = new LinearLayout[1];
            LinearLayout cell = dayCell(context, cal, busy[0], cellHolder);
            cell.setOnClickListener(v -> {
                busy[0] = !busy[0];
                if (busy[0]) {
                    edited.add(iso);
                } else {
                    edited.remove(iso);
                }
                setSelected(cellHolder[0], busy[0]);
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
     * One day cell: day name over the date number. The blue rectangle
     * outline is the selection; the whole cell is the tap target in edit
     * mode ({@code cellOut} non-null, cell is clickable).
     */
    private static LinearLayout dayCell(Context context, Calendar day,
                                        boolean busy, final LinearLayout[] cellOut) {
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

        setSelected(cell, busy);
        if (cellOut != null && cellOut.length > 0) {
            cellOut[0] = cell;
            cell.setClickable(true);
            cell.setFocusable(true);
        } else {
            cell.setClickable(false);
            cell.setFocusable(false);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(Ui.dp(context, 4));
        cell.setLayoutParams(params);
        return cell;
    }

    private static void setSelected(LinearLayout cell, boolean selected) {
        cell.setBackgroundResource(selected
                ? R.drawable.availability_cell_selected
                : R.drawable.availability_cell_bg);
    }

    /** Handle on an editable grid. */
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
