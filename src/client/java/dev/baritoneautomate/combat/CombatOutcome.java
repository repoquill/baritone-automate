package dev.baritoneautomate.combat;

public enum CombatOutcome {
	IDLE,
	RUNNING,
	/** End crystals are gone. */
	CLEARED,
	/** Dragon is dead. */
	DONE,
	FAILED,
	CANCELLED
}
