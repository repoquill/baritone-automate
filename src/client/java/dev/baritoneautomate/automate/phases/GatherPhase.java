package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import java.util.List;
import java.util.function.Predicate;

/** Mine until {@code ready} is true. A finished mine process only triggers another dig. */
final class GatherPhase implements PhaseHandler {
	private final RunPhase id;
	private final int quantity;
	private final List<String> blockIds;
	private final Predicate<PhaseContext> ready;
	private final String progress;
	private final String finished;
	private final long timeout;
	private final MineLoop mines = new MineLoop(3);

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
		mines.reset();
		if (ready.test(context)) {
			return;
		}
		mines.start(context, quantity, blockIds);
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (ready.test(context)) {
			return PhaseTick.advance(finished);
		}
		return mines.afterShortfall(context, quantity, blockIds, progress);
	}

	@Override
	public long timeoutMillis() {
		return timeout;
	}
}
