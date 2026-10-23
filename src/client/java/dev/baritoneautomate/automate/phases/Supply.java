package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.bridge.ProcessKind;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.inventory.ActionResult;
import dev.baritoneautomate.inventory.CraftRecipes;
import dev.baritoneautomate.inventory.InventoryAccess;
import dev.baritoneautomate.inventory.ItemIds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns craft and smelt results into a phase tick. OK counts only when the items appear. */
final class Supply {
	private static final List<String> PICKS = List.of(
			"wooden_pickaxe",
			"stone_pickaxe",
			"iron_pickaxe",
			"diamond_pickaxe",
			"netherite_pickaxe");
	private static final List<String> LOG_BLOCKS = List.of(
			"oak_log",
			"spruce_log",
			"birch_log",
			"jungle_log",
			"acacia_log",
			"dark_oak_log",
			"mangrove_log",
			"cherry_log",
			"pale_oak_log",
			"poplar_log");
	private static final List<String> STEMS = List.of("crimson_stem", "warped_stem");
	private static final List<String> PLANKS = List.of(
			"oak_planks",
			"spruce_planks",
			"birch_planks",
			"jungle_planks",
			"acacia_planks",
			"dark_oak_planks",
			"mangrove_planks",
			"cherry_planks",
			"pale_oak_planks",
			"poplar_planks",
			"bamboo_planks",
			"crimson_planks",
			"warped_planks");

	private Supply() {
	}

	static boolean hasPick(InventoryAccess inventory, String minimum) {
		int floor = 0;
		for (int index = 0; index < PICKS.size(); index++) {
			String pick = PICKS.get(index);
			if (pick.equals(minimum) || pick.startsWith(minimum + "_")) {
				floor = index;
				break;
			}
		}
		for (int index = floor; index < PICKS.size(); index++) {
			if (inventory.count(PICKS.get(index)) > 0) {
				return true;
			}
		}
		return false;
	}

	static List<String> logBlocks() {
		return LOG_BLOCKS;
	}

	/**
	 * Wood may advance when a wooden pick is already held, when at least four logs
	 * can craft one, or when planks, sticks, and a table already close that chain.
	 * Three logs can craft a pick on paper, but that pile stays in early wood.
	 */
	static boolean woodenPickReady(InventoryAccess inventory) {
		if (hasPick(inventory, "wooden")) {
			return true;
		}
		if (!previewWoodenPick(inventory)) {
			return false;
		}
		if (logCount(inventory) >= 4) {
			return true;
		}
		return previewWoodenPick(inventory, false);
	}

	static boolean previewWoodenPick(InventoryAccess inventory) {
		return previewWoodenPick(inventory, true);
	}

	static PhaseTick craft(PhaseContext context, String itemId) {
		return craft(context, itemId, false);
	}

	/**
	 * @param shortageContinues MISSING and UNAVAILABLE keep the run going.
	 * A reported success that does not change the inventory still fails.
	 */
	static PhaseTick craft(PhaseContext context, String itemId, boolean shortageContinues) {
		stopMine(context);
		int before = context.inventory().count(itemId);
		ActionResult result = context.inventory().craft(itemId, 1);
		if (result.status() == ActionResult.Status.PENDING) {
			return PhaseTick.cont(result.message());
		}
		if (result.status() == ActionResult.Status.OK && context.inventory().count(itemId) > before) {
			return PhaseTick.cont(result.message());
		}
		if (result.status() == ActionResult.Status.OK) {
			return PhaseTick.fail(itemId + " craft reported success but the inventory did not change");
		}
		if (shortageContinues && recoverableShortage(result.status())) {
			return PhaseTick.cont(result.message());
		}
		return PhaseTick.fail(result.message());
	}

	static PhaseTick smelt(PhaseContext context, String inputId, int count) {
		return smelt(context, inputId, count, false);
	}

	/**
	 * @param shortageContinues MISSING and UNAVAILABLE keep the run going so the phase can gather fuel or retry.
	 */
	static PhaseTick smelt(PhaseContext context, String inputId, int count, boolean shortageContinues) {
		stopMine(context);
		int before = context.inventory().count("iron_ingot");
		ActionResult result = context.inventory().smelt(inputId, count);
		if (result.status() == ActionResult.Status.PENDING) {
			return PhaseTick.cont(result.message());
		}
		if (result.status() == ActionResult.Status.OK && context.inventory().count("iron_ingot") > before) {
			return PhaseTick.cont(result.message());
		}
		if (result.status() == ActionResult.Status.OK) {
			return PhaseTick.fail("smelt reported success but no iron ingot was added");
		}
		if (shortageContinues && recoverableShortage(result.status())) {
			return PhaseTick.cont(result.message());
		}
		return PhaseTick.fail(result.message());
	}

	static long smeltFuelCapacity(InventoryAccess inventory) {
		long total = inventory.count("coal") * 16L + inventory.count("charcoal") * 16L + inventory.count("stick");
		total += inventory.countWhere(id -> CraftRecipes.matches(id, "fuel_planks")) * 3L;
		total += inventory.countWhere(id -> CraftRecipes.matches(id, "fuel_log")) * 3L;
		return total;
	}

	static boolean hasSmeltFuel(InventoryAccess inventory, int smeltItems) {
		return smeltFuelCapacity(inventory) >= (long) smeltItems * 2L;
	}

	private static boolean previewWoodenPick(InventoryAccess inventory, boolean includeLogs) {
		Map<String, Integer> counts = new HashMap<>();
		if (includeLogs) {
			for (String log : LOG_BLOCKS) {
				copyWoodCounts(inventory, counts, log, log.replace("_log", "_wood"));
			}
			for (String stem : STEMS) {
				copyWoodCounts(inventory, counts, stem, stem.replace("_stem", "_hyphae"));
			}
			copyCount(inventory, counts, "bamboo_block");
		}
		copyCounts(inventory, counts, PLANKS);
		copyCount(inventory, counts, "stick");
		copyCount(inventory, counts, "crafting_table");
		boolean table = inventory.count("crafting_table") > 0;
		ActionResult result = CraftRecipes.craft(counts, "wooden_pickaxe", 1, table);
		return result.status() == ActionResult.Status.OK;
	}

	private static int logCount(InventoryAccess inventory) {
		return inventory.countWhere(id -> CraftRecipes.matches(id, "log") || ItemIds.matches(id, "bamboo_block"));
	}

	private static void copyWoodCounts(InventoryAccess inventory, Map<String, Integer> counts, String log, String wood) {
		copyCount(inventory, counts, log);
		copyCount(inventory, counts, wood);
		copyCount(inventory, counts, "stripped_" + log);
		copyCount(inventory, counts, "stripped_" + wood);
	}

	private static void copyCounts(InventoryAccess inventory, Map<String, Integer> counts, List<String> ids) {
		for (String id : ids) {
			copyCount(inventory, counts, id);
		}
	}

	private static void copyCount(InventoryAccess inventory, Map<String, Integer> counts, String id) {
		int count = inventory.count(id);
		if (count > 0) {
			counts.put(ItemIds.normalize(id), count);
		}
	}

	private static boolean recoverableShortage(ActionResult.Status status) {
		return status == ActionResult.Status.MISSING || status == ActionResult.Status.UNAVAILABLE;
	}

	private static void stopMine(PhaseContext context) {
		var process = context.gateway().currentProcess();
		if (process.kind() == ProcessKind.MINE && process.outcome() == ProcessOutcome.RUNNING) {
			context.gateway().cancel();
		}
	}
}
