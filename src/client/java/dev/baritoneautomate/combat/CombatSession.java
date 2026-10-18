package dev.baritoneautomate.combat;

import java.util.function.Consumer;

/**
 * Crystal break and a simple dragon fight. Pathing stays on the gateway;
 * this type only deals damage and asks the gateway to walk closer.
 */
public interface CombatSession {
	void startCrystals();

	void startDragon();

	void cancel();

	void tick();

	CombatSnapshot snapshot();

	/** Fired once when health drops to the pause threshold. The run should pause, not fail. */
	void setLowHealthHook(Consumer<Float> hook);

	void setPaused(boolean paused);
}
