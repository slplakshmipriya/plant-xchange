package com.gardenswap.test.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.util.NavRouter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomnavigation.LabelVisibilityMode;

/**
 * Bottom navigation bar (UID-004).
 *
 * <p>Call {@link #attach(AppCompatActivity, NavRouter.Tab)} after
 * {@code setContentView(...)} on each top-level screen. The bar is added
 * below the existing content, styled with the nav-green/acid tokens, and
 * routes taps via {@link NavRouter}. Tapping the already-selected tab is a
 * no-op; other taps reuse the existing destination instance
 * ({@code FLAG_ACTIVITY_REORDER_TO_FRONT}) so the back stack stays flat.
 */
public final class Nav {

    private Nav() {
    }

    public static void attach(AppCompatActivity activity, NavRouter.Tab selected) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }
        View current = content.getChildAt(0);
        content.removeView(current);

        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(current, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        BottomNavigationView bar = buildBar(activity, selected);
        wrapper.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(wrapper, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static BottomNavigationView buildBar(
            AppCompatActivity activity, NavRouter.Tab selected) {
        BottomNavigationView bar = new BottomNavigationView(activity);
        bar.inflateMenu(R.menu.bottom_nav_menu);
        bar.setBackgroundColor(ResourcesCompat.getColor(
                activity.getResources(), R.color.garden_nav, activity.getTheme()));
        ColorStateList tint = ResourcesCompat.getColorStateList(
                activity.getResources(), R.color.nav_item_tint, activity.getTheme());
        if (tint != null) {
            bar.setItemIconTintList(tint);
            bar.setItemTextColor(tint);
        }
        bar.setLabelVisibilityMode(LabelVisibilityMode.LABEL_VISIBILITY_LABELED);
        // Set the checked item before installing the listener so the initial
        // selection does not fire a navigation event.
        bar.setSelectedItemId(NavRouter.menuId(selected));
        bar.setOnItemSelectedListener(item -> {
            NavRouter.Tab target = NavRouter.tabForMenuId(item.getItemId());
            if (target == null || NavRouter.isSameTab(selected, target)) {
                return true;
            }
            Intent intent = new Intent(activity, NavRouter.activityFor(target));
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivity(intent);
            return true;
        });
        return bar;
    }
}
