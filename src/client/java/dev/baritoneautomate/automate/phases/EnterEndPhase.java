package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.Dimensions;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.inventory.ActionResult;

/**
 * Fill empty end portal frames with eyes of ender, then stand in the lit portal.
 * Short eyes, a frame that refuses the eye, or a portal that never lights fails outright.
 */
final class EnterEndPhase implements PhaseHandler {
	private static final long TIMEOUT = 3 * 60_000L;
	private static final int STAND_TICKS = 100;
	private static final int EYE_REJECTS = 20;
	private static final int PORTAL_WAIT = 40;
	private static final double FRAME_REACH = 4.5;
	private final PathRequests paths = new PathRequests();
	private int stood;
	private int rejected;
	private int portalWait;

	@Override
	public RunPhase phase() {
		return RunPhase.ENTER_END;
	}

	@Override
	public void onEnter(PhaseContext context) {
		stood = 0;
		rejected = 0;
		portalWait = 0;
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (Dimensions.isEnd(context.world().dimensionId())) {
			return PhaseTick.advance("in the end");
		}
		BlockPosView portal = context.world().findNearbyBlock("end_portal", 16);
		if (portal != null) {
			return standInPortal(context, portal);
		}
		int empty = context.world().emptyEndFrames(16);
		BlockPosView frame = context.world().findEmptyEndFrame(16);
		if (empty > 0 || frame != null) {
			portalWait = 0;
			return fillFrame(context, frame);
		}
		if (context.world().findNearbyBlock("end_portal_frame", 16) != null) {
			portalWait++;
			if (portalWait > PORTAL_WAIT) {
				return PhaseTick.fail("filled the frames but the end portal did not open");
			}
			return PhaseTick.cont("waiting for the end portal");
		}
		return PhaseTick.fail("no end portal within reach");
	}

	private PhaseTick standInPortal(PhaseContext context, BlockPosView portal) {
		paths.gotoBlock(context, "end-portal", portal);
		if (!PhaseSupport.close(context, portal, 1.0)) {
			stood = 0;
			return PhaseTick.cont("pathing into the end portal");
		}
		stood++;
		if (stood >= STAND_TICKS) {
			return PhaseTick.fail("stood in the end portal but the dimension did not change");
		}
		return PhaseTick.cont("waiting to enter the end");
	}

	private PhaseTick fillFrame(PhaseContext context, BlockPosView frame) {
		if (context.inventory().count("ender_eye") <= 0) {
			ActionResult crafted = context.inventory().craft("ender_eye", 1);
			if (crafted.status() == ActionResult.Status.PENDING || context.inventory().count("ender_eye") > 0) {
				return PhaseTick.cont(crafted.message());
			}
			return PhaseTick.fail("not enough ender eyes to fill the portal frames; " + crafted.message());
		}
		if (frame == null || !PhaseSupport.close(context, frame, FRAME_REACH)) {
			if (frame != null) {
				paths.gotoBlock(context, "end-frame", frame);
			}
			return PhaseTick.cont("pathing to an end portal frame");
		}
		int before = context.inventory().count("ender_eye");
		ActionResult filled = context.world().fillEndPortalFrame();
		if (filled.status() == ActionResult.Status.PENDING) {
			return PhaseTick.cont(filled.message());
		}
		if (filled.status() != ActionResult.Status.OK) {
			return PhaseTick.fail(filled.message());
		}
		if (context.inventory().count("ender_eye") >= before) {
			rejected++;
			if (rejected >= EYE_REJECTS) {
				return PhaseTick.fail("ender eye was not accepted by the frame");
			}
			return PhaseTick.cont("placing an eye of ender");
		}
		rejected = 0;
		return PhaseTick.cont("placing an eye of ender");
	}

	@Override
	public long timeoutMillis() {
		return TIMEOUT;
	}
}
