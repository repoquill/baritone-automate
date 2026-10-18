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

	public static ActionResult craft(StationPorts ports, String outputId, int count) {
		if (ports == null || !ports.inWorld()) {
			reset();
			return ActionResult.unavailable("no world");
		}
		String output = ItemIds.normalize(outputId);
		int times = Math.max(1, count);
		int have = ports.count(output);
		if (job == null || job.smelt || !output.equals(job.output)) {
			boolean table = ports.blockNearby("crafting_table") || ports.count("crafting_table") > 0;
			ActionResult preview = CraftRecipes.craft(ports.snapshot(), output, times, table);
			if (preview.status() != ActionResult.Status.OK) {
				reset();
				return preview;
			}
			job = new Job(output, have + times, false, "");
		}
		if (ports.count(output) >= job.target) {
			finish(ports);
			return ActionResult.ok("crafted " + ItemIds.path(output));
		}
		if (job.ticks++ > CRAFT_BUDGET) {
			finish(ports);
			return ActionResult.unavailable("crafting did not produce " + ItemIds.path(output));
		}
		return stepCraft(ports);
	}

	public static ActionResult smelt(StationPorts ports, String inputId, int count) {
		if (ports == null || !ports.inWorld()) {
			reset();
			return ActionResult.unavailable("no world");
		}
		if (!"raw_iron".equals(ItemIds.path(inputId))) {
			return ActionResult.unsupported("no smelt recipe for " + ItemIds.path(inputId));
		}
		int times = Math.max(1, count);
		int have = ports.count("iron_ingot");
		if (job == null || !job.smelt || !ItemIds.matches(job.input, inputId)) {
			boolean furnace = ports.blockNearby("furnace") || ports.count("furnace") > 0;
			boolean table = ports.blockNearby("crafting_table") || ports.count("crafting_table") > 0;
			ActionResult preview = CraftRecipes.smelt(ports.snapshot(), inputId, times, furnace, table);
			if (preview.status() != ActionResult.Status.OK) {
				reset();
				return preview;
			}
			job = new Job("minecraft:iron_ingot", have + times, true, ItemIds.normalize(inputId));
		}
		if (ports.count("iron_ingot") >= job.target) {
			finish(ports);
			return ActionResult.ok("smelted iron_ingot");
		}
		if (job.ticks++ > 220L * times + 200L) {
			finish(ports);
			return ActionResult.unavailable("furnace did not produce iron_ingot");
		}
		if (!ports.blockNearby("furnace") && ports.count("furnace") <= 0) {
			return craftNested(ports, "furnace");
		}
		return stepFurnace(ports);
	}

	private static ActionResult craftNested(StationPorts ports, String output) {
		if (job.nested == null || !output.equals(job.nested.output)) {
			job.nested = new Job(ItemIds.normalize(output), ports.count(output) + 1, false, "");
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
			reset();
		}
		return step;
	}

	private static ActionResult stepCraft(StationPorts ports) {
		CraftRecipes.Recipe recipe = CraftRecipes.recipe(job.focus().output);
		if (recipe == null) {
			reset();
			return ActionResult.unsupported("no recipe for " + ItemIds.path(job.focus().output));
		}
		if (!recipe.outputId().equals(job.output) && ports.count(recipe.outputId()) >= job.focus().target) {
			job.focusStack.pop();
			job.cell = 0;
			job.wait = 0;
			return ActionResult.pending("crafted " + ItemIds.path(recipe.outputId()));
		}
		if (job.cell == 0) {
			for (CraftRecipes.Need need : recipe.needs()) {
				if (CraftRecipes.countToken(ports.snapshot(), need.token()) >= need.count()) {
					continue;
				}
				String producer = producer(need.token());
				if (producer == null) {
					reset();
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
		if (recipe.needsTable()) {
			ActionResult opened = openTable(ports);
			if (opened != null) {
				return opened;
			}
		} else if (ports.menu() != MenuKind.PLAYER) {
			ports.closeMenu();
			return ActionResult.pending("closing the container");
		}
		if (job.cell < recipe.grid().size()) {
			String token = recipe.grid().get(job.cell);
			int width = recipe.gridWidth();
			int x = job.cell % width;
			int y = job.cell / width;
			job.cell++;
			if (token != null && !token.isBlank()) {
				ActionResult placed = ports.placeOne(token, StationSlots.craftSlot(width, x, y));
				if (placed.status() != ActionResult.Status.OK) {
					if (placed.status() != ActionResult.Status.PENDING) {
						reset();
					}
					return placed;
				}
				return ActionResult.pending("placing " + ItemIds.path(token));
			}
			return ActionResult.pending("crafting " + ItemIds.path(recipe.outputId()));
		}
		if (ports.craftResultEmpty()) {
			if (job.wait++ > RESULT_WAIT) {
				String produced = ItemIds.path(recipe.outputId());
				reset();
				return ActionResult.unavailable("recipe did not produce " + produced);
			}
			return ActionResult.pending("waiting for " + ItemIds.path(recipe.outputId()));
		}
		ports.quickMove(StationSlots.CRAFT_RESULT);
		job.cell = 0;
		job.wait = 0;
		return ActionResult.pending("taking " + ItemIds.path(recipe.outputId()));
	}

	private static String producer(String token) {
		if ("planks".equals(ItemIds.path(token))) {
			return "minecraft:oak_planks";
		}
		CraftRecipes.Recipe recipe = CraftRecipes.recipe(token);
		return recipe == null ? null : recipe.outputId();
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
				reset();
				return used;
			}
			return ActionResult.pending("opening the crafting table");
		}
		if (ports.count("crafting_table") > 0) {
			ActionResult placed = ports.placeBlock("crafting_table", "no crafting table in the inventory");
			if (placed.status() != ActionResult.Status.OK) {
				if (placed.status() != ActionResult.Status.PENDING) {
					reset();
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
		reset();
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
					reset();
					return used;
				}
				return ActionResult.pending("opening the furnace");
			}
			if (ports.count("furnace") <= 0) {
				reset();
				return ActionResult.unavailable("no furnace nearby");
			}
			ActionResult placed = ports.placeBlock("furnace", "no furnace in the inventory");
			if (placed.status() != ActionResult.Status.OK) {
				if (placed.status() != ActionResult.Status.PENDING) {
					reset();
				}
				return placed;
			}
			return ActionResult.pending("placing a furnace");
		}
		if (ports.furnaceIngredientEmpty()) {
			job.wait = 0;
			ActionResult placed = ports.placeOne("raw_iron", StationSlots.FURNACE_INGREDIENT);
			if (placed.status() != ActionResult.Status.OK) {
				if (placed.status() != ActionResult.Status.PENDING) {
					reset();
				}
				return placed;
			}
			return ActionResult.pending("putting raw iron in the furnace");
		}
		if (ports.furnaceFuelEmpty()) {
			String fuel = fuelToken(ports);
			if (fuel == null) {
				reset();
				return ActionResult.missing("missing fuel");
			}
			ActionResult placed = ports.placeOne(fuel, StationSlots.FURNACE_FUEL);
			if (placed.status() != ActionResult.Status.OK) {
				if (placed.status() != ActionResult.Status.PENDING) {
					reset();
				}
				return placed;
			}
			return ActionResult.pending("adding fuel");
		}
		if (!ports.furnaceResultEmpty()) {
			ports.quickMove(StationSlots.FURNACE_RESULT);
			return ActionResult.pending("taking iron ingots");
		}
		if (!ports.furnaceLit() && job.wait++ > 40) {
			reset();
			return ActionResult.unavailable("furnace is not burning");
		}
		return ActionResult.pending("smelting raw iron");
	}

	private static String fuelToken(StationPorts ports) {
		Map<String, Integer> counts = ports.snapshot();
		if (CraftRecipes.countToken(counts, "coal") > 0) {
			return "coal";
		}
		if (CraftRecipes.countToken(counts, "charcoal") > 0) {
			return "charcoal";
		}
		if (CraftRecipes.countToken(counts, "planks") > 0) {
			return "planks";
		}
		if (CraftRecipes.countToken(counts, "log") > 0) {
			return "log";
		}
		if (CraftRecipes.countToken(counts, "stick") > 0) {
			return "stick";
		}
		return null;
	}

	private static void finish(StationPorts ports) {
		if (ports != null && ports.inWorld() && ports.menu() != MenuKind.PLAYER) {
			ports.closeMenu();
		}
		job = null;
	}

	private static final class Job {
		private final String output;
		private final int target;
		private final boolean smelt;
		private final String input;
		private final ArrayDeque<Focus> focusStack = new ArrayDeque<>();
		private Job nested;
		private int cell;
		private int wait;
		private int ticks;

		private Job(String output, int target, boolean smelt, String input) {
			this.output = output;
			this.target = target;
			this.smelt = smelt;
			this.input = input;
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
