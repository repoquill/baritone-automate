package dev.baritoneautomate.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SettingEnumsTest {
	@Test
	void enumClassListsConstantNames() {
		assertTrue(SettingEnums.names(TimeUnit.class).contains("SECONDS"));
	}

	@Test
	void nonEnumsAreEmpty() {
		assertEquals(0, SettingEnums.names(String.class).size());
		assertEquals(0, SettingEnums.names(null).size());
	}
}
