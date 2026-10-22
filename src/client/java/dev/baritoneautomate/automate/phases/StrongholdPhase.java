package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.Dimensions;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.inventory.ActionResult;

/**
 * Craft eyes if needed, throw them, follow the eye, then explore toward the landing.
 * A missing frame is not an immediate failure; eight throws with no frame fails outright.
 */
final class StrongholdPhase implements PhaseHandler {
	private static final long TIMEOUT = 8 * 60_000L;
	private static final long THROW_GAP_MILLIS = 4_000L;
	private static final int MAX_THROWS = 8;
	private final PathRequests paths = new PathRequests();
	private long lastThrowAt = Long.MIN_VALUE;
	private int throwsSoFar;
	private BlockPosView lastEye;

	@Override
	public RunPhase phase() {
		return RunPhase.STRONGHOLD;
	}

	@Override
	public void onEnter(PhaseContext context) {
		lastThrowAt = Long.MIN_VALUE;
		throwsSoFar = 0;
		lastEye = null;
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (Dimensions.isEnd(context.world().dimensionId())) {
			return PhaseTick.advance("already in the end");
		}
		if (Dimensions.isNether(context.world().dimensionId())) {
			BlockPosView portal = context.world().findNearbyBlock("nether_portal", 16);
			if (portal == null) {
				return PhaseTick.fail("return to the overworld through a nether portal before throwing ender eyes");
			}
			paths.gotoBlock(context, "return-portal", portal);
			return PhaseTick.cont("returning to the overworld");
		}
		BlockPosView frame = PhaseSupport.firstBlock(context, 16, "end_portal_frame", "end_portal");
		if (frame != null) {
			if (PhaseSupport.close(context, frame, 6.0)) {
				return PhaseTick.advance("reached the stronghold");
			}
			paths.gotoBlock(context, "frame", frame);
			return PhaseTick.cont("pathing to the stronghold");
		}
		BlockPosView eye = context.world().findNearbyEntity("minecraft:eye_of_ender", 64.0);
		if (eye != null) {
			lastEye = eye;
			paths.approach(context, "eye", eye);
			return PhaseTick.cont("following an eye of ender");
		}
		if (lastThrowAt != Long.MIN_VALUE && context.nowMillis() - lastThrowAt < THROW_GAP_MILLIS) {
			return search(context);
		}
		if (throwsSoFar >= MAX_THROWS) {
			return PhaseTick.fail("threw eyes of ender but no end portal frame was found");
		}
		if (context.inventory().count("ender_eye") <= 0) {
			int before = 0;
			ActionResult crafted = context.inventory().craft("ender_eye", 1);
			if (context.inventory().count("ender_eye") > before) {
				return PhaseTick.cont(crafted.message());
			}
			if (crafted.status() == ActionResult.Status.PENDING) {
				return PhaseTick.cont(crafted.message());
			}
			if (crafted.status() == ActionResult.Status.OK) {
				return PhaseTick.fail("no ender eyes to throw; craft reported success but the inventory did not change");
			}
			return PhaseTick.fail("no ender eyes to throw; " + crafted.message());
		}
		ActionResult thrown = context.world().useItem("ender_eye");
		if (thrown.status() == ActionResult.Status.PENDING) {
			return PhaseTick.cont(thrown.message());
		}
		if (thrown.status() != ActionResult.Status.OK) {
			return PhaseTick.fail(thrown.message());
		}
		lastThrowAt = context.nowMillis();
		throwsSoFar++;
		return PhaseTick.cont("throwing an eye of ender");
	}

	private PhaseTick search(PhaseContext context) {
		if (lastEye != null && !PhaseSupport.close(context, lastEye, 8.0)) {
			paths.approach(context, "eye-landing", lastEye);
			return PhaseTick.cont("walking toward where the eye landed");
		}
		BlockPosView feet = context.world().blockPos();
		if (feet != null) {
			int spread = 32 + throwsSoFar * 16;
			paths.explore(context, feet.x() + spread, feet.z());
		}
		return PhaseTick.cont("searching for the stronghold");
	}

	@Override
	public long timeoutMillis() {
		return TIMEOUT;
	}
}
