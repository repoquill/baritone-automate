package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.bridge.CustomGoalRequest;
import dev.baritoneautomate.bridge.ProcessKind;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.bridge.ProcessSnapshot;

/** Avoid restarting the same Baritone request on every tick. */
final class PathRequests {
	private String key = "";

	void gotoBlock(PhaseContext context, String key, BlockPosView pos) {
		if (pos == null) {
			return;
		}
		if (sameRunning(context, key, ProcessKind.GOTO)) {
			return;
		}
		this.key = key;
		context.gateway().gotoBlock(pos.x(), pos.y(), pos.z());
	}

	void approach(PhaseContext context, String key, BlockPosView pos) {
		if (pos == null) {
			return;
		}
		if (sameRunning(context, key, ProcessKind.CUSTOM_GOAL)) {
			return;
		}
		this.key = key;
		context.gateway().customGoal(CustomGoalRequest.near(pos.x(), pos.y(), pos.z(), 2));
	}

	void explore(PhaseContext context, int x, int z) {
		ProcessSnapshot process = context.gateway().currentProcess();
		if ("explore".equals(key) && process.kind() == ProcessKind.EXPLORE && process.outcome() == ProcessOutcome.RUNNING) {
			return;
		}
		key = "explore";
		context.gateway().explore(x, z);
	}

	private boolean sameRunning(PhaseContext context, String key, ProcessKind kind) {
		if (!key.equals(this.key)) {
			return false;
		}
		ProcessSnapshot process = context.gateway().currentProcess();
		return process.kind() == kind && process.outcome() == ProcessOutcome.RUNNING;
	}
}
