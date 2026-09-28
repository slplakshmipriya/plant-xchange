package com.gardenswap.app.util;

import com.gardenswap.app.MainActivity;
import com.gardenswap.app.R;
import com.gardenswap.app.chat.ThreadListActivity;
import com.gardenswap.app.profile.MySwapsActivity;
import com.gardenswap.app.profile.ProfileActivity;
import com.gardenswap.app.sitters.SitterListActivity;

/**
 * Pure bottom-navigation routing logic (UID-004).
 *
 * <p>Maps each {@link Tab} to its destination activity and menu item id.
 * No Android framework calls — safe for JVM unit tests.
 */
public final class NavRouter {

    /** The five top-level destinations of the bottom nav. */
    public enum Tab {
        EXPLORE,
        MESSAGES,
        SWAPS,
        CARE,
        PROFILE
    }

    private NavRouter() {
    }

    /** Destination activity for a tab. */
    public static Class<?> activityFor(Tab tab) {
        switch (tab) {
            case EXPLORE:
                return MainActivity.class;
            case MESSAGES:
                return ThreadListActivity.class;
            case SWAPS:
                return MySwapsActivity.class;
            case CARE:
                return SitterListActivity.class;
            case PROFILE:
                return ProfileActivity.class;
            default:
                throw new IllegalArgumentException("Unknown tab: " + tab);
        }
    }

    /** Menu item id for a tab (see res/menu/bottom_nav_menu.xml). */
    public static int menuId(Tab tab) {
        switch (tab) {
            case EXPLORE:
                return R.id.nav_explore;
            case MESSAGES:
                return R.id.nav_messages;
            case SWAPS:
                return R.id.nav_swaps;
            case CARE:
                return R.id.nav_care;
            case PROFILE:
                return R.id.nav_profile;
            default:
                throw new IllegalArgumentException("Unknown tab: " + tab);
        }
    }

    /** Tab for a menu item id, or null when the id is not a nav destination. */
    public static Tab tabForMenuId(int menuId) {
        if (menuId == R.id.nav_explore) {
            return Tab.EXPLORE;
        }
        if (menuId == R.id.nav_messages) {
            return Tab.MESSAGES;
        }
        if (menuId == R.id.nav_swaps) {
            return Tab.SWAPS;
        }
        if (menuId == R.id.nav_care) {
            return Tab.CARE;
        }
        if (menuId == R.id.nav_profile) {
            return Tab.PROFILE;
        }
        return null;
    }

    /** True when tapping the target tab would stay on the current screen. */
    public static boolean isSameTab(Tab current, Tab target) {
        return current != null && current == target;
    }
}
