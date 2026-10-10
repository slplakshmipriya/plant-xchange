package com.gardenswap.test.api;

/**
 * A pick-your-own slot on a tree listing (PRD parity, r2 contract).
 *
 * <p>A slot is a bookable window on a given day. {@code creditCost} defaults
 * to 1 when the wire omits it (PRD §5: free listings ride the 1-credit
 * floor); {@code cashCents} is the optional fiat price when the sitter/owner
 * charges cash instead, null when unset.
 *
 * <p>The caller-relative annotations ({@code claimedByMe},
 * {@code visitConfirmed}, {@code lbsPicked}) are only present on the wire
 * for slots the signed-in user has claimed; everyone else gets the plain
 * public shape. Absent annotations parse as false/null.
 */
public final class Slot {

    private final String id;
    private final String treeId;
    private final long dayMs;
    private final long startMs;
    private final long endMs;
    private final int maxPickers;
    private final int claimedCount;
    private final int creditCost;
    private final Integer cashCents;
    private final boolean claimedByMe;
    private final boolean visitConfirmed;
    private final Double lbsPicked;

    public Slot(String id, String treeId, long dayMs, long startMs, long endMs,
                int maxPickers, int claimedCount, int creditCost, Integer cashCents) {
        this(id, treeId, dayMs, startMs, endMs, maxPickers, claimedCount,
                creditCost, cashCents, false, false, null);
    }

    /** Full constructor, including the caller-relative claim annotations. */
    public Slot(String id, String treeId, long dayMs, long startMs, long endMs,
                int maxPickers, int claimedCount, int creditCost, Integer cashCents,
                boolean claimedByMe, boolean visitConfirmed, Double lbsPicked) {
        this.id = id;
        this.treeId = treeId;
        this.dayMs = dayMs;
        this.startMs = startMs;
        this.endMs = endMs;
        this.maxPickers = maxPickers;
        this.claimedCount = claimedCount;
        this.creditCost = creditCost;
        this.cashCents = cashCents;
        this.claimedByMe = claimedByMe;
        this.visitConfirmed = visitConfirmed;
        this.lbsPicked = lbsPicked;
    }

    public String getId() {
        return id;
    }

    public String getTreeId() {
        return treeId;
    }

    /** Start of the slot's calendar day (epoch ms). */
    public long getDayMs() {
        return dayMs;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public int getMaxPickers() {
        return maxPickers;
    }

    public int getClaimedCount() {
        return claimedCount;
    }

    public int getCreditCost() {
        return creditCost;
    }

    /** Fiat price in cents; null when the slot is credit-only. */
    public Integer getCashCents() {
        return cashCents;
    }

    /** True when the signed-in user holds a claim on this slot. */
    public boolean isClaimedByMe() {
        return claimedByMe;
    }

    /** True once the signed-in user has confirmed their visit on this slot. */
    public boolean isVisitConfirmed() {
        return visitConfirmed;
    }

    /** Pounds the signed-in user logged at their visit; null until confirmed. */
    public Double getLbsPicked() {
        return lbsPicked;
    }

    /** Remaining capacity; never negative even on a racy wire value. */
    public int getRemainingCount() {
        return Math.max(0, maxPickers - claimedCount);
    }

    /** Immutable copy with a new claimed count (claim flow). */
    public Slot withClaimedCount(int claimedCount) {
        return new Slot(id, treeId, dayMs, startMs, endMs, maxPickers,
                claimedCount, creditCost, cashCents,
                claimedByMe, visitConfirmed, lbsPicked);
    }

    /** Immutable copy marked as claimed by the signed-in user. */
    public Slot withClaimedByMe() {
        return new Slot(id, treeId, dayMs, startMs, endMs, maxPickers,
                claimedCount, creditCost, cashCents,
                true, visitConfirmed, lbsPicked);
    }

    /** Immutable copy after a visit confirmation (visited, amount logged). */
    public Slot withVisitConfirmed(double lbs) {
        return new Slot(id, treeId, dayMs, startMs, endMs, maxPickers,
                claimedCount, creditCost, cashCents,
                true, true, lbs);
    }
}
