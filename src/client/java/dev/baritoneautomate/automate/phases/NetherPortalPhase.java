package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.Dimensions;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.bridge.ProcessKind;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.inventory.ActionResult;
import java.util.List;

/**
 * Mine obsidian until the inventory actually has ten, then place a frame and use
 * an igniter. The phase advances only after the dimension changes.
 */
final class NetherPortalPhase implements PhaseHandler {
	private static final int OBSIDIAN = 10;
	private static final int MAX_IGNITE_ATTEMPTS = 60;
	private final long timeoutMillis;
	private final MineLoop mines = new MineLoop(3);
	private final PathRequests paths = new PathRequests();
	private int igniteAttempts;

	NetherPortalPhase(long timeoutMillis) {
		this.timeoutMillis = timeoutMillis;
	}

	@Override
	public RunPhase phase() {
		return RunPhase.NETHER_PORTAL;
	}

	@Override
	public void onEnter(PhaseContext context) {
		mines.reset();
		igniteAttempts = 0;
		if (left(context) || context.inventory().count("obsidian") >= OBSIDIAN) {
			return;
		}
		mines.start(context, OBSIDIAN, List.of("obsidian"));
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (left(context)) {
			return PhaseTick.advance("left the overworld");
		}
		BlockPosView lit = context.world().findNearbyBlock("nether_portal", 12);
		if (lit != null) {
			if (PhaseSupport.close(context, lit, 1.5)) {
				return PhaseTick.cont("standing in a lit portal");
			}
			paths.gotoBlock(context, "portal", lit);
			return PhaseTick.cont("pathing into a lit portal");
		}
		if (context.inventory().count("obsidian") < OBSIDIAN) {
			return mines.afterShortfall(context, OBSIDIAN, List.of("obsidian"), "mining obsidian");
		}
		stopMine(context);
		if (igniteAttempts >= MAX_IGNITE_ATTEMPTS) {
			return PhaseTick.fail("ignition did not create a portal");
		}
		igniteAttempts++;
		ActionResult attempt = context.world().lightNetherPortal();
		if (attempt.status() == ActionResult.Status.MISSING || attempt.status() == ActionResult.Status.UNAVAILABLE) {
			return PhaseTick.fail(attempt.message());
		}
		return PhaseTick.cont(attempt.message());
	}

	@Override
	public long timeoutMillis() {
		return timeoutMillis;
	}

	private static boolean left(PhaseContext context) {
		String dimension = context.world().dimensionId();
		return Dimensions.isNether(dimension) || Dimensions.isEnd(dimension);
	}

	private static void stopMine(PhaseContext context) {
		var process = context.gateway().currentProcess();
		if (process.kind() == ProcessKind.MINE && process.outcome() == ProcessOutcome.RUNNING) {
			context.gateway().cancel();
		}
	}
}
