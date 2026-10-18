package dev.baritoneautomate.combat;

/**
 * Completion rules that do not touch the client. Crystals are cleared only after
 * every ring sample has been visited with no crystal in sight. A dragon that was
 * already engaged and then disappears is treated as defeated, not as a failed search.
 */
public final class CombatRules {
	public static final int DRAGON_SEARCH_TICKS = 200;
	public static final int DRAGON_DESPAWN_CONFIRM_TICKS = 20;

	private CombatRules() {
	}

	public static boolean ringCleared(boolean crystalVisible, boolean[] checked) {
		if (crystalVisible || checked == null || checked.length == 0) {
			return false;
		}
		for (boolean point : checked) {
			if (!point) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return {@link CombatOutcome#DONE} when the engaged dragon is dead or has been
	 * gone long enough to count as a despawn, otherwise null to keep going
	 */
	public static CombatOutcome engagedDragon(boolean present, boolean alive, int quietTicks) {
		if (alive) {
			return null;
		}
		if (present) {
			return CombatOutcome.DONE;
		}
		if (quietTicks >= DRAGON_DESPAWN_CONFIRM_TICKS) {
			return CombatOutcome.DONE;
		}
		return null;
	}

	/** @return {@link CombatOutcome#FAILED} only when the dragon was never seen */
	public static CombatOutcome unseenDragon(int quietTicks) {
		if (quietTicks >= DRAGON_SEARCH_TICKS) {
			return CombatOutcome.FAILED;
		}
		return null;
	}
}
