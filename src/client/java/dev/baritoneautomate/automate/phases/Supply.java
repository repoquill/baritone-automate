package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.bridge.ProcessKind;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.inventory.ActionResult;
import dev.baritoneautomate.inventory.InventoryAccess;
import java.util.List;

/** Turns craft and smelt results into a phase tick. OK counts only when the items appear. */
final class Supply {
	private static final List<String> PICKS = List.of(
			"wooden_pickaxe",
			"stone_pickaxe",
			"iron_pickaxe",
			"diamond_pickaxe",
			"netherite_pickaxe");

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

	static PhaseTick craft(PhaseContext context, String itemId) {
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
		return PhaseTick.fail(result.message());
	}

	static PhaseTick smelt(PhaseContext context, String inputId, int count) {
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
		return PhaseTick.fail(result.message());
	}

	private static void stopMine(PhaseContext context) {
		var process = context.gateway().currentProcess();
		if (process.kind() == ProcessKind.MINE && process.outcome() == ProcessOutcome.RUNNING) {
			context.gateway().cancel();
		}
	}
}
