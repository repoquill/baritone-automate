package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import java.util.List;

/** Mines diamonds until an iron-or-better pick can craft a diamond pick. */
final class DiamondPhase implements PhaseHandler {
	private static final long TIMEOUT = 10 * 60_000L;
	private final MineLoop mines = new MineLoop(3);

	@Override
	public RunPhase phase() {
		return RunPhase.DIAMOND;
	}

	@Override
	public void onEnter(PhaseContext context) {
		mines.reset();
		if (Supply.hasPick(context.inventory(), "diamond") || context.inventory().count("diamond") >= 3) {
			return;
		}
		mines.start(context, 3, List.of("diamond_ore", "deepslate_diamond_ore"));
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (Supply.hasPick(context.inventory(), "diamond")) {
			return PhaseTick.advance("diamond pick ready");
		}
		if (context.inventory().count("diamond") >= 3) {
			PhaseTick crafted = Supply.craft(context, "diamond_pickaxe");
			if (crafted.signal() != dev.baritoneautomate.automate.PhaseSignal.FAIL
					&& Supply.hasPick(context.inventory(), "diamond")) {
				return PhaseTick.advance("diamond pick ready");
			}
			return crafted;
		}
		return mines.afterShortfall(context, 3, List.of("diamond_ore", "deepslate_diamond_ore"), "mining diamonds");
	}

	@Override
	public long timeoutMillis() {
		return TIMEOUT;
	}
}
