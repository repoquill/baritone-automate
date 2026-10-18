package dev.baritoneautomate.automate;

public record RunSnapshot(
		RunState state,
		RunPhase phase,
		int attempt,
		String detail,
		String failureReason,
		long phaseElapsedMillis,
		boolean skipDiamond
) {
}
