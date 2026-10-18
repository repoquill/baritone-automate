package dev.baritoneautomate.bridge;

/**
 * Point-in-time view of Baritone pathing. Tick fields are Baritone's own estimates.
 */
public record PathingSnapshot(
		boolean baritonePresent,
		boolean pathing,
		boolean paused,
		String processName,
		String goalSummary,
		Double estimatedTicksToGoal,
		Double segmentTicksRemaining,
		String etaNote
) {
	public PathingSnapshot {
		if (processName == null) {
			processName = "";
		}
		if (goalSummary == null) {
			goalSummary = "";
		}
		if (etaNote == null) {
			etaNote = "";
		}
	}

	public static PathingSnapshot absent(String note) {
		return new PathingSnapshot(false, false, false, "", "", null, null, note);
	}
}
