package dev.baritoneautomate.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingNumbersTest {
	@Test
	void acceptsFiniteSignedValuesAndScientificNotation() {
		assertTrue(SettingNumbers.valid(SettingKind.INTEGER, " -12 "));
		assertTrue(SettingNumbers.valid(SettingKind.LONG, "9223372036854775807"));
		assertTrue(SettingNumbers.valid(SettingKind.FLOAT, "1.25e-3"));
		assertTrue(SettingNumbers.valid(SettingKind.DOUBLE, "-1.25e300"));
	}

	@Test
	void rejectsNonFiniteAndOverflowedNumbers() {
		assertFalse(SettingNumbers.valid(SettingKind.FLOAT, "NaN"));
		assertFalse(SettingNumbers.valid(SettingKind.DOUBLE, "Infinity"));
		assertFalse(SettingNumbers.valid(SettingKind.FLOAT, "1e100"));
		assertFalse(SettingNumbers.valid(SettingKind.DOUBLE, "1e400"));
		assertFalse(SettingNumbers.valid(SettingKind.INTEGER, "2147483648"));
		assertFalse(SettingNumbers.valid(SettingKind.LONG, "9223372036854775808"));
	}

	@Test
	void partialEditsAreNotSubmittedAsSettings() {
		assertFalse(SettingNumbers.valid(SettingKind.INTEGER, "-"));
		assertFalse(SettingNumbers.valid(SettingKind.DOUBLE, "1e"));
		assertFalse(SettingNumbers.valid(SettingKind.FLOAT, ""));
		assertFalse(SettingNumbers.valid(SettingKind.DOUBLE, null));
	}
}
