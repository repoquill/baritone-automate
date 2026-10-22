package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.Dimensions;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.inventory.ActionResult;

/**
 * Blaze rods, then ender pearls. Hunts with follow-style pathing and a client attack.
 * Outside the nether it looks for a lit portal once, then fails instead of waiting.
 */
final class HuntPhase implements PhaseHandler {
	private static final long TIMEOUT = 8 * 60_000L;
	private final PathRequests paths = new PathRequests();

	@Override
	public RunPhase phase() {
		return RunPhase.BLAZE_PEARL;
	}

	@Override
	public void onEnter(PhaseContext context) {
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (Dimensions.isEnd(context.world().dimensionId())) {
			return PhaseTick.advance("already in the end");
		}
		if (ready(context)) {
			return PhaseTick.advance("blaze rods and ender pearls ready");
		}
		if (!Dimensions.isNether(context.world().dimensionId())) {
			BlockPosView portal = context.world().findNearbyBlock("nether_portal", 12);
			if (portal == null) {
				return PhaseTick.fail("blaze phase needs the nether, and no lit portal is nearby");
			}
			paths.gotoBlock(context, "portal", portal);
			if (PhaseSupport.close(context, portal, 1.5)) {
				return PhaseTick.cont("standing in a lit portal");
			}
			return PhaseTick.cont("pathing into a lit portal");
		}
		if (context.inventory().count("blaze_rod") < 6) {
			return hunt(context, "minecraft:blaze", "searching for a blaze");
		}
		return hunt(context, "minecraft:enderman", "searching for an enderman");
	}

	@Override
	public long timeoutMillis() {
		return TIMEOUT;
	}

	private PhaseTick hunt(PhaseContext context, String entityId, String searching) {
		BlockPosView entity = context.world().findNearbyEntity(entityId, 48.0);
		if (entity != null) {
			if (PhaseSupport.close(context, entity, 2.5)) {
				context.gateway().cancel();
				ActionResult attack = context.world().attackNearest(entityId, 6.0);
				String detail = attack.status() == ActionResult.Status.OK ? "fighting " + entityId : attack.message();
				return PhaseTick.cont(detail);
			}
			paths.approach(context, entityId, entity);
			return PhaseTick.cont("pathing to " + entityId);
		}
		BlockPosView feet = context.world().blockPos();
		if (feet != null) {
			paths.explore(context, feet.x(), feet.z());
		}
		return PhaseTick.cont(searching);
	}

	private static boolean ready(PhaseContext context) {
		return context.inventory().count("blaze_rod") >= 6 && context.inventory().count("ender_pearl") >= 12;
	}
}
