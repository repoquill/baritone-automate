package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import java.util.List;

/**
 * Gets a pick that can mine iron, smelts raw iron, then crafts an iron pick.
 * A finished mine process is not enough; the ingots have to be in the inventory.
 */
final class IronPhase implements PhaseHandler {
	private static final long TIMEOUT = 8 * 60_000L;
	private final MineLoop iron = new MineLoop(3);
	private final MineLoop cobble = new MineLoop(3);

	@Override
	public RunPhase phase() {
		return RunPhase.IRON;
	}

	@Override
	public void onEnter(PhaseContext context) {
		iron.reset();
		cobble.reset();
		if (Supply.hasPick(context.inventory(), "iron")) {
			return;
		}
		if (!Supply.hasPick(context.inventory(), "stone")
				&& context.inventory().count("cobblestone") < 3
				&& context.inventory().count("cobbled_deepslate") < 3
				&& Supply.hasPick(context.inventory(), "wooden")) {
			cobble.start(context, 3, List.of("cobblestone", "cobbled_deepslate"));
			return;
		}
		if (Supply.hasPick(context.inventory(), "stone")
				&& context.inventory().count("raw_iron") + context.inventory().count("iron_ingot") < 3) {
			iron.start(context, 3, List.of("iron_ore", "deepslate_iron_ore"));
		}
	}

	@Override
	public PhaseTick onTick(PhaseContext context) {
		if (Supply.hasPick(context.inventory(), "iron")) {
			return PhaseTick.advance("iron pick ready");
		}
		if (!Supply.hasPick(context.inventory(), "wooden")) {
			return Supply.craft(context, "wooden_pickaxe");
		}
		if (!Supply.hasPick(context.inventory(), "stone")) {
			int loose = context.inventory().count("cobblestone") + context.inventory().count("cobbled_deepslate");
			if (loose < 3) {
				return cobble.afterShortfall(context, 3, List.of("cobblestone", "cobbled_deepslate"), "mining cobblestone");
			}
			return Supply.craft(context, "stone_pickaxe");
		}
		int ingots = context.inventory().count("iron_ingot");
		if (ingots < 3) {
			if (context.inventory().count("raw_iron") <= 0) {
				return iron.afterShortfall(context, 3, List.of("iron_ore", "deepslate_iron_ore"), "mining iron");
			}
			return Supply.smelt(context, "raw_iron", 3 - ingots);
		}
		PhaseTick crafted = Supply.craft(context, "iron_pickaxe");
		if (crafted.signal() != dev.baritoneautomate.automate.PhaseSignal.FAIL
				&& Supply.hasPick(context.inventory(), "iron")) {
			return PhaseTick.advance("iron pick ready");
		}
		return crafted;
	}

	@Override
	public long timeoutMillis() {
		return TIMEOUT;
	}
}
