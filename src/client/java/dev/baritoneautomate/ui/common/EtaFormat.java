package dev.baritoneautomate.ui.common;

import dev.baritoneautomate.bridge.PathingSnapshot;

/**
 * Turns Baritone's estimated ticks into a short clock. Twenty ticks are one second.
 * The caller is responsible for the 「约」 / "about" label — this class only formats the duration.
 */
public final class EtaFormat {
	private EtaFormat() {
	}

	public static String duration(Double ticks) {
		if (ticks == null || ticks.isNaN() || ticks.isInfinite()) {
			return "—";
		}
		long seconds = Math.max(0L, Math.round(ticks / 20.0));
		long minutes = seconds / 60L;
		long rest = seconds % 60L;
		if (minutes > 0L) {
			return minutes + ":" + (rest < 10L ? "0" : "") + rest;
		}
		return seconds + "s";
	}

	/** Prefer the goal estimate, then the current segment. */
	public static String duration(PathingSnapshot snapshot) {
		if (snapshot == null) {
			return "—";
		}
		Double ticks = snapshot.estimatedTicksToGoal();
		if (ticks == null) {
			ticks = snapshot.segmentTicksRemaining();
		}
		return duration(ticks);
	}
}
