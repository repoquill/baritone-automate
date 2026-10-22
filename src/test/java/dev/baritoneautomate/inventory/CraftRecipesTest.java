package dev.baritoneautomate.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CraftRecipesTest {
	@Test
	void woodenPickFromLogsConsumesTheChain() {
		Map<String, Integer> counts = new HashMap<>();
		counts.put("minecraft:oak_log", 4);
		ActionResult result = CraftRecipes.craft(counts, "wooden_pickaxe", 1, false);
		assertTrue(result.status() == ActionResult.Status.OK, result.message());
		assertEquals(1, counts.get("minecraft:wooden_pickaxe"));
		assertTrue(counts.getOrDefault("minecraft:crafting_table", 0) >= 1);
	}

	@Test
	void missingTableDoesNotConsumeIngredients() {
		Map<String, Integer> counts = new HashMap<>();
		counts.put("minecraft:oak_planks", 3);
		counts.put("minecraft:stick", 2);
		ActionResult result = CraftRecipes.craft(counts, "wooden_pickaxe", 1, false);
		assertEquals(ActionResult.Status.UNAVAILABLE, result.status());
		assertTrue(result.message().contains("crafting table"));
		assertEquals(3, counts.get("minecraft:oak_planks"));
		assertEquals(2, counts.get("minecraft:stick"));
	}

	@Test
	void enderEyeRequiresPowder() {
		Map<String, Integer> counts = new HashMap<>();
		counts.put("minecraft:ender_pearl", 1);
		ActionResult result = CraftRecipes.craft(counts, "ender_eye", 1, false);
		assertEquals(ActionResult.Status.MISSING, result.status());
		assertTrue(result.message().contains("blaze"));
		assertEquals(1, counts.get("minecraft:ender_pearl"));
		assertEquals(null, counts.get("minecraft:ender_eye"));
	}

	@Test
	void enderEyeCraftsPowderFromRods() {
		Map<String, Integer> counts = new HashMap<>();
		counts.put("minecraft:ender_pearl", 1);
		counts.put("minecraft:blaze_rod", 1);
		ActionResult result = CraftRecipes.craft(counts, "ender_eye", 1, false);
		assertEquals(ActionResult.Status.OK, result.status());
		assertEquals(1, counts.get("minecraft:ender_eye"));
		assertEquals(1, counts.getOrDefault("minecraft:blaze_powder", 0));
	}

	@Test
	void smeltRawIronUsesOneCoal() {
		Map<String, Integer> counts = new HashMap<>();
		counts.put("minecraft:raw_iron", 3);
		counts.put("minecraft:coal", 1);
		ActionResult result = CraftRecipes.smelt(counts, "raw_iron", 3, true, true);
		assertEquals(ActionResult.Status.OK, result.status());
		assertEquals(3, counts.get("minecraft:iron_ingot"));
		assertEquals(0, counts.get("minecraft:raw_iron"));
		assertEquals(0, counts.get("minecraft:coal"));
	}

	@Test
	void smeltWithoutFurnaceOrFuelFailsCleanly() {
		Map<String, Integer> noFurnace = new HashMap<>();
		noFurnace.put("minecraft:raw_iron", 1);
		noFurnace.put("minecraft:coal", 1);
		ActionResult missingFurnace = CraftRecipes.smelt(noFurnace, "raw_iron", 1, false, true);
		assertEquals(ActionResult.Status.UNAVAILABLE, missingFurnace.status());
		assertTrue(missingFurnace.message().contains("furnace"));
		assertEquals(1, noFurnace.get("minecraft:raw_iron"));

		Map<String, Integer> noFuel = new HashMap<>();
		noFuel.put("minecraft:raw_iron", 1);
		ActionResult missingFuel = CraftRecipes.smelt(noFuel, "raw_iron", 1, true, true);
		assertEquals(ActionResult.Status.MISSING, missingFuel.status());
		assertTrue(missingFuel.message().contains("fuel"));
		assertEquals(1, noFuel.get("minecraft:raw_iron"));
	}

	@Test
	void birchLogsProduceBirchPlanksThroughoutTheCraftingChain() {
		Map<String, Integer> counts = new HashMap<>();
		counts.put("minecraft:birch_log", 4);
		assertEquals(ActionResult.Status.OK, CraftRecipes.craft(counts, "wooden_pickaxe", 1, false).status());
		assertEquals(1, counts.get("minecraft:wooden_pickaxe"));
		assertEquals(0, counts.getOrDefault("minecraft:oak_planks", 0));
		assertTrue(counts.getOrDefault("minecraft:birch_planks", 0) > 0);
	}

	@Test
	void inventoryTableIsReusedWithoutCraftingAnotherOne() {
		Map<String, Integer> counts = new HashMap<>(Map.of("crafting_table", 1, "oak_planks", 3, "stick", 2));
		assertEquals(ActionResult.Status.OK, CraftRecipes.craft(counts, "wooden_pickaxe", 1, false).status());
		assertEquals(1, counts.get("minecraft:crafting_table"));
	}

	@Test
	void twoPlanksCanSmeltThreeItems() {
		Map<String, Integer> counts = new HashMap<>(Map.of("raw_iron", 3, "oak_planks", 2));
		assertEquals(ActionResult.Status.OK, CraftRecipes.smelt(counts, "raw_iron", 3, true, true).status());
		assertEquals(3, counts.get("minecraft:iron_ingot"));
		assertEquals(0, counts.get("minecraft:oak_planks"));
	}

	@Test
	void netherWoodCannotBeUsedAsFurnaceFuel() {
		Map<String, Integer> counts = new HashMap<>(Map.of("raw_iron", 1, "warped_stem", 1, "crimson_planks", 2));
		assertEquals(ActionResult.Status.MISSING, CraftRecipes.smelt(counts, "raw_iron", 1, true, true).status());
		assertEquals(1, counts.get("raw_iron"));
	}

	@Test
	void vanillaRecipesDoNotConsumeItemsWithAnotherNamespace() {
		assertEquals(0, CraftRecipes.countToken(Map.of("example:iron_ingot", 3), "iron_ingot"));
		assertEquals(3, CraftRecipes.countToken(Map.of("example:iron_ingot", 3), "example:iron_ingot"));
	}
}
