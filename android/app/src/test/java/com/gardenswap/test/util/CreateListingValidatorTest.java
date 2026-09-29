package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** JVM tests for {@link CreateListingValidator} (AND-020). */
public class CreateListingValidatorTest {

    private static final long NOW = 1_700_000_000_000L;

    private CreateListingValidator.Draft validDraft() {
        CreateListingValidator.Draft draft = new CreateListingValidator.Draft();
        draft.type = "seedling";
        draft.photos = Arrays.asList("content://photo/1");
        draft.variety = "Cherokee Purple tomato";
        draft.quantityText = "6";
        draft.unit = "starts";
        draft.creditCost = 2;
        draft.pickupStartMs = NOW + 3_600_000L;
        draft.pickupEndMs = NOW + 7_200_000L;
        draft.expiresAtMs = NOW + 7 * 86_400_000L;
        draft.sprayDisclosure = "Neem oil only, 3 weeks ago.";
        return draft;
    }

    @Test
    public void validDraftHasNoErrors() {
        assertTrue(CreateListingValidator.validate(validDraft(), NOW).isEmpty());
    }

    @Test
    public void nullDraftIsOneError() {
        List<String> errors = CreateListingValidator.validate(null, NOW);
        assertEquals(1, errors.size());
    }

    @Test
    public void photoIsRequired() {
        CreateListingValidator.Draft draft = validDraft();
        draft.photos = Collections.emptyList();
        List<String> errors = CreateListingValidator.validate(draft, NOW);
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("photo")));
    }

    @Test
    public void sprayDisclosureIsRequiredAndBlocksSubmit() {
        for (String bad : new String[]{null, "", "   "}) {
            CreateListingValidator.Draft draft = validDraft();
            draft.sprayDisclosure = bad;
            List<String> errors = CreateListingValidator.validate(draft, NOW);
            assertTrue("disclosure '" + bad + "' must block submit",
                    errors.stream().anyMatch(e -> e.toLowerCase().contains("spray")));
        }
        // "none" is an explicit, acceptable disclosure.
        CreateListingValidator.Draft draft = validDraft();
        draft.sprayDisclosure = "none";
        assertTrue(CreateListingValidator.validate(draft, NOW).isEmpty());
    }

    @Test
    public void creditCostBounds() {
        CreateListingValidator.Draft draft = validDraft();
        draft.creditCost = 0;
        assertTrue(hasError(draft, "credit"));
        draft.creditCost = 4;
        assertTrue(hasError(draft, "credit"));
        draft.creditCost = 3;
        assertTrue(CreateListingValidator.validate(draft, NOW).isEmpty());
    }

    @Test
    public void quantityMustBePositiveNumber() {
        CreateListingValidator.Draft draft = validDraft();
        draft.quantityText = "-2";
        assertTrue(hasError(draft, "quantity"));
        draft.quantityText = "abc";
        assertTrue(hasError(draft, "quantity"));
        // Blank quantity is allowed (e.g. "a few seedlings").
        draft.quantityText = "";
        assertTrue(CreateListingValidator.validate(draft, NOW).isEmpty());
    }

    @Test
    public void pickupWindowEndMustBeAfterStart() {
        CreateListingValidator.Draft draft = validDraft();
        draft.pickupEndMs = draft.pickupStartMs;
        assertTrue(hasError(draft, "pickup"));
    }

    @Test
    public void expiryMustBeInFuture() {
        CreateListingValidator.Draft draft = validDraft();
        draft.expiresAtMs = NOW - 1;
        List<String> errors = CreateListingValidator.validate(draft, NOW);
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("expiry")));
    }

    @Test
    public void varietyIsRequired() {
        CreateListingValidator.Draft draft = validDraft();
        draft.variety = " ";
        assertTrue(hasError(draft, "variety"));
    }

    @Test
    public void malformedPickupDatesAreFlagged() {
        CreateListingValidator.Draft draft = validDraft();
        draft.pickupStartText = "not a date";
        draft.pickupStartMs = null;
        List<String> errors = CreateListingValidator.validate(draft, NOW);
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("pickup start")));
    }

    @Test
    public void parseQuantity() {
        assertEquals(Double.valueOf(2.5), CreateListingValidator.parseQuantity(" 2.5 "));
        assertNull(CreateListingValidator.parseQuantity(""));
        assertNull(CreateListingValidator.parseQuantity(null));
    }

    private static boolean hasError(CreateListingValidator.Draft draft, String fragment) {
        return CreateListingValidator.validate(draft, NOW).stream()
                .anyMatch(e -> e.toLowerCase().contains(fragment));
    }
}
