package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gardenswap.test.api.ListingStatus;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** JVM tests for {@link DetailViewLogic} display rules (UID-012). */
public class DetailViewLogicTest {

    private static final String OWNER = "owner-1";
    private static final String VIEWER = "viewer-2";

    @Test
    public void claimVisibleWhenLiveAndNotOwner() {
        assertTrue(DetailViewLogic.isClaimCtaVisible(ListingStatus.LIVE, OWNER, VIEWER));
    }

    @Test
    public void claimHiddenForOwner() {
        assertFalse(DetailViewLogic.isClaimCtaVisible(ListingStatus.LIVE, OWNER, OWNER));
    }

    @Test
    public void claimHiddenWhenNotLive() {
        assertFalse(DetailViewLogic.isClaimCtaVisible(ListingStatus.DRAFT, OWNER, VIEWER));
        assertFalse(DetailViewLogic.isClaimCtaVisible(ListingStatus.CLAIMED, OWNER, VIEWER));
        assertFalse(DetailViewLogic.isClaimCtaVisible(ListingStatus.COMPLETED, OWNER, VIEWER));
    }

    @Test
    public void claimHiddenWhenStatusOrOwnerNull() {
        assertFalse(DetailViewLogic.isClaimCtaVisible(null, OWNER, VIEWER));
        assertFalse(DetailViewLogic.isClaimCtaVisible(ListingStatus.LIVE, null, VIEWER));
    }

    @Test
    public void claimVisibleForLoggedOutViewer() {
        assertTrue(DetailViewLogic.isClaimCtaVisible(ListingStatus.LIVE, OWNER, null));
    }

    @Test
    public void cancelVisibleForOwnerWhileEditable() {
        assertTrue(DetailViewLogic.isCancelCtaVisible(ListingStatus.DRAFT, OWNER, OWNER));
        assertTrue(DetailViewLogic.isCancelCtaVisible(ListingStatus.LIVE, OWNER, OWNER));
    }

    @Test
    public void cancelHiddenForNonOwnerOrTerminal() {
        assertFalse(DetailViewLogic.isCancelCtaVisible(ListingStatus.LIVE, OWNER, VIEWER));
        assertFalse(DetailViewLogic.isCancelCtaVisible(ListingStatus.CLAIMED, OWNER, OWNER));
        assertFalse(DetailViewLogic.isCancelCtaVisible(ListingStatus.CANCELLED, OWNER, OWNER));
        assertFalse(DetailViewLogic.isCancelCtaVisible(null, OWNER, OWNER));
    }

    @Test
    public void terminalNoticeOnlyForTerminalStates() {
        assertTrue(DetailViewLogic.showTerminalNotice(ListingStatus.COMPLETED));
        assertTrue(DetailViewLogic.showTerminalNotice(ListingStatus.EXPIRED));
        assertTrue(DetailViewLogic.showTerminalNotice(ListingStatus.CANCELLED));
        assertFalse(DetailViewLogic.showTerminalNotice(ListingStatus.LIVE));
        assertFalse(DetailViewLogic.showTerminalNotice(ListingStatus.DRAFT));
        assertFalse(DetailViewLogic.showTerminalNotice(ListingStatus.CLAIMED));
        assertFalse(DetailViewLogic.showTerminalNotice(null));
    }

    @Test
    public void creditLineFormatsFreeAndCounts() {
        assertEquals("Free \u2014 no credits needed", DetailViewLogic.creditLine(true, 2));
        assertEquals("1 credit", DetailViewLogic.creditLine(false, 1));
        assertEquals("3 credits", DetailViewLogic.creditLine(false, 3));
    }

    @Test
    public void quantityLineCombinesAmountAndUnit() {
        assertEquals("6 starts", DetailViewLogic.quantityLine(6.0, "starts"));
        assertEquals("1.5 kg", DetailViewLogic.quantityLine(1.5, "kg"));
        assertEquals("2", DetailViewLogic.quantityLine(2.0, null));
        assertEquals("2", DetailViewLogic.quantityLine(2.0, "  "));
        assertNull(DetailViewLogic.quantityLine(null, "starts"));
    }

    @Test
    public void photoCountLabelHandlesZeroOneMany() {
        assertEquals("No photos", DetailViewLogic.photoCountLabel(0));
        assertEquals("1 photo", DetailViewLogic.photoCountLabel(1));
        assertEquals("3 photos", DetailViewLogic.photoCountLabel(3));
    }

    @Test
    public void visitRulesSplitOnBlankLines() {
        List<String> items = DetailViewLogic.visitRuleItems("Ring the bell\n\nPark on the street\n ");
        assertEquals(Arrays.asList("Ring the bell", "Park on the street"), items);
    }

    @Test
    public void visitRulesEmptyWhenNullOrBlank() {
        assertTrue(DetailViewLogic.visitRuleItems(null).isEmpty());
        assertTrue(DetailViewLogic.visitRuleItems("  \n ").isEmpty());
    }

    @Test
    public void visitRulesTreatsLiteralNullAsBlank() {
        assertTrue(DetailViewLogic.visitRuleItems("null").isEmpty());
        assertTrue(DetailViewLogic.visitRuleItems("NULL").isEmpty());
        assertTrue(DetailViewLogic.visitRuleItems(" Null ").isEmpty());
    }

    @Test
    public void visitRulesDropsNullLinesButKeepsRealRules() {
        List<String> items = DetailViewLogic.visitRuleItems("null\nBring gloves\nNULL");
        assertEquals(Arrays.asList("Bring gloves"), items);
    }

    @Test
    public void visitRulesKeepsLinesMerelyContainingNull() {
        List<String> items = DetailViewLogic.visitRuleItems("Do not nullify the gate code");
        assertEquals(Arrays.asList("Do not nullify the gate code"), items);
    }

    @Test
    public void sprayLabelMapsNoneSentinelToFriendlyLabel() {
        assertEquals("No sprays used", DetailViewLogic.sprayDisclosureLabel("none"));
        assertEquals("No sprays used", DetailViewLogic.sprayDisclosureLabel(" NONE "));
    }

    @Test
    public void sprayLabelPassesFreeTextThroughTrimmed() {
        assertEquals("Neem oil only, last applied 3 weeks ago.",
                DetailViewLogic.sprayDisclosureLabel("  Neem oil only, last applied 3 weeks ago. "));
    }

    @Test
    public void sprayLabelKeepsNullAndBlankAsNull() {
        assertNull(DetailViewLogic.sprayDisclosureLabel(null));
        assertNull(DetailViewLogic.sprayDisclosureLabel("   "));
    }
}
