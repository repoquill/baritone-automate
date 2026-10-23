package dev.baritoneautomate.bootstrap;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.inventory.ActionResult;
import dev.baritoneautomate.inventory.CraftRecipes;
import dev.baritoneautomate.inventory.ItemIds;
import dev.baritoneautomate.inventory.StationDriver;
import dev.baritoneautomate.inventory.StationPorts;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Live screen-handler adapter for {@link StationDriver}.
 *
 * <p>Clicks go through {@code MultiPlayerGameMode.handleContainerInput}
 * ({@code ContainerInput.PICKUP} button 0, button 1 to place one, {@code QUICK_MOVE}).
 * That call returns void, so a rejected or still-syncing click is only visible later
 * as an empty result slot or an inventory count that did not rise. Opening a block
 * is {@code useItemOn}. Those two outcomes still need a 26.3 client to confirm.
 */
public final class ClientStation {
	private static final int STATION_BLOCK_RADIUS = 6;
	private static final StationPorts PORTS = new MinecraftPorts();

	private ClientStation() {
	}

	public static ActionResult craft(String outputId, int count) {
		return StationDriver.craft(PORTS, outputId, count);
	}

	public static ActionResult smelt(String inputId, int count) {
		return StationDriver.smelt(PORTS, inputId, count);
	}

	public static ActionResult tickPendingAction() {
		return StationDriver.tickPendingAction(PORTS);
	}

	public static void cancelPendingAction() {
		StationDriver.cancel(PORTS);
	}

	private static final class MinecraftPorts implements StationPorts {
		@Override
		public boolean inWorld() {
			return player() != null;
		}

		@Override
		public int count(String itemId) {
			LocalPlayer player = player();
			if (player == null) {
				return 0;
			}
			return CraftRecipes.countToken(inventorySnapshot(player), itemId);
		}

		@Override
		public Map<String, Integer> snapshot() {
			LocalPlayer player = player();
			return player == null ? new HashMap<>() : inventorySnapshot(player);
		}

		@Override
		public boolean blockNearby(String blockId) {
			LocalPlayer player = player();
			return player != null && nearbyBlock(player, blockId, STATION_BLOCK_RADIUS) != null;
		}

		@Override
		public MenuKind menu() {
			LocalPlayer player = player();
			if (player == null) {
				return MenuKind.OTHER;
			}
			if (player.containerMenu instanceof CraftingMenu) {
				return MenuKind.CRAFTING;
			}
			if (player.containerMenu instanceof AbstractFurnaceMenu) {
				return MenuKind.FURNACE;
			}
			if (player.containerMenu instanceof InventoryMenu) {
				return MenuKind.PLAYER;
			}
			return MenuKind.OTHER;
		}

		@Override
		public boolean craftResultEmpty() {
			return slotEmpty(0);
		}

		@Override
		public boolean furnaceIngredientEmpty() {
			return slotEmpty(AbstractFurnaceMenu.INGREDIENT_SLOT);
		}

		@Override
		public int furnaceIngredientCount() {
			LocalPlayer player = player();
			return player == null || menu() != MenuKind.FURNACE ? 0
					: player.containerMenu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem().getCount();
		}

		@Override
		public boolean furnaceIngredientMatches(String token) {
			LocalPlayer player = player();
			return player != null && menu() == MenuKind.FURNACE
					&& CraftRecipes.matches(itemId(player.containerMenu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem()), token);
		}

		@Override
		public boolean furnaceFuelEmpty() {
			return slotEmpty(AbstractFurnaceMenu.FUEL_SLOT);
		}

		@Override
		public boolean furnaceResultEmpty() {
			return slotEmpty(AbstractFurnaceMenu.RESULT_SLOT);
		}

		@Override
		public boolean furnaceLit() {
			LocalPlayer player = player();
			return player != null
					&& player.containerMenu instanceof AbstractFurnaceMenu menu
					&& menu.isLit();
		}

		@Override
		public ActionResult clearCraftingGrid() {
			LocalPlayer player = player();
			if (player == null || Minecraft.getInstance().gameMode == null) {
				return ActionResult.unavailable("no world");
			}
			ActionResult recovered = recoverCarriedCursor(player);
			if (recovered != null) {
				return recovered;
			}
			int cells = switch (menu()) {
				case PLAYER -> 4;
				case CRAFTING -> 9;
				default -> 0;
			};
			boolean clearing = false;
			for (int slot = 1; slot <= cells; slot++) {
				if (!slotEmpty(slot)) {
					quickMove(slot);
					clearing = true;
				}
			}
			return clearing ? ActionResult.pending("clearing the crafting grid") : ActionResult.ok("crafting grid is empty");
		}

		@Override
		public ActionResult placeOne(String token, int menuSlot) {
			LocalPlayer player = player();
			if (player == null || Minecraft.getInstance().gameMode == null) {
				return ActionResult.unavailable("no world");
			}
			ActionResult recovered = recoverCarriedCursor(player);
			if (recovered != null) {
				return recovered;
			}
			int source = findInventorySlot(player, token);
			if (source < 0) {
				return ActionResult.missing("missing " + ItemIds.path(token));
			}
			if (menuSlot < 0 || menuSlot >= player.containerMenu.slots.size()
					|| !player.containerMenu.getSlot(menuSlot).mayPlace(player.containerMenu.getSlot(source).getItem())
					|| (!slotEmpty(menuSlot) && (!ItemStack.isSameItemSameComponents(
							player.containerMenu.getSlot(menuSlot).getItem(), player.containerMenu.getSlot(source).getItem())
							|| player.containerMenu.getSlot(menuSlot).getItem().getCount()
									>= player.containerMenu.getSlot(menuSlot).getItem().getMaxStackSize()))) {
				return ActionResult.unavailable("container input slot is busy");
			}
			Slot target = player.containerMenu.getSlot(menuSlot);
			int before = target.getItem().isEmpty() ? 0 : target.getItem().getCount();
			click(player, source, 0, ContainerInput.PICKUP);
			click(player, menuSlot, 1, ContainerInput.PICKUP);
			click(player, source, 0, ContainerInput.PICKUP);
			if (!placedInSlot(target, token, before)) {
				return ActionResult.pending("waiting for " + ItemIds.path(token));
			}
			return ActionResult.ok("placed " + ItemIds.path(token));
		}

		@Override
		public void quickMove(int menuSlot) {
			LocalPlayer player = player();
			if (player == null || Minecraft.getInstance().gameMode == null) {
				return;
			}
			click(player, menuSlot, 0, ContainerInput.QUICK_MOVE);
		}

		@Override
		public ActionResult useBlock(String blockId) {
			LocalPlayer player = player();
			if (player == null || Minecraft.getInstance().gameMode == null) {
				return ActionResult.unavailable("no world");
			}
			BlockPos nearby = nearbyBlock(player, blockId, STATION_BLOCK_RADIUS);
			if (nearby == null) {
				return ActionResult.missing("no " + ItemIds.path(blockId) + " nearby");
			}
			Vec3 hit = Vec3.atCenterOf(nearby).add(0.0, 0.5, 0.0);
			InteractionResult used = Minecraft.getInstance().gameMode.useItemOn(
					player,
					InteractionHand.MAIN_HAND,
					new BlockHitResult(hit, Direction.UP, nearby, false));
			return used.consumesAction() ? ActionResult.pending("opening the " + ItemIds.path(blockId))
					: ActionResult.unavailable("could not open the " + ItemIds.path(blockId));
		}

		@Override
		public ActionResult placeBlock(String blockId, String missingMessage) {
			LocalPlayer player = player();
			if (player == null) {
				return ActionResult.unavailable("no world");
			}
			ActionResult selected = ClientWorldActions.ensureSelected(player, missingMessage, blockId);
			if (selected.status() != ActionResult.Status.OK) {
				return selected;
			}
			return placeHeld(player);
		}

		@Override
		public void closeMenu() {
			LocalPlayer player = player();
			if (player != null && !(player.containerMenu instanceof InventoryMenu)) {
				player.closeContainer();
			}
		}
	}

	private static boolean placedInSlot(Slot slot, String token, int countBefore) {
		ItemStack stack = slot.getItem();
		if (stack.isEmpty()) {
			return false;
		}
		if (!CraftRecipes.matches(itemId(stack), token)) {
			return false;
		}
		return stack.getCount() > countBefore;
	}

	private static ActionResult recoverCarriedCursor(LocalPlayer player) {
		ItemStack carried = player.containerMenu.getCarried();
		if (carried.isEmpty()) {
			return null;
		}
		int empty = findEmptyPlayerSlot(player);
		if (empty < 0) {
			return ActionResult.pending("waiting for inventory space");
		}
		click(player, empty, 0, ContainerInput.PICKUP);
		return ActionResult.pending("clearing container cursor");
	}

	private static int findEmptyPlayerSlot(LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		for (int index = 0; index < menu.slots.size(); index++) {
			Slot slot = menu.getSlot(index);
			if (slot.container == player.getInventory() && slot.getItem().isEmpty()) {
				return index;
			}
		}
		return -1;
	}

	private static boolean slotEmpty(int index) {
		LocalPlayer player = player();
		if (player == null || index < 0 || index >= player.containerMenu.slots.size()) {
			return true;
		}
		return player.containerMenu.getSlot(index).getItem().isEmpty();
	}

	private static int findInventorySlot(LocalPlayer player, String token) {
		AbstractContainerMenu menu = player.containerMenu;
		for (int index = 0; index < menu.slots.size(); index++) {
			Slot slot = menu.getSlot(index);
			if (slot.container != player.getInventory()) {
				continue;
			}
			if (CraftRecipes.matches(itemId(slot.getItem()), token)) {
				return index;
			}
		}
		return -1;
	}

	private static void click(LocalPlayer player, int slot, int button, ContainerInput input) {
		Minecraft.getInstance().gameMode.handleContainerInput(
				player.containerMenu.containerId,
				slot,
				button,
				input,
				player);
	}

	private static ActionResult placeHeld(LocalPlayer player) {
		Direction facing = player.getDirection();
		BlockPos cell = player.blockPosition().relative(facing);
		BlockState state = player.level().getBlockState(cell);
		if (!state.isAir() && !state.canBeReplaced()) {
			return ActionResult.unavailable("could not place the block; the spot is blocked");
		}
		BlockPos clicked = cell.below();
		Direction face = Direction.UP;
		BlockState below = player.level().getBlockState(clicked);
		if (below.isAir() || below.canBeReplaced()) {
			clicked = cell.relative(facing.getOpposite());
			face = facing;
			BlockState behind = player.level().getBlockState(clicked);
			if (behind.isAir() || behind.canBeReplaced()) {
				return ActionResult.unavailable("could not place the block; the spot is blocked");
			}
		}
		Vec3 hit = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		InteractionResult used = Minecraft.getInstance().gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, clicked, false));
		return used.consumesAction() ? ActionResult.ok("placed a block") : ActionResult.unavailable("could not place the block");
	}

	private static BlockPos nearbyBlock(LocalPlayer player, String blockId, int radius) {
		BlockPosView found = ClientWorldActions.findNearbyBlock(blockId, radius);
		if (found == null) {
			return null;
		}
		BlockPos pos = new BlockPos(found.x(), found.y(), found.z());
		if (player.blockPosition().distSqr(pos) > (double) radius * radius) {
			return null;
		}
		return pos;
	}

	private static Map<String, Integer> inventorySnapshot(LocalPlayer player) {
		Map<String, Integer> counts = new HashMap<>();
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			String id = itemId(inventory.getItem(slot));
			if (!id.isEmpty()) {
				counts.merge(id, inventory.getItem(slot).getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private static String itemId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id == null ? "" : id.toString();
	}

	private static LocalPlayer player() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft == null ? null : minecraft.player;
	}
}
