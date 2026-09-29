package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gardenswap.test.MainActivity;
import com.gardenswap.test.R;
import com.gardenswap.test.chat.ThreadListActivity;
import com.gardenswap.test.profile.MySwapsActivity;
import com.gardenswap.test.profile.ProfileActivity;
import com.gardenswap.test.sitters.SitterListActivity;
import com.gardenswap.test.util.NavRouter.Tab;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** JVM tests for {@link NavRouter} (UID-004). */
public class NavRouterTest {

    @Test
    public void exploreMapsToMainActivity() {
        assertEquals(MainActivity.class, NavRouter.activityFor(Tab.EXPLORE));
    }

    @Test
    public void messagesMapsToThreadListActivity() {
        assertEquals(ThreadListActivity.class, NavRouter.activityFor(Tab.MESSAGES));
    }

    @Test
    public void swapsMapsToMySwapsActivity() {
        assertEquals(MySwapsActivity.class, NavRouter.activityFor(Tab.SWAPS));
    }

    @Test
    public void careMapsToSitterListActivity() {
        assertEquals(SitterListActivity.class, NavRouter.activityFor(Tab.CARE));
    }

    @Test
    public void profileMapsToProfileActivity() {
        assertEquals(ProfileActivity.class, NavRouter.activityFor(Tab.PROFILE));
    }

    @Test
    public void everyTabHasADistinctMenuId() {
        Set<Integer> ids = new HashSet<>();
        for (Tab tab : Tab.values()) {
            ids.add(NavRouter.menuId(tab));
        }
        assertEquals(Tab.values().length, ids.size());
    }

    @Test
    public void menuIdMatchesBottomNavMenu() {
        assertEquals(R.id.nav_explore, NavRouter.menuId(Tab.EXPLORE));
        assertEquals(R.id.nav_messages, NavRouter.menuId(Tab.MESSAGES));
        assertEquals(R.id.nav_swaps, NavRouter.menuId(Tab.SWAPS));
        assertEquals(R.id.nav_care, NavRouter.menuId(Tab.CARE));
        assertEquals(R.id.nav_profile, NavRouter.menuId(Tab.PROFILE));
    }

    @Test
    public void menuIdRoundTripsToTab() {
        for (Tab tab : Tab.values()) {
            assertEquals(tab, NavRouter.tabForMenuId(NavRouter.menuId(tab)));
        }
    }

    @Test
    public void unknownMenuIdReturnsNull() {
        assertNull(NavRouter.tabForMenuId(-1));
        assertNull(NavRouter.tabForMenuId(0));
    }

    @Test
    public void sameTabIsSame() {
        assertTrue(NavRouter.isSameTab(Tab.EXPLORE, Tab.EXPLORE));
        assertTrue(NavRouter.isSameTab(Tab.PROFILE, Tab.PROFILE));
    }

    @Test
    public void differentTabsAreNotSame() {
        assertFalse(NavRouter.isSameTab(Tab.EXPLORE, Tab.MESSAGES));
        assertFalse(NavRouter.isSameTab(Tab.CARE, Tab.SWAPS));
    }

    @Test
    public void nullCurrentOrTargetIsNotSame() {
        assertFalse(NavRouter.isSameTab(null, Tab.EXPLORE));
        assertFalse(NavRouter.isSameTab(Tab.EXPLORE, null));
    }

    @Test
    public void fiveTabsDefined() {
        assertEquals(5, Tab.values().length);
    }
}
