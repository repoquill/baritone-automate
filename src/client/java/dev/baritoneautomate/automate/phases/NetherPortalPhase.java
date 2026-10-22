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
import java.util.Locale;

/**
 * Require a diamond pick or better, collect a complete supported frame, then
 * place a frame and use an igniter. The phase advances only after the dimension changes.
 */
final class NetherPortalPhase implements PhaseHandler {
	private static final int OBSIDIAN = 14;
	private static final int MAX_IGNITE_ATTEMPTS = 60;
	private static final String NEED_PICK = "need diamond/netherite pickaxe before mining obsidian";
	private final long timeoutMillis;
	private final MineLoop mines = new MineLoop(3);
	private final MineLoop gravel = new MineLoop(3);
	private final PathRequests paths = new PathRequests();
	private int igniteAttempts;
	private boolean miningFlint;
	private boolean building;

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
		gravel.reset();
		igniteAttempts = 0;
		miningFlint = false;
		building = context.inventory().count("obsidian") >= OBSIDIAN || context.world().portalBuildInProgress();
		if (left(context) || building || context.world().findNearbyBlock("nether_portal", 12) != null) {
			return;
		}
		if (Supply.hasPick(context.inventory(), "diamond")) {
			mines.start(context, OBSIDIAN, List.of("obsidian"));
		}
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (left(context)) {
			return PhaseTick.advance("left the overworld");
		}
		BlockPosView lit = context.world().findNearbyBlock("nether_portal", 12);
		if (lit != null) {
			paths.gotoBlock(context, "portal", lit);
			if (PhaseSupport.close(context, lit, 1.5)) {
				return PhaseTick.cont("standing in a lit portal");
			}
			return PhaseTick.cont("pathing into a lit portal");
		}
		if (!building && context.inventory().count("obsidian") < OBSIDIAN) {
			if (!Supply.hasPick(context.inventory(), "diamond")) {
				PhaseTick secured = securePick(context);
				if (!Supply.hasPick(context.inventory(), "diamond")) {
					return secured;
				}
			}
			if (!mines.started()) {
				mines.start(context, OBSIDIAN, List.of("obsidian"));
			}
			return mines.afterShortfall(context, OBSIDIAN, List.of("obsidian"), "mining obsidian");
		}
		building = true;
		if (miningFlint) {
			if (hasIgniter(context) || context.inventory().count("flint") > 0) {
				miningFlint = false;
			} else {
				PhaseTick mined = gravel.afterShortfall(context, 1, List.of("gravel"), "mining gravel for flint");
				if (mined.signal() == dev.baritoneautomate.automate.PhaseSignal.FAIL) {
					return PhaseTick.fail("no flint to craft flint and steel");
				}
				return mined;
			}
		}
		if (igniteAttempts >= MAX_IGNITE_ATTEMPTS) {
			return PhaseTick.fail("ignition did not create a portal");
		}
		stopMine(context);
		ActionResult attempt = context.world().lightNetherPortal();
		if (attempt.status() == ActionResult.Status.PENDING) {
			return PhaseTick.cont(attempt.message());
		}
		if (attempt.status() == ActionResult.Status.OK) {
			igniteAttempts++;
			return PhaseTick.cont(attempt.message());
		}
		if (attempt.status() == ActionResult.Status.MISSING && igniterMissing(attempt.message())) {
			return craftIgniter(context);
		}
		return PhaseTick.fail(attempt.message());
	}

	private PhaseTick securePick(PhaseContext context) {
		if (context.inventory().count("diamond") >= 3) {
			PhaseTick crafted = Supply.craft(context, "diamond_pickaxe");
			if (Supply.hasPick(context.inventory(), "diamond")) {
				return PhaseTick.cont("diamond pick ready");
			}
			if (crafted.signal() == dev.baritoneautomate.automate.PhaseSignal.FAIL) {
				return PhaseTick.fail(NEED_PICK + "; " + crafted.detail());
			}
			return crafted;
		}
		return PhaseTick.fail(NEED_PICK);
	}

	private PhaseTick craftIgniter(PhaseContext context) {
		ActionResult crafted = context.inventory().craft("flint_and_steel", 1);
		if (hasIgniter(context)) {
			return PhaseTick.cont(crafted.message());
		}
		if (crafted.status() == ActionResult.Status.PENDING) {
			return PhaseTick.cont(crafted.message());
		}
		if (crafted.status() == ActionResult.Status.OK) {
			return PhaseTick.fail("flint and steel craft reported success but the inventory did not change");
		}
		if (context.inventory().count("flint") == 0
				&& context.inventory().count("iron_ingot") > 0
				&& crafted.message().toLowerCase(Locale.ROOT).contains("flint")) {
			miningFlint = true;
			gravel.reset();
			gravel.start(context, 1, List.of("gravel"));
			return PhaseTick.cont("mining gravel for flint");
		}
		return PhaseTick.fail(crafted.message());
	}

	private static boolean hasIgniter(PhaseContext context) {
		return context.inventory().count("flint_and_steel") > 0 || context.inventory().count("fire_charge") > 0;
	}

	private static boolean igniterMissing(String message) {
		String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
		return text.contains("flint") || text.contains("fire charge") || text.contains("igniter");
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
