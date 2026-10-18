package dev.baritoneautomate.inventory;

import java.util.Map;

/**
 * What a crafting grid or furnace step needs from a screen handler.
 * The live client and the unit-test station both implement this.
 */
public interface StationPorts {
	enum MenuKind {
		PLAYER,
		CRAFTING,
		FURNACE,
		OTHER
	}

	boolean inWorld();

	int count(String itemId);

	/** Mutable copy. Recipe preview may clear and refill it. */
	Map<String, Integer> snapshot();

	boolean blockNearby(String blockId);

	MenuKind menu();

	boolean craftResultEmpty();

	boolean furnaceIngredientEmpty();

	boolean furnaceFuelEmpty();

	boolean furnaceResultEmpty();

	boolean furnaceLit();

	/**
	 * Pickup one matching stack and place a single item into {@code menuSlot}
	 * (vanilla pickup, then button 1).
	 */
	ActionResult placeOne(String token, int menuSlot);

	/** Quick-move that slot into the player inventory. */
	void quickMove(int menuSlot);

	ActionResult useBlock(String blockId);

	ActionResult placeBlock(String blockId, String missingMessage);

	void closeMenu();
}
