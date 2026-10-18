package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.Dimensions;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;

/** Walk into a located end portal. Missing portal or a portal that never trips fails outright. */
final class EnterEndPhase implements PhaseHandler {
	private static final long TIMEOUT = 3 * 60_000L;
	private static final int STAND_TICKS = 100;
	private final PathRequests paths = new PathRequests();
	private int stood;

	@Override
	public RunPhase phase() {
		return RunPhase.ENTER_END;
	}

	@Override
	public void onEnter(PhaseContext context) {
		stood = 0;
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (Dimensions.isEnd(context.world().dimensionId())) {
			return PhaseTick.advance("in the end");
		}
		BlockPosView portal = context.world().findNearbyBlock("end_portal", 16);
		if (portal == null) {
			return PhaseTick.fail("no end portal within reach");
		}
		if (!PhaseSupport.close(context, portal, 2.5)) {
			stood = 0;
			paths.gotoBlock(context, "end-portal", portal);
			return PhaseTick.cont("pathing into the end portal");
		}
		stood++;
		if (stood >= STAND_TICKS) {
			return PhaseTick.fail("stood in the end portal but the dimension did not change");
		}
		return PhaseTick.cont("waiting to enter the end");
	}

	@Override
	public long timeoutMillis() {
		return TIMEOUT;
	}
}
