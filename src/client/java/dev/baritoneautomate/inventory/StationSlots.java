package dev.baritoneautomate.inventory;

/**
 * Menu slot numbers taken from Minecraft 26.3 {@code minecraft-common-deobf}.
 *
 * <p>{@code InventoryMenu}: {@code RESULT_SLOT=0}, {@code CRAFT_SLOT_START=1},
 * {@code CRAFT_SLOT_COUNT=4} so the 2×2 grid is {@code 1 + x + y * 2}.
 * {@code CraftingMenu} is constructed as a 3×3 {@code AbstractCraftingMenu} and calls
 * {@code addResultSlot} before {@code addCraftingGridSlots}, so the grid is
 * {@code 1 + x + y * 3}. {@code addCraftingGridSlots} walks y then x and stores
 * {@code x + y * width}. {@code AbstractFurnaceMenu}: ingredient 0, fuel 1, result 2,
 * {@code SLOT_COUNT=3} (player slots follow).
 */
public final class StationSlots {
	public static final int CRAFT_RESULT = 0;
	public static final int GRID_ORIGIN = 1;
	public static final int PLAYER_GRID_WIDTH = 2;
	public static final int TABLE_GRID_WIDTH = 3;
	public static final int FURNACE_INGREDIENT = 0;
	public static final int FURNACE_FUEL = 1;
	public static final int FURNACE_RESULT = 2;

	private StationSlots() {
	}

	public static int craftSlot(int gridWidth, int x, int y) {
		return GRID_ORIGIN + x + y * gridWidth;
	}
}
