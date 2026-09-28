package com.gardenswap.app.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side validation for the create-listing form (AND-020).
 *
 * <p>Pure JVM — no Android dependencies. The backend re-validates everything
 * (API-020); this exists to give the form instant feedback and to block a
 * submit that could never succeed (e.g. missing spray disclosure).
 *
 * <p>All instants are epoch millis. {@code nowMs} is injected so tests are
 * deterministic.
 */
public final class CreateListingValidator {

    private CreateListingValidator() {
    }

    public static final class Draft {
        public String type;
        public List<String> photos;
        public String variety;
        public String quantityText;
        public String unit;
        public int creditCost = 1;
        public String pickupStartText;
        public String pickupEndText;
        public Long pickupStartMs;
        public Long pickupEndMs;
        public Long expiresAtMs;
        public String sprayDisclosure;
    }

    /** Returns the list of blocking errors; empty means the draft can publish. */
    public static List<String> validate(Draft draft, long nowMs) {
        List<String> errors = new ArrayList<>();
        if (draft == null) {
            errors.add("Listing is missing.");
            return errors;
        }
        if (draft.type == null || draft.type.trim().isEmpty()) {
            errors.add("Choose a listing type.");
        }
        if (draft.photos == null || draft.photos.isEmpty()) {
            errors.add("Add at least one photo.");
        }
        if (isBlank(draft.variety)) {
            errors.add("Enter the variety.");
        }
        if (!isBlank(draft.quantityText)) {
            try {
                double quantity = Double.parseDouble(draft.quantityText.trim());
                if (quantity <= 0) {
                    errors.add("Quantity must be greater than zero.");
                }
            } catch (NumberFormatException e) {
                errors.add("Quantity must be a number.");
            }
        }
        if (draft.creditCost < 1 || draft.creditCost > 3) {
            errors.add("Credit cost must be 1–3.");
        }
        if (!isBlank(draft.pickupStartText) && draft.pickupStartMs == null) {
            errors.add("Pickup start must look like 2026-10-05 09:00.");
        }
        if (!isBlank(draft.pickupEndText) && draft.pickupEndMs == null) {
            errors.add("Pickup end must look like 2026-10-05 12:00.");
        }
        if (draft.pickupStartMs != null && draft.pickupEndMs != null
                && draft.pickupEndMs <= draft.pickupStartMs) {
            errors.add("Pickup window end must be after the start.");
        }
        if (draft.expiresAtMs != null && draft.expiresAtMs <= nowMs) {
            errors.add("Expiry must be in the future.");
        }
        if (isBlank(draft.sprayDisclosure)) {
            errors.add("Spray disclosure is required — describe any pesticides used, or write \"none\".");
        }
        return errors;
    }

    /** Parsed quantity, or null when the field is blank. Throws on bad input. */
    public static Double parseQuantity(String quantityText) {
        if (isBlank(quantityText)) {
            return null;
        }
        return Double.parseDouble(quantityText.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
