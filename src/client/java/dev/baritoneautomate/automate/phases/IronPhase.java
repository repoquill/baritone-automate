package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseSignal;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.inventory.InventoryAccess;
import java.util.List;

/**
 * Gets a pick that can mine iron, smelts raw iron, then crafts an iron pick.
 * A finished mine process is not enough; the ingots have to be in the inventory.
 */
final class IronPhase implements PhaseHandler {
	private static final long TIMEOUT = 8 * 60_000L;
	private static final List<String> COBBLE_BLOCKS = List.of("stone", "deepslate", "cobblestone", "cobbled_deepslate");
	private static final List<String> COAL_BLOCKS = List.of("coal_ore", "deepslate_coal_ore");
	private static final int FURNACE_COBBLE = 8;
	private static final int STATION_RADIUS = 6;
	private final MineLoop iron = new MineLoop(3);
	private final MineLoop cobble = new MineLoop(3);
	private final MineLoop wood = new MineLoop(3);
	private final MineLoop coal = new MineLoop(3);

	@Override
	public RunPhase phase() {
		return RunPhase.IRON;
	}

	@Override
	public void onEnter(PhaseContext context) {
		iron.reset();
		cobble.reset();
		wood.reset();
		coal.reset();
		if (Supply.hasPick(context.inventory(), "iron")) {
			return;
		}
		if (!Supply.hasPick(context.inventory(), "stone")
				&& context.inventory().count("cobblestone") < 3
				&& context.inventory().count("cobbled_deepslate") < 3
				&& Supply.hasPick(context.inventory(), "wooden")) {
			cobble.start(context, 3, COBBLE_BLOCKS);
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
			if (Supply.previewWoodenPick(context.inventory())) {
				return Supply.craft(context, "wooden_pickaxe", true);
			}
			if (!wood.started()) {
				wood.start(context, 4, Supply.logBlocks());
			}
			return wood.afterShortfall(context, 4, Supply.logBlocks(), "gathering logs for a wooden pickaxe");
		}
		if (!Supply.hasPick(context.inventory(), "stone")) {
			int loose = context.inventory().count("cobblestone") + context.inventory().count("cobbled_deepslate");
			if (loose < 3) {
				return cobble.afterShortfall(context, 3, COBBLE_BLOCKS, "mining cobblestone");
			}
			return Supply.craft(context, "stone_pickaxe", true);
		}
		int ingots = context.inventory().count("iron_ingot");
		if (ingots < 3) {
			if (context.inventory().count("raw_iron") + ingots < 3) {
				return iron.afterShortfall(context, 3, List.of("iron_ore", "deepslate_iron_ore"), "mining iron");
			}
			int smeltCount = 3 - ingots;
			if (!furnaceReady(context)) {
				int loose = looseCobble(context.inventory());
				if (loose < FURNACE_COBBLE) {
					return cobble.afterShortfall(
							context,
							FURNACE_COBBLE - loose,
							COBBLE_BLOCKS,
							"mining cobblestone for a furnace");
				}
			}
			PhaseTick fuel = ensureSmeltFuel(context, smeltCount);
			if (fuel != null) {
				return fuel;
			}
			return Supply.smelt(context, "raw_iron", smeltCount, true);
		}
		PhaseTick crafted = Supply.craft(context, "iron_pickaxe", true);
		if (crafted.signal() != PhaseSignal.FAIL
				&& Supply.hasPick(context.inventory(), "iron")) {
			return PhaseTick.advance("iron pick ready");
		}
		return crafted;
	}

	@Override
	public long timeoutMillis() {
		return TIMEOUT;
	}

	private static boolean furnaceReady(PhaseContext context) {
		if (context.inventory().count("furnace") > 0) {
			return true;
		}
		BlockPosView nearby = context.world().findNearbyBlock("furnace", STATION_RADIUS);
		return nearby != null;
	}

	private static int looseCobble(InventoryAccess inventory) {
		return inventory.count("cobblestone") + inventory.count("cobbled_deepslate");
	}

	private PhaseTick ensureSmeltFuel(PhaseContext context, int smeltItems) {
		if (Supply.hasSmeltFuel(context.inventory(), smeltItems)) {
			return null;
		}
		InventoryAccess inventory = context.inventory();
		for (String log : Supply.logBlocks()) {
			if (inventory.count(log) > 0) {
				String planks = log.replace("_log", "_planks");
				return Supply.craft(context, planks, true);
			}
		}
		if (inventory.countWhere(id -> dev.baritoneautomate.inventory.CraftRecipes.matches(id, "planks")) > 0) {
			return Supply.craft(context, "stick", true);
		}
		return coal.afterShortfall(context, 1, COAL_BLOCKS, "gathering coal for smelting");
	}
}
