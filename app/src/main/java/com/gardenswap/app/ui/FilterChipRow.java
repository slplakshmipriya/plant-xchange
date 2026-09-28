package com.gardenswap.app.ui;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gardenswap.app.api.ListingType;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-select filter chip row (UID-011): All, Seedlings, Pick, Harvest, Care.
 *
 * <p>Label mapping: Seedlings &rarr; {@link ListingType#SEEDLING},
 * Harvest &rarr; {@link ListingType#HARVEST}, Pick (pick-your-own) &rarr;
 * {@link ListingType#TREE}. "Care" has no {@link ListingType} in the current
 * data model, so it reports null (same as All) until the model gains one.
 *
 * <p>Uses a custom listener interface instead of
 * {@code java.util.function.Consumer} because minSdk is 23 and core-library
 * desugaring is not enabled.
 */
public class FilterChipRow extends HorizontalScrollView {

    /** Receives the newly selected filter; null means "All". */
    public interface OnFilterChangedListener {
        void onFilterChanged(ListingType filterOrNullAll);
    }

    private static final class ChipDef {
        final String label;
        final ListingType type;

        ChipDef(String label, ListingType type) {
            this.label = label;
            this.type = type;
        }
    }

    private static final ChipDef[] CHIPS = {
            new ChipDef("All", null),
            new ChipDef("Seedlings", ListingType.SEEDLING),
            new ChipDef("Pick", ListingType.TREE),
            new ChipDef("Harvest", ListingType.HARVEST),
            new ChipDef("Care", null),
    };

    private final List<TextView> chipViews = new ArrayList<>();
    private int selected = 0;
    private OnFilterChangedListener listener;

    public FilterChipRow(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int rowPadding = Ui.dp(context, 4);
        row.setPadding(0, rowPadding, 0, rowPadding);

        for (int i = 0; i < CHIPS.length; i++) {
            final int index = i;
            TextView chip = Ui.chip(context, CHIPS[i].label);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.leftMargin = Ui.dp(context, 8);
            }
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> select(index));
            row.addView(chip);
            chipViews.add(chip);
        }
        addView(row);
        refreshSelection();
    }

    public void setOnFilterChanged(OnFilterChangedListener listener) {
        this.listener = listener;
    }

    /** Currently selected filter; null means "All". */
    public ListingType getSelectedFilter() {
        return CHIPS[selected].type;
    }

    /** Selects a chip by index (0 = All). */
    public void select(int index) {
        if (index < 0 || index >= CHIPS.length || index == selected) {
            return;
        }
        selected = index;
        refreshSelection();
        if (listener != null) {
            listener.onFilterChanged(CHIPS[selected].type);
        }
    }

    private void refreshSelection() {
        for (int i = 0; i < chipViews.size(); i++) {
            Ui.setChipSelected(getContext(), chipViews.get(i), i == selected);
        }
    }
}
