package dev.baritoneautomate.inventory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Speedrun recipes the phases actually call. Applying a recipe mutates the count
 * map only when every ingredient, including crafted intermediates, is present.
 * A 3x3 recipe without a table tries to craft a crafting table first.
 */
public final class CraftRecipes {
	public record Need(String token, int count) {
	}

	public record Recipe(String outputId, int outputCount, boolean needsTable, int gridWidth, List<String> grid, List<Need> needs) {
	}

	private static final Map<String, Recipe> RECIPES = build();

	private CraftRecipes() {
	}

	public static Recipe recipe(String outputId) {
		return RECIPES.get(ItemIds.normalize(outputId));
	}

	public static ActionResult craft(Map<String, Integer> counts, String outputId, int times, boolean tableReady) {
		if (counts == null) {
			return ActionResult.unavailable("no inventory");
		}
		int amount = Math.max(1, times);
		Map<String, Integer> copy = normalize(counts);
		boolean table = tableReady;
		ArrayDeque<String> stack = new ArrayDeque<>();
		Recipe requested = recipe(outputId);
		if (requested == null) {
			return ActionResult.unsupported("no recipe for " + ItemIds.path(outputId));
		}
		if (requested.needsTable() && !table) {
			ActionResult tableCraft = craftOne(copy, "crafting_table", false, stack);
			if (tableCraft.status() != ActionResult.Status.OK) {
				return ActionResult.unavailable("no crafting table nearby");
			}
			table = true;
		}
		for (int i = 0; i < amount; i++) {
			ActionResult step = craftOne(copy, outputId, table, stack);
			if (step.status() != ActionResult.Status.OK) {
				return step;
			}
		}
		counts.clear();
		counts.putAll(copy);
		return ActionResult.ok("crafted " + amount + " " + ItemIds.path(outputId));
	}

	public static ActionResult smelt(Map<String, Integer> counts, String inputId, int times, boolean furnaceReady, boolean tableReady) {
		if (counts == null) {
			return ActionResult.unavailable("no inventory");
		}
		if (!"raw_iron".equals(ItemIds.path(inputId))) {
			return ActionResult.unsupported("no smelt recipe for " + ItemIds.path(inputId));
		}
		int amount = Math.max(1, times);
		Map<String, Integer> copy = normalize(counts);
		if (!furnaceReady) {
			ActionResult furnace = craft(copy, "furnace", 1, tableReady);
			if (furnace.status() != ActionResult.Status.OK) {
				return ActionResult.unavailable("no furnace nearby");
			}
		}
		if (countToken(copy, "raw_iron") < amount) {
			return ActionResult.missing("missing raw_iron");
		}
		if (!hasFuel(copy, amount)) {
			return ActionResult.missing("missing fuel");
		}
		consume(copy, "raw_iron", amount);
		consumeFuel(copy, amount);
		add(copy, "minecraft:iron_ingot", amount);
		counts.clear();
		counts.putAll(copy);
		return ActionResult.ok("smelted " + amount + " iron_ingot");
	}

	public static int countToken(Map<String, Integer> counts, String token) {
		int total = 0;
		if (counts == null) {
			return 0;
		}
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (entry.getValue() != null && matches(entry.getKey(), token)) {
				total += entry.getValue();
			}
		}
		return total;
	}

	public static boolean matches(String itemId, String token) {
		String path = ItemIds.path(itemId);
		String want = ItemIds.path(token);
		return switch (want) {
			case "log" -> path.endsWith("_log") || path.endsWith("_stem");
			case "planks" -> path.endsWith("_planks");
			case "cobble" -> path.equals("cobblestone") || path.equals("cobbled_deepslate");
			default -> path.equals(want);
		};
	}

	private static ActionResult craftOne(Map<String, Integer> counts, String outputId, boolean table, ArrayDeque<String> stack) {
		Recipe recipe = recipe(outputId);
		if (recipe == null) {
			return ActionResult.unsupported("no recipe for " + ItemIds.path(outputId));
		}
		String id = recipe.outputId();
		if (stack.contains(id)) {
			return ActionResult.unsupported("recipe cycle for " + ItemIds.path(outputId));
		}
		if (recipe.needsTable() && !table) {
			return ActionResult.unavailable("no crafting table nearby");
		}
		stack.push(id);
		for (Need need : recipe.needs()) {
			int guard = 0;
			while (countToken(counts, need.token()) < need.count()) {
				if (guard++ > 16) {
					stack.pop();
					return ActionResult.missing("missing " + ItemIds.path(need.token()));
				}
				String producer = producer(need.token());
				if (producer == null) {
					stack.pop();
					return ActionResult.missing("missing " + ItemIds.path(need.token()));
				}
				ActionResult made = craftOne(counts, producer, table, stack);
				if (made.status() != ActionResult.Status.OK) {
					stack.pop();
					return made;
				}
			}
		}
		if (!consumeNeeds(counts, recipe.needs())) {
			stack.pop();
			return ActionResult.missing("missing ingredients for " + ItemIds.path(outputId));
		}
		add(counts, id, recipe.outputCount());
		stack.pop();
		return ActionResult.ok("crafted " + ItemIds.path(outputId));
	}

	private static String producer(String token) {
		String path = ItemIds.path(token);
		if (path.equals("planks")) {
			return "minecraft:oak_planks";
		}
		if (recipe(token) != null) {
			return ItemIds.normalize(token);
		}
		return null;
	}

	private static boolean consumeNeeds(Map<String, Integer> counts, List<Need> needs) {
		Map<String, Integer> copy = new HashMap<>(counts);
		for (Need need : needs) {
			if (!consume(copy, need.token(), need.count())) {
				return false;
			}
		}
		counts.clear();
		counts.putAll(copy);
		return true;
	}

	private static boolean consume(Map<String, Integer> counts, String token, int amount) {
		int left = amount;
		List<String> keys = new ArrayList<>(counts.keySet());
		for (String key : keys) {
			if (left <= 0) {
				break;
			}
			if (!matches(key, token)) {
				continue;
			}
			int have = counts.getOrDefault(key, 0);
			int used = Math.min(have, left);
			counts.put(key, have - used);
			left -= used;
		}
		return left <= 0;
	}

	private static boolean hasFuel(Map<String, Integer> counts, int items) {
		int sticks = countToken(counts, "stick");
		return countToken(counts, "coal") * 8
				+ countToken(counts, "charcoal") * 8
				+ countToken(counts, "planks")
				+ countToken(counts, "log")
				+ sticks / 2 >= items;
	}

	private static void consumeFuel(Map<String, Integer> counts, int items) {
		int left = items;
		left = burn(counts, "coal", left, 8);
		left = burn(counts, "charcoal", left, 8);
		if (left > 0) {
			int use = Math.min(left, countToken(counts, "planks"));
			consume(counts, "planks", use);
			left -= use;
		}
		if (left > 0) {
			int use = Math.min(left, countToken(counts, "log"));
			consume(counts, "log", use);
			left -= use;
		}
		if (left > 0) {
			consume(counts, "stick", left * 2);
		}
	}

	private static int burn(Map<String, Integer> counts, String token, int left, int value) {
		if (left <= 0) {
			return 0;
		}
		int have = countToken(counts, token);
		int use = Math.min(have, (left + value - 1) / value);
		consume(counts, token, use);
		return Math.max(0, left - use * value);
	}

	private static void add(Map<String, Integer> counts, String itemId, int amount) {
		String id = ItemIds.normalize(itemId);
		counts.merge(id, amount, Integer::sum);
	}

	private static Map<String, Integer> normalize(Map<String, Integer> counts) {
		Map<String, Integer> copy = new HashMap<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (entry.getValue() == null || entry.getValue() <= 0) {
				continue;
			}
			copy.merge(ItemIds.normalize(entry.getKey()), entry.getValue(), Integer::sum);
		}
		return copy;
	}

	private static Map<String, Recipe> build() {
		Map<String, Recipe> recipes = new HashMap<>();
		add(recipes, "oak_planks", 4, false, 2, List.of("log", "", "", ""));
		add(recipes, "stick", 4, false, 2, List.of("planks", "", "planks", ""));
		add(recipes, "crafting_table", 1, false, 2, List.of("planks", "planks", "planks", "planks"));
		add(recipes, "wooden_pickaxe", 1, true, 3, List.of(
				"planks", "planks", "planks",
				"", "stick", "",
				"", "stick", ""));
		add(recipes, "stone_pickaxe", 1, true, 3, List.of(
				"cobble", "cobble", "cobble",
				"", "stick", "",
				"", "stick", ""));
		add(recipes, "furnace", 1, true, 3, List.of(
				"cobble", "cobble", "cobble",
				"cobble", "", "cobble",
				"cobble", "cobble", "cobble"));
		add(recipes, "iron_pickaxe", 1, true, 3, List.of(
				"iron_ingot", "iron_ingot", "iron_ingot",
				"", "stick", "",
				"", "stick", ""));
		add(recipes, "diamond_pickaxe", 1, true, 3, List.of(
				"diamond", "diamond", "diamond",
				"", "stick", "",
				"", "stick", ""));
		add(recipes, "flint_and_steel", 1, false, 2, List.of("iron_ingot", "", "", "flint"));
		add(recipes, "blaze_powder", 2, false, 2, List.of("blaze_rod", "", "", ""));
		add(recipes, "ender_eye", 1, false, 2, List.of("ender_pearl", "blaze_powder", "", ""));
		return Map.copyOf(recipes);
	}

	private static void add(Map<String, Recipe> recipes, String output, int count, boolean needsTable, int width, List<String> grid) {
		Map<String, Integer> totals = new HashMap<>();
		for (String cell : grid) {
			if (cell == null || cell.isBlank()) {
				continue;
			}
			totals.merge(cell, 1, Integer::sum);
		}
		List<Need> needs = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : totals.entrySet()) {
			needs.add(new Need(entry.getKey(), entry.getValue()));
		}
		String id = ItemIds.normalize(output);
		recipes.put(id, new Recipe(id, count, needsTable, width, List.copyOf(grid), List.copyOf(needs)));
	}
}
