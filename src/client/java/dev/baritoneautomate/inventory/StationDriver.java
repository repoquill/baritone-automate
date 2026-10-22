package dev.baritoneautomate.inventory;

import dev.baritoneautomate.inventory.StationPorts.MenuKind;
import java.util.ArrayDeque;
import java.util.Map;

/**
 * One crafting or smelting step per call. {@link ActionResult.Status#OK} is returned
 * only after {@link StationPorts#count(String)} reaches the target. The same steps
 * run against a live screen handler or a simulated one.
 */
public final class StationDriver {
	private static final int CRAFT_BUDGET = 240;
	private static final int RESULT_WAIT = 30;

	private static Job job;

	private StationDriver() {
	}

	public static void reset() {
		job = null;
	}

	public static boolean hasPendingAction() {
		return job != null;
	}

	public static ActionResult tickPendingAction(StationPorts ports) {
		if (job == null) {
			return ActionResult.ok("no pending inventory action");
		}
		return job.smelt ? smelt(ports, job.input, job.times) : craft(ports, job.output, job.times);
	}

	public static void cancel(StationPorts ports) {
		if (job != null) {
			finish(ports);
		}
	}

	public static ActionResult craft(StationPorts ports, String outputId, int count) {
		if (ports == null || !ports.inWorld()) {
			finish(ports);
			return ActionResult.unavailable("no world");
		}
		String output = ItemIds.normalize(outputId);
		int times = Math.max(1, count);
		int have = ports.count(output);
		if (job == null || job.smelt || !output.equals(job.output)) {
			cancel(ports);
			ActionResult cleared = ports.clearCraftingGrid();
			if (cleared.status() != ActionResult.Status.OK) {
				return cleared;
			}
			have = ports.count(output);
			boolean table = ports.blockNearby("crafting_table") || ports.count("crafting_table") > 0;
			ActionResult preview = CraftRecipes.craft(ports.snapshot(), output, times, table);
			if (preview.status() != ActionResult.Status.OK) {
				finish(ports);
				return preview;
			}
			CraftRecipes.Recipe recipe = CraftRecipes.recipe(output);
			job = new Job(output, Math.addExact(have, Math.multiplyExact(times, recipe.outputCount())), false, "", times);
		}
		if (ports.count(output) >= job.target) {
			finish(ports);
			return ActionResult.ok("crafted " + ItemIds.path(output));
		}
		if (job.ticks++ > (long) CRAFT_BUDGET * job.times) {
			finish(ports);
			return ActionResult.unavailable("crafting did not produce " + ItemIds.path(output));
		}
		return stepCraft(ports);
	}

	public static ActionResult smelt(StationPorts ports, String inputId, int count) {
		if (ports == null || !ports.inWorld()) {
			finish(ports);
			return ActionResult.unavailable("no world");
		}
		if (!ItemIds.matches(inputId, "raw_iron")) {
			return ActionResult.unsupported("no smelt recipe for " + ItemIds.path(inputId));
		}
		int times = Math.max(1, count);
		int have = ports.count("iron_ingot");
		if (job == null || !job.smelt || !ItemIds.matches(job.input, inputId)) {
			cancel(ports);
			have = ports.count("iron_ingot");
			boolean furnace = ports.blockNearby("furnace") || ports.count("furnace") > 0;
			boolean table = ports.blockNearby("crafting_table") || ports.count("crafting_table") > 0;
			Map<String, Integer> previewItems = ports.snapshot();
			if (ports.menu() == MenuKind.FURNACE) {
				if (ports.furnaceIngredientMatches("raw_iron")) {
					previewItems.merge("minecraft:raw_iron", ports.furnaceIngredientCount(), Integer::sum);
				}
				if (ports.furnaceLit() || !ports.furnaceFuelEmpty()) {
					// The live step checks fuel again when the existing burn or queued fuel runs out.
					previewItems.merge("minecraft:coal", (int) ((times + 7L) / 8L), Integer::sum);
				}
			}
			ActionResult preview = CraftRecipes.smelt(previewItems, inputId, times, furnace, table);
			if (preview.status() != ActionResult.Status.OK) {
				finish(ports);
				return preview;
			}
			job = new Job("minecraft:iron_ingot", Math.addExact(have, times), true, ItemIds.normalize(inputId), times);
		}
		if (ports.count("iron_ingot") >= job.target) {
			finish(ports);
			return ActionResult.ok("smelted iron_ingot");
		}
		if (job.ticks++ > 220L * job.times + 200L) {
			finish(ports);
			return ActionResult.unavailable("furnace did not produce iron_ingot");
		}
		if (!ports.blockNearby("furnace") && ports.count("furnace") <= 0) {
			return craftNested(ports, "furnace");
		}
		return stepFurnace(ports);
	}

	private static ActionResult craftNested(StationPorts ports, String output) {
		output = ItemIds.normalize(output);
		if (job.nested == null || !output.equals(job.nested.output)) {
			job.nested = new Job(output, ports.count(output) + 1, false, "", 1);
		}
		if (ports.count(output) >= job.nested.target) {
			job.nested = null;
			return ActionResult.pending("crafted " + ItemIds.path(output));
		}
		Job outer = job;
		job = outer.nested;
		ActionResult step = stepCraft(ports);
		outer.nested = job;
		job = outer;
		if (step.status() == ActionResult.Status.OK) {
			return ActionResult.pending(step.message());
		}
		if (step.status() != ActionResult.Status.PENDING) {
			finish(ports);
		}
		return step;
	}

	private static ActionResult stepCraft(StationPorts ports) {
		CraftRecipes.Recipe recipe = CraftRecipes.recipe(job.focus().output);
		if (recipe == null) {
			String output = job.focus().output;
			finish(ports);
			return ActionResult.unsupported("no recipe for " + ItemIds.path(output));
		}
		if (!recipe.outputId().equals(job.output) && ports.count(recipe.outputId()) >= job.focus().target) {
			job.focusStack.pop();
			job.cell = 0;
			job.wait = 0;
			return ActionResult.pending("crafted " + ItemIds.path(recipe.outputId()));
		}
		if (recipe.needsTable()) {
			ActionResult opened = openTable(ports);
			if (opened != null) {
				return opened;
			}
		} else if (ports.menu() != MenuKind.PLAYER) {
			ports.closeMenu();
			return ActionResult.pending("closing the container");
		}
		if (job.cell == 0) {
			ActionResult cleared = ports.clearCraftingGrid();
			if (cleared.status() != ActionResult.Status.OK) {
				return cleared;
			}
			for (CraftRecipes.Need need : recipe.needs()) {
				if (CraftRecipes.countToken(ports.snapshot(), need.token()) >= need.count()) {
					continue;
				}
				String producer = CraftRecipes.producer(ports.snapshot(), need.token());
				if (producer == null) {
					finish(ports);
					return ActionResult.missing("missing " + ItemIds.path(need.token()));
				}
				if (!job.crafting(producer)) {
					CraftRecipes.Recipe made = CraftRecipes.recipe(producer);
					int batch = made == null ? 1 : made.outputCount();
					job.focusStack.push(new Focus(producer, ports.count(producer) + batch));
					job.cell = 0;
					job.wait = 0;
				}
				return ActionResult.pending("crafting " + ItemIds.path(producer));
			}
		}
		if (job.cell < recipe.grid().size()) {
			String token = recipe.grid().get(job.cell);
			int width = recipe.gridWidth();
			int x = job.cell % width;
			int y = job.cell / width;
			if (token != null && !token.isBlank()) {
				ActionResult placed = ports.placeOne(token, StationSlots.craftSlot(width, x, y));
				if (placed.status() != ActionResult.Status.OK) {
					if (placed.status() != ActionResult.Status.PENDING) {
						finish(ports);
					}
					return placed;
				}
				job.cell++;
				return ActionResult.pending("placing " + ItemIds.path(token));
			}
			job.cell++;
			return ActionResult.pending("crafting " + ItemIds.path(recipe.outputId()));
		}
		if (ports.craftResultEmpty()) {
			if (job.wait++ > RESULT_WAIT) {
				String produced = ItemIds.path(recipe.outputId());
				finish(ports);
				return ActionResult.unavailable("recipe did not produce " + produced);
			}
			return ActionResult.pending("waiting for " + ItemIds.path(recipe.outputId()));
		}
		ports.quickMove(StationSlots.CRAFT_RESULT);
		job.cell = 0;
		job.wait = 0;
		return ActionResult.pending("taking " + ItemIds.path(recipe.outputId()));
	}

	private static ActionResult openTable(StationPorts ports) {
		if (ports.menu() == MenuKind.OTHER) {
			ports.closeMenu();
			return ActionResult.pending("closing a container before crafting");
		}
		if (ports.menu() == MenuKind.CRAFTING) {
			return null;
		}
		if (ports.menu() == MenuKind.FURNACE) {
			ports.closeMenu();
			return ActionResult.pending("closing the container");
		}
		if (ports.blockNearby("crafting_table")) {
			ActionResult used = ports.useBlock("crafting_table");
			if (used.status() != ActionResult.Status.OK && used.status() != ActionResult.Status.PENDING) {
				finish(ports);
				return used;
			}
			return ActionResult.pending("opening the crafting table");
		}
		if (ports.count("crafting_table") > 0) {
			ActionResult placed = ports.placeBlock("crafting_table", "no crafting table in the inventory");
			if (placed.status() != ActionResult.Status.OK) {
				if (placed.status() != ActionResult.Status.PENDING) {
					finish(ports);
				}
				return placed;
			}
			return ActionResult.pending("placing a crafting table");
		}
		if (!job.crafting("minecraft:crafting_table") && !ItemIds.matches(job.focus().output, "crafting_table")) {
			job.focusStack.push(new Focus("minecraft:crafting_table", ports.count("crafting_table") + 1));
			job.cell = 0;
			job.wait = 0;
			return ActionResult.pending("crafting a crafting table");
		}
		finish(ports);
		return ActionResult.unavailable("no crafting table nearby");
	}

	private static ActionResult stepFurnace(StationPorts ports) {
		if (ports.menu() == MenuKind.OTHER || ports.menu() == MenuKind.CRAFTING) {
			ports.closeMenu();
			return ActionResult.pending("closing a container before smelting");
		}
		if (ports.menu() != MenuKind.FURNACE) {
			if (ports.blockNearby("furnace")) {
				ActionResult used = ports.useBlock("furnace");
				if (used.status() != ActionResult.Status.OK && used.status() != ActionResult.Status.PENDING) {
					finish(ports);
					return used;
				}
				return ActionResult.pending("opening the furnace");
			}
			if (ports.count("furnace") <= 0) {
				finish(ports);
				return ActionResult.unavailable("no furnace nearby");
			}
			ActionResult placed = ports.placeBlock("furnace", "no furnace in the inventory");
			if (placed.status() != ActionResult.Status.OK) {
				if (placed.status() != ActionResult.Status.PENDING) {
					finish(ports);
				}
				return placed;
			}
			return ActionResult.pending("placing a furnace");
		}
		if (!ports.furnaceResultEmpty()) {
			ports.quickMove(StationSlots.FURNACE_RESULT);
			return ActionResult.pending("taking iron ingots");
		}
		if (!ports.furnaceIngredientEmpty() && !ports.furnaceIngredientMatches("raw_iron")) {
			finish(ports);
			return ActionResult.unavailable("furnace input is busy with another item");
		}
		int remaining = job.target - ports.count("iron_ingot");
		int inputTarget = Math.min(remaining, 64);
		if (ports.furnaceIngredientCount() < inputTarget && ports.count("raw_iron") > 0) {
			job.wait = 0;
			ActionResult placed = ports.placeOne("raw_iron", StationSlots.FURNACE_INGREDIENT);
			if (placed.status() != ActionResult.Status.OK) {
				if (placed.status() != ActionResult.Status.PENDING) {
					finish(ports);
				}
				return placed;
			}
			return ActionResult.pending("putting raw iron in the furnace");
		}
		if (ports.furnaceIngredientEmpty()) {
			if (job.wait++ > RESULT_WAIT) {
				finish(ports);
				return ActionResult.missing("missing raw_iron");
			}
			return ActionResult.pending("waiting for the furnace result");
		}
		if (ports.furnaceFuelEmpty() && !ports.furnaceLit()) {
			String fuel = CraftRecipes.fuelToken(ports.snapshot());
			if (fuel == null) {
				finish(ports);
				return ActionResult.missing("missing fuel");
			}
			ActionResult placed = ports.placeOne(fuel, StationSlots.FURNACE_FUEL);
			if (placed.status() != ActionResult.Status.OK) {
				if (placed.status() != ActionResult.Status.PENDING) {
					finish(ports);
				}
				return placed;
			}
			return ActionResult.pending("adding fuel");
		}
		if (!ports.furnaceLit() && job.wait++ > 40) {
			finish(ports);
			return ActionResult.unavailable("furnace is not burning");
		}
		return ActionResult.pending("smelting raw iron");
	}

	private static void finish(StationPorts ports) {
		if (ports != null && ports.inWorld()) {
			ports.clearCraftingGrid();
			if (ports.menu() != MenuKind.PLAYER) {
				ports.closeMenu();
			}
		}
		job = null;
	}

	private static final class Job {
		private final String output;
		private final int target;
		private final boolean smelt;
		private final String input;
		private final int times;
		private final ArrayDeque<Focus> focusStack = new ArrayDeque<>();
		private Job nested;
		private int cell;
		private int wait;
		private int ticks;

		private Job(String output, int target, boolean smelt, String input, int times) {
			this.output = output;
			this.target = target;
			this.smelt = smelt;
			this.input = input;
			this.times = times;
			this.focusStack.push(new Focus(output, target));
		}

		private Focus focus() {
			return focusStack.peek();
		}

		private boolean crafting(String outputId) {
			for (Focus focus : focusStack) {
				if (focus.output.equals(outputId)) {
					return true;
				}
			}
			return false;
		}
	}

	private record Focus(String output, int target) {
	}
}
