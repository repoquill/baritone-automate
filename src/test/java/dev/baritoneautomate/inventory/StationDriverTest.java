package dev.baritoneautomate.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baritoneautomate.inventory.StationPorts.MenuKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Screen-handler steps without a Minecraft client. Slots follow {@link StationSlots}. */
class StationDriverTest {
	@BeforeEach
	void resetJob() {
		StationDriver.reset();
	}

	@Test
	void playerGridCraftsSticksOnTheTwoByTwoSlots() {
		SimStation station = new SimStation();
		station.set("oak_planks", 2);
		ActionResult result = pump(station, () -> StationDriver.craft(station, "stick", 1));
		assertEquals(ActionResult.Status.OK, result.status());
		assertEquals(4, station.count("stick"));
		assertEquals(0, station.count("oak_planks"));
		assertEquals(MenuKind.PLAYER, station.menu);
		assertFalse(station.openedCrafting);
		assertEquals(List.of(1, 3), station.placed);
		assertEquals(List.of(StationSlots.CRAFT_RESULT), station.quickMoves);
	}

	@Test
	void craftingTableUsesTheThreeByThreeGrid() {
		SimStation station = new SimStation();
		station.tableNearby = true;
		station.set("oak_planks", 3);
		station.set("stick", 2);
		ActionResult result = pump(station, () -> StationDriver.craft(station, "wooden_pickaxe", 1));
		assertEquals(ActionResult.Status.OK, result.status());
		assertEquals(1, station.count("wooden_pickaxe"));
		assertEquals(0, station.count("oak_planks"));
		assertEquals(0, station.count("stick"));
		assertTrue(station.openedCrafting);
		assertEquals(List.of(1, 2, 3, 5, 8), station.placed);
		assertTrue(station.quickMoves.contains(StationSlots.CRAFT_RESULT));
	}

	@Test
	void furnaceTakesSeveralTicksToFinishOneIngot() {
		SimStation station = new SimStation();
		station.furnaceNearby = true;
		station.smeltDelay = 8;
		station.set("raw_iron", 1);
		station.set("coal", 1);
		int calls = 0;
		ActionResult result = ActionResult.pending("start");
		while (result.status() == ActionResult.Status.PENDING && calls < 40) {
			result = StationDriver.smelt(station, "raw_iron", 1);
			calls++;
		}
		assertEquals(ActionResult.Status.OK, result.status());
		assertTrue(calls > 8, "smelt finished in " + calls + " steps");
		assertEquals(1, station.count("iron_ingot"));
		assertEquals(0, station.count("raw_iron"));
		assertTrue(station.openedFurnace);
		assertTrue(station.placed.contains(StationSlots.FURNACE_INGREDIENT));
		assertTrue(station.placed.contains(StationSlots.FURNACE_FUEL));
		assertTrue(station.quickMoves.contains(StationSlots.FURNACE_RESULT));
	}

	@Test
	void blockedTablePlacementFailsWithoutAPick() {
		SimStation station = new SimStation();
		station.set("crafting_table", 1);
		station.set("oak_planks", 3);
		station.set("stick", 2);
		station.spotBlocked = true;
		ActionResult result = StationDriver.craft(station, "wooden_pickaxe", 1);
		assertEquals(ActionResult.Status.UNAVAILABLE, result.status());
		assertTrue(result.message().contains("blocked"));
		assertEquals(0, station.count("wooden_pickaxe"));
		assertEquals(MenuKind.PLAYER, station.menu);
	}

	@Test
	void busyContainerIsClosedBeforeTheTwoByTwoGrid() {
		SimStation station = new SimStation();
		station.menu = MenuKind.OTHER;
		station.set("oak_planks", 2);
		ActionResult first = StationDriver.craft(station, "stick", 1);
		assertEquals(ActionResult.Status.PENDING, first.status());
		assertTrue(first.message().contains("closing"));
		assertEquals(MenuKind.PLAYER, station.menu);
		ActionResult result = pump(station, () -> StationDriver.craft(station, "stick", 1));
		assertEquals(ActionResult.Status.OK, result.status());
		assertEquals(4, station.count("stick"));
	}

	@Test
	void busyClickFailsThePlace() {
		SimStation station = new SimStation();
		station.set("oak_planks", 2);
		station.clicksBusy = true;
		ActionResult result = StationDriver.craft(station, "stick", 1);
		assertEquals(ActionResult.Status.UNAVAILABLE, result.status());
		assertTrue(result.message().contains("busy"));
		assertEquals(0, station.count("stick"));
		assertEquals(2, station.count("oak_planks"));
	}

	@Test
	void furnaceThatNeverLightsFails() {
		SimStation station = new SimStation();
		station.furnaceNearby = true;
		station.lightFuel = false;
		station.set("raw_iron", 1);
		station.set("coal", 1);
		ActionResult result = pump(station, () -> StationDriver.smelt(station, "raw_iron", 1));
		assertEquals(ActionResult.Status.UNAVAILABLE, result.status());
		assertTrue(result.message().contains("not burning"));
		assertEquals(0, station.count("iron_ingot"));
	}

	@Test
	void openingAFurnaceCanBeRefused() {
		SimStation station = new SimStation();
		station.furnaceNearby = true;
		station.refuseOpen = true;
		station.set("raw_iron", 1);
		station.set("coal", 1);
		ActionResult result = StationDriver.smelt(station, "raw_iron", 1);
		assertEquals(ActionResult.Status.UNAVAILABLE, result.status());
		assertTrue(result.message().contains("busy"));
		assertFalse(station.openedFurnace);
		assertEquals(0, station.count("iron_ingot"));
	}

	@Test
	void requestedCraftCountMeansCompleteRecipeBatches() {
		SimStation station = new SimStation();
		station.set("oak_planks", 4);
		ActionResult result = pump(station, () -> StationDriver.craft(station, "stick", 2));
		assertEquals(ActionResult.Status.OK, result.status());
		assertEquals(8, station.count("stick"));
		assertEquals(0, station.count("oak_planks"));
	}

	@Test
	void pendingIngredientPlacementRetriesTheSameCell() {
		SimStation station = new SimStation();
		station.set("oak_planks", 2);
		station.pendingClicks = 1;
		ActionResult result = pump(station, () -> StationDriver.craft(station, "stick", 1));
		assertEquals(ActionResult.Status.OK, result.status(), result.message());
		assertEquals(4, station.count("stick"));
		assertEquals(List.of(1, 3), station.placed);
	}

	@Test
	void finishedIngotIsTakenBeforeRequestingMoreOreOrFuel() {
		SimStation station = new SimStation();
		station.furnaceNearby = true;
		station.set("raw_iron", 1);
		station.set("coal", 1);
		for (int i = 0; i < 3; i++) {
			assertEquals(ActionResult.Status.PENDING, StationDriver.smelt(station, "raw_iron", 1).status());
		}
		station.ingredient = "";
		station.fuel = "";
		station.furnaceResult = "minecraft:iron_ingot";
		ActionResult result = pump(station, () -> StationDriver.smelt(station, "raw_iron", 1));
		assertEquals(ActionResult.Status.OK, result.status(), result.message());
		assertEquals(1, station.count("iron_ingot"));
	}

	@Test
	void burningFurnaceDoesNotRequireAnotherFuelItem() {
		SimStation station = new SimStation();
		station.furnaceNearby = true;
		station.set("raw_iron", 1);
		station.set("coal", 1);
		for (int i = 0; i < 3; i++) {
			StationDriver.smelt(station, "raw_iron", 1);
		}
		station.fuel = "";
		station.smeltLeft = 100;
		assertEquals(ActionResult.Status.PENDING, StationDriver.smelt(station, "raw_iron", 1).status());
	}

	@Test
	void woodenPickCanBeCraftedThroughItsIntermediateRecipes() {
		SimStation station = new SimStation();
		station.set("oak_log", 4);
		ActionResult result = pump(station, () -> StationDriver.craft(station, "wooden_pickaxe", 1));
		assertEquals(ActionResult.Status.OK, result.status(), result.message());
		assertEquals(1, station.count("wooden_pickaxe"));
	}

	@Test
	void smeltingCanFirstCraftAndPlaceTheMissingFurnace() {
		SimStation station = new SimStation();
		station.tableNearby = true;
		station.set("cobblestone", 8);
		station.set("raw_iron", 1);
		station.set("coal", 1);
		ActionResult result = pump(station, () -> StationDriver.smelt(station, "raw_iron", 1));
		assertEquals(ActionResult.Status.OK, result.status(), result.message());
		assertTrue(station.furnaceNearby);
		assertEquals(1, station.count("iron_ingot"));
	}

	@Test
	void cancellingReturnsIngredientsAndAllowsTheNextCraft() {
		SimStation station = new SimStation();
		station.set("oak_planks", 2);
		assertEquals(ActionResult.Status.PENDING, StationDriver.craft(station, "stick", 1).status());
		assertEquals(1, station.count("oak_planks"));
		StationDriver.cancel(station);
		assertFalse(StationDriver.hasPendingAction());
		assertEquals(2, station.count("oak_planks"));
		assertEquals(ActionResult.Status.OK, pump(station, () -> StationDriver.craft(station, "stick", 1)).status());
	}

	@Test
	void furnaceLoadsTheBatchBeforeBurningItsOnlyFuel() {
		SimStation station = new SimStation();
		station.furnaceNearby = true;
		station.set("raw_iron", 3);
		station.set("coal", 1);
		ActionResult result = pump(station, () -> StationDriver.smelt(station, "raw_iron", 3));
		assertEquals(ActionResult.Status.OK, result.status(), result.message());
		assertEquals(3, station.count("iron_ingot"));
		assertEquals(1, station.placed.stream().filter(slot -> slot == StationSlots.FURNACE_FUEL).count());
	}

	@Test
	void smeltingMoreThanOneStackWaitsForSpaceInTheInputSlot() {
		SimStation station = new SimStation();
		station.furnaceNearby = true;
		station.set("raw_iron", 65);
		station.set("coal", 9);
		ActionResult result = ActionResult.pending("start");
		for (int i = 0; i < 1_000 && result.status() == ActionResult.Status.PENDING; i++) {
			result = StationDriver.smelt(station, "raw_iron", 65);
		}
		assertEquals(ActionResult.Status.OK, result.status(), result.message());
		assertEquals(65, station.count("iron_ingot"));
		assertEquals(0, station.count("raw_iron"));
	}

	@Test
	void largeCraftBatchesHaveEnoughTimeToComplete() {
		SimStation station = new SimStation();
		station.set("oak_planks", 128);
		ActionResult result = ActionResult.pending("start");
		for (int i = 0; i < 600 && result.status() == ActionResult.Status.PENDING; i++) {
			result = StationDriver.craft(station, "stick", 64);
		}
		assertEquals(ActionResult.Status.OK, result.status(), result.message());
		assertEquals(256, station.count("stick"));
	}

	@Test
	void anAlreadyBurningFurnaceCanFinishItsExistingInput() {
		SimStation station = new SimStation();
		station.furnaceNearby = true;
		station.menu = MenuKind.FURNACE;
		station.ingredient = "minecraft:raw_iron";
		station.ingredientCount = 1;
		station.lit = true;
		station.smeltLeft = 4;
		ActionResult result = pump(station, () -> StationDriver.smelt(station, "raw_iron", 1));
		assertEquals(ActionResult.Status.OK, result.status(), result.message());
		assertEquals(1, station.count("iron_ingot"));
		assertTrue(station.placed.isEmpty());
	}

	@Test
	void unrelatedFurnaceInputIsNotFueledOrReplaced() {
		SimStation station = new SimStation();
		station.furnaceNearby = true;
		station.menu = MenuKind.FURNACE;
		station.ingredient = "minecraft:raw_gold";
		station.ingredientCount = 1;
		station.set("raw_iron", 1);
		station.set("coal", 1);
		ActionResult result = StationDriver.smelt(station, "raw_iron", 1);
		assertEquals(ActionResult.Status.UNAVAILABLE, result.status());
		assertTrue(result.message().contains("busy"));
		assertTrue(station.placed.isEmpty());
		assertEquals(1, station.count("raw_iron"));
		assertEquals(1, station.count("coal"));
	}

	private static ActionResult pump(SimStation station, java.util.function.Supplier<ActionResult> step) {
		ActionResult result = ActionResult.pending("start");
		int guard = 0;
		while (result.status() == ActionResult.Status.PENDING && guard++ < 80) {
			result = step.get();
		}
		return result;
	}

	private static final class SimStation implements StationPorts {
		private static final List<String> KNOWN = List.of(
				"oak_planks",
				"stick",
				"crafting_table",
				"wooden_pickaxe",
				"stone_pickaxe",
				"furnace",
				"iron_pickaxe",
				"diamond_pickaxe",
				"flint_and_steel",
				"blaze_powder",
				"ender_eye");

		private final Map<String, Integer> items = new HashMap<>();
		private final List<String> grid = new ArrayList<>();
		final List<Integer> placed = new ArrayList<>();
		final List<Integer> quickMoves = new ArrayList<>();
		private MenuKind menu = MenuKind.PLAYER;
		private boolean tableNearby;
		private boolean furnaceNearby;
		private boolean spotBlocked;
		private boolean clicksBusy;
		private int pendingClicks;
		private boolean refuseOpen;
		private boolean lightFuel = true;
		private boolean openedCrafting;
		private boolean openedFurnace;
		private int smeltDelay = 8;
		private int smeltLeft;
		private boolean lit;
		private String ingredient = "";
		private int ingredientCount;
		private String fuel = "";
		private String furnaceResult = "";
		private String craftResult = "";
		private int craftResultCount;

		void set(String id, int count) {
			items.put(ItemIds.normalize(id), count);
		}

		@Override
		public boolean inWorld() {
			return true;
		}

		@Override
		public int count(String itemId) {
			return CraftRecipes.countToken(items, itemId);
		}

		@Override
		public Map<String, Integer> snapshot() {
			return new HashMap<>(items);
		}

		@Override
		public boolean blockNearby(String blockId) {
			if (ItemIds.matches(blockId, "crafting_table")) {
				return tableNearby;
			}
			if (ItemIds.matches(blockId, "furnace")) {
				return furnaceNearby;
			}
			return false;
		}

		@Override
		public MenuKind menu() {
			return menu;
		}

		@Override
		public boolean craftResultEmpty() {
			refreshCraft();
			return craftResult.isEmpty();
		}

		@Override
		public boolean furnaceIngredientEmpty() {
			return ingredient.isEmpty();
		}

		@Override
		public int furnaceIngredientCount() {
			return ingredient.isEmpty() ? 0 : ingredientCount;
		}

		@Override
		public boolean furnaceIngredientMatches(String token) {
			return CraftRecipes.matches(ingredient, token);
		}

		@Override
		public boolean furnaceFuelEmpty() {
			return fuel.isEmpty();
		}

		@Override
		public boolean furnaceResultEmpty() {
			if (!furnaceResult.isEmpty()) {
				return false;
			}
			if (ingredient.isEmpty() || !lit) {
				return true;
			}
			if (smeltLeft > 0) {
				smeltLeft--;
			}
			if (smeltLeft > 0) {
				return true;
			}
			furnaceResult = "minecraft:iron_ingot";
			if (--ingredientCount <= 0) {
				ingredient = "";
			}
			smeltLeft = smeltDelay;
			return false;
		}

		@Override
		public boolean furnaceLit() {
			return lit;
		}

		@Override
		public ActionResult clearCraftingGrid() {
			if (menu == MenuKind.PLAYER || menu == MenuKind.CRAFTING) {
				for (int i = 0; i < grid.size(); i++) {
					if (!grid.get(i).isBlank()) {
						give(grid.get(i), 1);
						grid.set(i, "");
					}
				}
				craftResult = "";
			}
			return ActionResult.ok("crafting grid is empty");
		}

		@Override
		public ActionResult placeOne(String token, int menuSlot) {
			if (pendingClicks > 0) {
				pendingClicks--;
				return ActionResult.pending("waiting for container sync");
			}
			if (clicksBusy) {
				return ActionResult.unavailable("container is busy");
			}
			if (menu == MenuKind.FURNACE && menuSlot == StationSlots.FURNACE_INGREDIENT && ingredientCount >= 64) {
				return ActionResult.unavailable("furnace input slot is full");
			}
			String taken = take(token);
			if (taken == null) {
				return ActionResult.missing("missing " + ItemIds.path(token));
			}
			if (menu == MenuKind.FURNACE) {
				if (menuSlot == StationSlots.FURNACE_INGREDIENT) {
					ingredient = taken;
					ingredientCount++;
				} else if (menuSlot == StationSlots.FURNACE_FUEL) {
					fuel = taken;
					lit = lightFuel;
					smeltLeft = smeltDelay;
				} else {
					give(taken, 1);
					return ActionResult.unavailable("furnace slot " + menuSlot + " is not an input");
				}
				placed.add(menuSlot);
				return ActionResult.ok("placed " + ItemIds.path(token));
			}
			int index = menuSlot - StationSlots.GRID_ORIGIN;
			int width = menu == MenuKind.CRAFTING ? StationSlots.TABLE_GRID_WIDTH : StationSlots.PLAYER_GRID_WIDTH;
			ensureGrid(width * width);
			if (index < 0 || index >= width * width) {
				give(taken, 1);
				return ActionResult.unavailable("craft slot " + menuSlot + " is outside the grid");
			}
			grid.set(index, taken);
			placed.add(menuSlot);
			return ActionResult.ok("placed " + ItemIds.path(token));
		}

		@Override
		public void quickMove(int menuSlot) {
			quickMoves.add(menuSlot);
			if (menu == MenuKind.FURNACE && menuSlot == StationSlots.FURNACE_RESULT && !furnaceResult.isEmpty()) {
				give(furnaceResult, 1);
				furnaceResult = "";
				return;
			}
			if (menuSlot == StationSlots.CRAFT_RESULT && !craftResult.isEmpty()) {
				give(craftResult, craftResultCount);
				craftResult = "";
				craftResultCount = 0;
				for (int i = 0; i < grid.size(); i++) {
					grid.set(i, "");
				}
			}
		}

		@Override
		public ActionResult useBlock(String blockId) {
			if (refuseOpen) {
				return ActionResult.unavailable("container is busy");
			}
			spill();
			if (ItemIds.matches(blockId, "furnace")) {
				menu = MenuKind.FURNACE;
				openedFurnace = true;
			} else {
				menu = MenuKind.CRAFTING;
				openedCrafting = true;
			}
			return ActionResult.pending("opening the " + ItemIds.path(blockId));
		}

		@Override
		public ActionResult placeBlock(String blockId, String missingMessage) {
			if (spotBlocked) {
				return ActionResult.unavailable("could not place the block; the spot is blocked");
			}
			if (take(blockId) == null) {
				return ActionResult.missing(missingMessage);
			}
			if (ItemIds.matches(blockId, "crafting_table")) {
				tableNearby = true;
			}
			if (ItemIds.matches(blockId, "furnace")) {
				furnaceNearby = true;
			}
			return ActionResult.ok("placed a block");
		}

		@Override
		public void closeMenu() {
			spill();
			menu = MenuKind.PLAYER;
		}

		private void refreshCraft() {
			craftResult = "";
			craftResultCount = 0;
			if (menu == MenuKind.FURNACE) {
				return;
			}
			int width = menu == MenuKind.CRAFTING ? StationSlots.TABLE_GRID_WIDTH : StationSlots.PLAYER_GRID_WIDTH;
			ensureGrid(width * width);
			for (String id : KNOWN) {
				CraftRecipes.Recipe recipe = CraftRecipes.recipe(id);
				if (recipe == null || recipe.gridWidth() != width || recipe.grid().size() != width * width) {
					continue;
				}
				boolean matches = true;
				for (int i = 0; i < recipe.grid().size(); i++) {
					String token = recipe.grid().get(i);
					String cell = grid.get(i);
					if (token == null || token.isBlank()) {
						if (cell != null && !cell.isBlank()) {
							matches = false;
						}
					} else if (!CraftRecipes.matches(cell, token)) {
						matches = false;
					}
				}
				if (matches) {
					craftResult = recipe.outputId();
					craftResultCount = recipe.outputCount();
					return;
				}
			}
		}

		private void ensureGrid(int size) {
			while (grid.size() < size) {
				grid.add("");
			}
		}

		private void spill() {
			for (int i = 0; i < grid.size(); i++) {
				if (!grid.get(i).isBlank()) {
					give(grid.get(i), 1);
				}
				grid.set(i, "");
			}
			if (!ingredient.isEmpty()) {
				give(ingredient, 1);
				ingredient = "";
			}
			if (!fuel.isEmpty()) {
				give(fuel, 1);
				fuel = "";
			}
			if (!furnaceResult.isEmpty()) {
				give(furnaceResult, 1);
				furnaceResult = "";
			}
			lit = false;
		}

		private String take(String token) {
			for (Map.Entry<String, Integer> entry : items.entrySet()) {
				int have = entry.getValue() == null ? 0 : entry.getValue();
				if (have > 0 && CraftRecipes.matches(entry.getKey(), token)) {
					entry.setValue(have - 1);
					return entry.getKey();
				}
			}
			return null;
		}

		private void give(String id, int amount) {
			items.merge(ItemIds.normalize(id), amount, Integer::sum);
		}
	}
}
