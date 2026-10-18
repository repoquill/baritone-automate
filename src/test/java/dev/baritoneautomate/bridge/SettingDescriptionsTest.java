package dev.baritoneautomate.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingDescriptionsTest {
	@Test
	void knownSettingsUseTheBaritoneComment() {
		String allowBreak = SettingDescriptions.of("allowBreak");
		assertTrue(allowBreak.toLowerCase().contains("break"));
		assertFalse(allowBreak.isBlank());
		String sprint = SettingDescriptions.of("allowSprint");
		assertTrue(sprint.toLowerCase().contains("sprint"));
		String explore = SettingDescriptions.of("exploreMaintainY");
		assertTrue(explore.toLowerCase().contains("exploring"));
	}

	@Test
	void unknownNamesAreSplitInsteadOfLeftBlank() {
		String text = SettingDescriptions.of("farmUsingSelection");
		assertFalse(text.isBlank());
		assertTrue(text.contains(" "));
		assertTrue(text.contains("farm") || text.contains("Farm"));
		assertTrue(text.contains("Selection") || text.contains("selection"));
	}
}
