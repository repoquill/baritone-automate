package dev.baritoneautomate.combat;

/**
 * @param approach {@code search}, {@code path}, {@code melee}, {@code bow}, or empty
 */
public record CombatSnapshot(CombatMode mode, CombatOutcome outcome, String detail, String approach) {
	public CombatSnapshot {
		if (detail == null) {
			detail = "";
		}
		if (approach == null) {
			approach = "";
		}
	}

	public static CombatSnapshot idle() {
		return new CombatSnapshot(CombatMode.IDLE, CombatOutcome.IDLE, "", "");
	}
}
