package dev.baritoneautomate.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatRulesTest {
	@Test
	void crystalsStayOpenUntilEveryRingPointIsChecked() {
		boolean[] checked = new boolean[10];
		assertFalse(CombatRules.ringCleared(false, checked));
		assertFalse(CombatRules.ringCleared(true, checked));
		for (int i = 0; i < checked.length; i++) {
			checked[i] = true;
		}
		assertFalse(CombatRules.ringCleared(true, checked));
		assertTrue(CombatRules.ringCleared(false, checked));
	}

	@Test
	void engagedDragonDespawnCountsAsDefeated() {
		assertNull(CombatRules.engagedDragon(true, true, 0));
		assertEquals(CombatOutcome.DONE, CombatRules.engagedDragon(true, false, 1));
		assertNull(CombatRules.engagedDragon(false, false, 1));
		assertEquals(CombatOutcome.DONE, CombatRules.engagedDragon(false, false, CombatRules.DRAGON_DESPAWN_CONFIRM_TICKS));
	}

	@Test
	void unseenDragonEventuallyFails() {
		assertNull(CombatRules.unseenDragon(10));
		assertEquals(CombatOutcome.FAILED, CombatRules.unseenDragon(CombatRules.DRAGON_SEARCH_TICKS));
	}
}
