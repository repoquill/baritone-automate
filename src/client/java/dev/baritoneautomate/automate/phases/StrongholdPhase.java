package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.Dimensions;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.inventory.ActionResult;

/**
 * Throw eyes of ender and walk toward them until an end portal frame is in reach.
 * Crafting an eye is attempted once; if that cannot run, the phase fails instead of waiting.
 */
final class StrongholdPhase implements PhaseHandler {
	private static final long TIMEOUT = 8 * 60_000L;
	private static final long THROW_GAP_MILLIS = 4_000L;
	private final PathRequests paths = new PathRequests();
	private long lastThrowAt = Long.MIN_VALUE;
	private boolean craftAttempted;

	@Override
	public RunPhase phase() {
		return RunPhase.STRONGHOLD;
	}

	@Override
	public void onEnter(PhaseContext context) {
		lastThrowAt = Long.MIN_VALUE;
		craftAttempted = false;
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (Dimensions.isEnd(context.world().dimensionId())) {
			return PhaseTick.advance("already in the end");
		}
		BlockPosView frame = PhaseSupport.firstBlock(context, 16, "end_portal_frame", "end_portal");
		if (frame != null) {
			if (PhaseSupport.close(context, frame, 6.0)) {
				return PhaseTick.advance("reached the stronghold");
			}
			paths.gotoBlock(context, "frame", frame);
			return PhaseTick.cont("pathing to the stronghold");
		}
		if (context.inventory().count("ender_eye") <= 0) {
			if (!craftAttempted) {
				craftAttempted = true;
				ActionResult crafted = context.inventory().craft("ender_eye", 1);
				if (context.inventory().count("ender_eye") > 0) {
					return PhaseTick.cont(crafted.message());
				}
				return PhaseTick.fail("no ender eyes to throw; " + crafted.message());
			}
			return PhaseTick.fail("no ender eyes to throw");
		}
		BlockPosView eye = context.world().findNearbyEntity("minecraft:eye_of_ender", 64.0);
		if (eye != null) {
			paths.approach(context, "eye", eye);
			return PhaseTick.cont("following an eye of ender");
		}
		if (context.nowMillis() - lastThrowAt < THROW_GAP_MILLIS) {
			return PhaseTick.cont("waiting to throw another eye");
		}
		ActionResult thrown = context.world().useItem("ender_eye");
		lastThrowAt = context.nowMillis();
		if (thrown.status() != ActionResult.Status.OK) {
			return PhaseTick.fail(thrown.message());
		}
		return PhaseTick.cont("throwing an eye of ender");
	}

	@Override
	public long timeoutMillis() {
		return TIMEOUT;
	}
}
