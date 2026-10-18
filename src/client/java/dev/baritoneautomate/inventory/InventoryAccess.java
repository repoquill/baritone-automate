package dev.baritoneautomate.inventory;

import java.util.function.Predicate;

/**
 * Thin inventory port. Craft and smelt use the vanilla crafting grid and furnace.
 * {@link ActionResult.Status#OK} means the output count actually increased.
 * {@link ActionResult.Status#PENDING} means the station is still working.
 */
public interface InventoryAccess {
	boolean available();

	/** Count of one item id across the player inventory and offhand. */
	int count(String itemId);

	/** {@code normalizedItemId} is already {@link ItemIds#normalize(String)}'d. */
	int countWhere(Predicate<String> normalizedItemId);

	default int countAny(String... itemIds) {
		int total = 0;
		if (itemIds == null) {
			return 0;
		}
		for (String itemId : itemIds) {
			total += count(itemId);
		}
		return total;
	}

	default boolean has(String itemId, int minimum) {
		return count(itemId) >= minimum;
	}

	/** Select a hotbar slot holding {@code itemId}. Does not open screens. */
	boolean selectHotbarItem(String itemId);

	int selectedHotbarSlot();

	ActionResult craft(String outputItemId, int count);

	ActionResult smelt(String inputItemId, int count);
}
