package dev.baritoneautomate.automate;

public interface PhaseHandler {
	RunPhase phase();

	void onEnter(PhaseContext context);

	PhaseTick onTick(PhaseContext context);

	/** P0 allows skipping a phase so a stuck demo can be nudged forward. */
	default boolean skippable() {
		return true;
	}

	long timeoutMillis();
}
