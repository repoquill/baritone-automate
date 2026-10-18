package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.bridge.GatewayStatus;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.bridge.ProcessSnapshot;
import java.util.List;
import java.util.function.Predicate;

/** Mine a block list, then advance when inventory is ready or that mine process finishes. */
final class GatherPhase implements PhaseHandler {
	private final RunPhase id;
	private final int quantity;
	private final List<String> blockIds;
	private final Predicate<PhaseContext> ready;
	private final String progress;
	private final String finished;
	private final long timeout;
	private String error;
	private long trackedGeneration = -1L;

	GatherPhase(
			RunPhase id,
			int quantity,
			List<String> blockIds,
			Predicate<PhaseContext> ready,
			String progress,
			String finished,
			long timeoutMillis
	) {
		this.id = id;
		this.quantity = quantity;
		this.blockIds = List.copyOf(blockIds);
		this.ready = ready;
		this.progress = progress;
		this.finished = finished;
		this.timeout = timeoutMillis;
	}

	@Override
	public RunPhase phase() {
		return id;
	}

	@Override
	public void onEnter(PhaseContext context) {
		error = null;
		trackedGeneration = -1L;
		if (ready.test(context)) {
			return;
		}
		GatewayStatus status = context.gateway().mine(quantity, blockIds);
		if (!status.ok()) {
			error = status.message();
			return;
		}
		trackedGeneration = context.gateway().currentProcess().generation();
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (error != null) {
			return PhaseTick.fail(error);
		}
		if (ready.test(context)) {
			return PhaseTick.advance(finished);
		}
		ProcessSnapshot process = context.gateway().currentProcess();
		if (process.generation() == trackedGeneration && trackedGeneration >= 0L) {
			if (process.outcome() == ProcessOutcome.SUCCEEDED) {
				return PhaseTick.advance(finished);
			}
			if (process.outcome() == ProcessOutcome.FAILED) {
				String why = process.detail().isBlank() ? "mine failed" : process.detail();
				return PhaseTick.fail(why);
			}
		}
		return PhaseTick.cont(progress);
	}

	@Override
	public long timeoutMillis() {
		return timeout;
	}
}
