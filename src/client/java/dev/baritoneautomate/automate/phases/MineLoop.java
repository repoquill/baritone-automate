package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.bridge.GatewayStatus;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.bridge.ProcessSnapshot;
import java.util.List;

/**
 * Issues {@code mine} and, when that process ends with the inventory still short,
 * mines again. A finished process is not success by itself.
 */
final class MineLoop {
	private final int maxRetries;
	private int retries;
	private long tracked = -1L;
	private String error;

	MineLoop(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	void reset() {
		retries = 0;
		tracked = -1L;
		error = null;
	}

	boolean started() {
		return tracked >= 0L || error != null;
	}

	void start(PhaseContext context, int quantity, List<String> blockIds) {
		error = null;
		tracked = -1L;
		GatewayStatus status = context.gateway().mine(quantity, blockIds);
		if (!status.ok()) {
			error = status.message();
			return;
		}
		tracked = context.gateway().currentProcess().generation();
	}

	PhaseTick afterShortfall(PhaseContext context, int quantity, List<String> blockIds, String progress) {
		if (error != null) {
			return PhaseTick.fail(error);
		}
		ProcessSnapshot process = context.gateway().currentProcess();
		if (tracked < 0L || process.generation() != tracked) {
			start(context, quantity, blockIds);
			if (error != null) {
				return PhaseTick.fail(error);
			}
			return PhaseTick.cont(progress);
		}
		if (process.outcome() == ProcessOutcome.RUNNING || process.outcome() == ProcessOutcome.IDLE) {
			return PhaseTick.cont(progress);
		}
		if (retries >= maxRetries) {
			return PhaseTick.fail("mine finished but inventory is still short");
		}
		retries++;
		start(context, quantity, blockIds);
		if (error != null) {
			return PhaseTick.fail(error);
		}
		return PhaseTick.cont(progress + "; mining again");
	}
}
