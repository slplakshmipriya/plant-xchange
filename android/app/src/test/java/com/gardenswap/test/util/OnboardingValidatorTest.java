package com.gardenswap.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM unit tests for {@link OnboardingValidator} (AND-010). No Android dependencies. */
public class OnboardingValidatorTest {

    @Test
    public void normalizePhone_e164PassesThrough() {
        assertEquals("+15550102030", OnboardingValidator.normalizePhoneToE164("+15550102030"));
    }

    @Test
    public void normalizePhone_stripsFormattingCharacters() {
        assertEquals("+15550102030", OnboardingValidator.normalizePhoneToE164("+1 (555) 010-2030"));
        assertEquals("+15550102030", OnboardingValidator.normalizePhoneToE164("+1 555 010 2030"));
        assertEquals("+15550102030", OnboardingValidator.normalizePhoneToE164("  +15550102030  "));
    }

    @Test
    public void normalizePhone_tenDigitNationalAssumesPlusOne() {
        assertEquals("+15550102030", OnboardingValidator.normalizePhoneToE164("5550102030"));
        assertEquals("+15550102030", OnboardingValidator.normalizePhoneToE164("(555) 010-2030"));
    }

    @Test
    public void normalizePhone_elevenDigitLeadingOne() {
        assertEquals("+15550102030", OnboardingValidator.normalizePhoneToE164("15550102030"));
    }

    @Test
    public void normalizePhone_rejectsTooShort() {
        assertNull(OnboardingValidator.normalizePhoneToE164("+12345"));
        assertNull(OnboardingValidator.normalizePhoneToE164("5550102"));
    }

    @Test
    public void normalizePhone_rejectsTooLong() {
        assertNull(OnboardingValidator.normalizePhoneToE164("+1234567890123456"));
    }

    @Test
    public void normalizePhone_rejectsNullEmptyAndBlank() {
        assertNull(OnboardingValidator.normalizePhoneToE164(null));
        assertNull(OnboardingValidator.normalizePhoneToE164(""));
        assertNull(OnboardingValidator.normalizePhoneToE164("   "));
        assertNull(OnboardingValidator.normalizePhoneToE164("(abc)"));
    }

    @Test
    public void displayName_acceptsNormalNames() {
        assertTrue(OnboardingValidator.isValidDisplayName("Priya"));
        assertTrue(OnboardingValidator.isValidDisplayName("Jo"));
        assertTrue(OnboardingValidator.isValidDisplayName("  Maria Garcia  "));
    }

    @Test
    public void displayName_rejectsNullBlankAndSingleChar() {
        assertFalse(OnboardingValidator.isValidDisplayName(null));
        assertFalse(OnboardingValidator.isValidDisplayName(""));
        assertFalse(OnboardingValidator.isValidDisplayName("   "));
        assertFalse(OnboardingValidator.isValidDisplayName("J"));
    }

    @Test
    public void displayName_enforcesLengthBounds() {
        String forty = new String(new char[40]).replace('\0', 'a');
        String fortyOne = new String(new char[41]).replace('\0', 'a');
        assertTrue(OnboardingValidator.isValidDisplayName(forty));
        assertFalse(OnboardingValidator.isValidDisplayName(fortyOne));
    }

    @Test
    public void zip_acceptsFiveDigits() {
        assertTrue(OnboardingValidator.isValidZip("85281"));
        assertTrue(OnboardingValidator.isValidZip(" 85281 "));
        assertTrue(OnboardingValidator.isValidZip("00501"));
    }

    @Test
    public void zip_rejectsNonFiveDigit() {
        assertFalse(OnboardingValidator.isValidZip(null));
        assertFalse(OnboardingValidator.isValidZip(""));
        assertFalse(OnboardingValidator.isValidZip("8528"));
        assertFalse(OnboardingValidator.isValidZip("852810"));
        assertFalse(OnboardingValidator.isValidZip("8528a"));
        assertFalse(OnboardingValidator.isValidZip("85281-1234"));
    }
}
