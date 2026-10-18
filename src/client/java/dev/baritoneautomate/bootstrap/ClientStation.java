package dev.baritoneautomate.bootstrap;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.inventory.ActionResult;
import dev.baritoneautomate.inventory.CraftRecipes;
import dev.baritoneautomate.inventory.ItemIds;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
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
 * Opens a crafting grid or furnace and moves items one step per call.
 * Success is returned only after the player inventory count goes up.
 */
public final class ClientStation {
	private static final int CRAFT_BUDGET = 240;
	private static final int RESULT_WAIT = 30;

	private static Job job;

	private ClientStation() {
	}

	public static ActionResult craft(String outputId, int count) {
		LocalPlayer player = player();
		if (player == null) {
			clear();
			return ActionResult.unavailable("no world");
		}
		String output = ItemIds.normalize(outputId);
		int times = Math.max(1, count);
		int have = count(player, output);
		if (job == null || job.smelt || !output.equals(job.output)) {
			ActionResult preview = CraftRecipes.craft(snapshot(player), output, times, tableReady(player));
			if (preview.status() != ActionResult.Status.OK) {
				clear();
				return preview;
			}
			job = new Job(output, have + times, false, "", 0);
		}
		if (count(player, output) >= job.target) {
			closeCrafting(player);
			clear();
			return ActionResult.ok("crafted " + ItemIds.path(output));
		}
		if (job.ticks++ > CRAFT_BUDGET) {
			closeCrafting(player);
			clear();
			return ActionResult.unavailable("crafting did not produce " + ItemIds.path(output));
		}
		return stepCraft(player);
	}

	public static ActionResult smelt(String inputId, int count) {
		LocalPlayer player = player();
		if (player == null) {
			clear();
			return ActionResult.unavailable("no world");
		}
		if (!"raw_iron".equals(ItemIds.path(inputId))) {
			return ActionResult.unsupported("no smelt recipe for " + ItemIds.path(inputId));
		}
		int times = Math.max(1, count);
		int have = count(player, "iron_ingot");
		if (job == null || !job.smelt || !ItemIds.matches(job.input, inputId)) {
			ActionResult preview = CraftRecipes.smelt(snapshot(player), inputId, times, furnaceReady(player), tableReady(player));
			if (preview.status() != ActionResult.Status.OK) {
				clear();
				return preview;
			}
			job = new Job("minecraft:iron_ingot", have + times, true, ItemIds.normalize(inputId), times);
		}
		if (count(player, "iron_ingot") >= job.target) {
			closeCrafting(player);
			clear();
			return ActionResult.ok("smelted iron_ingot");
		}
		if (job.ticks++ > 220L * times + 200L) {
			closeCrafting(player);
			clear();
			return ActionResult.unavailable("furnace did not produce iron_ingot");
		}
		if (!furnaceReady(player) && count(player, "furnace") <= 0) {
			return craftNested(player, "furnace");
		}
		return stepFurnace(player);
	}

	private static ActionResult craftNested(LocalPlayer player, String output) {
		if (job.nested == null || !output.equals(job.nested.output)) {
			job.nested = new Job(ItemIds.normalize(output), count(player, output) + 1, false, "", 0);
		}
		if (count(player, output) >= job.nested.target) {
			job.nested = null;
			return ActionResult.pending("crafted " + ItemIds.path(output));
		}
		Job outer = job;
		job = outer.nested;
		ActionResult step = stepCraft(player);
		outer.nested = job;
		job = outer;
		if (step.status() == ActionResult.Status.OK) {
			return ActionResult.pending(step.message());
		}
		if (step.status() != ActionResult.Status.PENDING) {
			clear();
		}
		return step;
	}

	private static ActionResult stepCraft(LocalPlayer player) {
		CraftRecipes.Recipe recipe = CraftRecipes.recipe(job.focus().output);
		if (recipe == null) {
			clear();
			return ActionResult.unsupported("no recipe for " + ItemIds.path(job.focus().output));
		}
		if (!recipe.outputId().equals(job.output) && count(player, recipe.outputId()) >= job.focus().target) {
			job.focusStack.pop();
			job.cell = 0;
			job.wait = 0;
			return ActionResult.pending("crafted " + ItemIds.path(recipe.outputId()));
		}
		if (job.cell == 0) {
			for (CraftRecipes.Need need : recipe.needs()) {
				if (CraftRecipes.countToken(snapshot(player), need.token()) >= need.count()) {
					continue;
				}
				String producer = producer(need.token());
				if (producer == null) {
					clear();
					return ActionResult.missing("missing " + ItemIds.path(need.token()));
				}
				if (!job.crafting(producer)) {
					CraftRecipes.Recipe made = CraftRecipes.recipe(producer);
					int batch = made == null ? 1 : made.outputCount();
					job.focusStack.push(new Focus(producer, count(player, producer) + batch));
					job.cell = 0;
					job.wait = 0;
				}
				return ActionResult.pending("crafting " + ItemIds.path(producer));
			}
		}
		if (recipe.needsTable()) {
			ActionResult opened = openTable(player);
			if (opened != null) {
				return opened;
			}
		} else if (!(player.containerMenu instanceof InventoryMenu)) {
			player.closeContainer();
			return ActionResult.pending("closing the container");
		}
		if (job.cell < recipe.grid().size()) {
			String token = recipe.grid().get(job.cell);
			int width = recipe.gridWidth();
			int x = job.cell % width;
			int y = job.cell / width;
			job.cell++;
			if (token != null && !token.isBlank()) {
				int craftSlot = 1 + x + y * width;
				ActionResult placed = placeOne(player, token, craftSlot);
				if (placed.status() != ActionResult.Status.OK) {
					return placed;
				}
				return ActionResult.pending("placing " + ItemIds.path(token));
			}
			return ActionResult.pending("crafting " + ItemIds.path(recipe.outputId()));
		}
		Slot result = player.containerMenu.getSlot(0);
		if (result.getItem().isEmpty()) {
			if (job.wait++ > RESULT_WAIT) {
				String produced = ItemIds.path(recipe.outputId());
				clear();
				return ActionResult.unavailable("recipe did not produce " + produced);
			}
			return ActionResult.pending("waiting for " + ItemIds.path(recipe.outputId()));
		}
		click(player, 0, 0, ContainerInput.QUICK_MOVE);
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

	private static ActionResult openTable(LocalPlayer player) {
		if (player.containerMenu instanceof CraftingMenu) {
			return null;
		}
		BlockPos nearby = nearbyBlock(player, "crafting_table", 4);
		if (nearby != null) {
			useBlock(player, nearby);
			return ActionResult.pending("opening the crafting table");
		}
		if (count(player, "crafting_table") > 0) {
			ActionResult selected = ClientWorldActions.ensureSelected(player, "no crafting table in the inventory", "crafting_table");
			if (selected.status() != ActionResult.Status.OK) {
				return selected;
			}
			ActionResult placed = placeHeld(player);
			if (placed.status() != ActionResult.Status.OK) {
				return placed;
			}
			return ActionResult.pending("placing a crafting table");
		}
		if (!job.crafting("minecraft:crafting_table") && !ItemIds.matches(job.focus().output, "crafting_table")) {
			job.focusStack.push(new Focus("minecraft:crafting_table", count(player, "crafting_table") + 1));
			job.cell = 0;
			job.wait = 0;
			return ActionResult.pending("crafting a crafting table");
		}
		clear();
		return ActionResult.unavailable("no crafting table nearby");
	}

	private static ActionResult stepFurnace(LocalPlayer player) {
		BlockPos nearby = nearbyBlock(player, "furnace", 4);
		if (!(player.containerMenu instanceof AbstractFurnaceMenu)) {
			if (nearby != null) {
				useBlock(player, nearby);
				return ActionResult.pending("opening the furnace");
			}
			if (count(player, "furnace") <= 0) {
				clear();
				return ActionResult.unavailable("no furnace nearby");
			}
			ActionResult selected = ClientWorldActions.ensureSelected(player, "no furnace in the inventory", "furnace");
			if (selected.status() != ActionResult.Status.OK) {
				return selected;
			}
			ActionResult placed = placeHeld(player);
			if (placed.status() != ActionResult.Status.OK) {
				return placed;
			}
			return ActionResult.pending("placing a furnace");
		}
		AbstractFurnaceMenu menu = (AbstractFurnaceMenu) player.containerMenu;
		if (menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem().isEmpty()) {
			job.wait = 0;
			ActionResult placed = placeOne(player, "raw_iron", AbstractFurnaceMenu.INGREDIENT_SLOT);
			if (placed.status() != ActionResult.Status.OK) {
				return placed;
			}
			return ActionResult.pending("putting raw iron in the furnace");
		}
		if (menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT).getItem().isEmpty()) {
			String fuel = fuelToken(player);
			if (fuel == null) {
				clear();
				return ActionResult.missing("missing fuel");
			}
			ActionResult placed = placeOne(player, fuel, AbstractFurnaceMenu.FUEL_SLOT);
			if (placed.status() != ActionResult.Status.OK) {
				return placed;
			}
			return ActionResult.pending("adding fuel");
		}
		if (!menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).getItem().isEmpty()) {
			click(player, AbstractFurnaceMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
			return ActionResult.pending("taking iron ingots");
		}
		if (!menu.isLit() && job.wait++ > 40) {
			clear();
			return ActionResult.unavailable("furnace is not burning");
		}
		return ActionResult.pending("smelting raw iron");
	}

	private static String fuelToken(LocalPlayer player) {
		Map<String, Integer> counts = snapshot(player);
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

	private static ActionResult placeOne(LocalPlayer player, String token, int destination) {
		int source = findInventorySlot(player, token);
		if (source < 0) {
			return ActionResult.missing("missing " + ItemIds.path(token));
		}
		click(player, source, 0, ContainerInput.PICKUP);
		click(player, destination, 1, ContainerInput.PICKUP);
		click(player, source, 0, ContainerInput.PICKUP);
		return ActionResult.ok("placed " + ItemIds.path(token));
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
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.gameMode.handleContainerInput(player.containerMenu.containerId, slot, button, input, player);
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
		Minecraft.getInstance().gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, clicked, false));
		return ActionResult.ok("placed a block");
	}

	private static void useBlock(LocalPlayer player, BlockPos pos) {
		Vec3 hit = Vec3.atCenterOf(pos);
		Minecraft.getInstance().gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, pos, false));
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

	private static boolean tableReady(LocalPlayer player) {
		return nearbyBlock(player, "crafting_table", 4) != null || count(player, "crafting_table") > 0;
	}

	private static boolean furnaceReady(LocalPlayer player) {
		return nearbyBlock(player, "furnace", 4) != null || count(player, "furnace") > 0;
	}

	private static void closeCrafting(LocalPlayer player) {
		if (!(player.containerMenu instanceof InventoryMenu)) {
			player.closeContainer();
		}
	}

	private static int count(LocalPlayer player, String itemId) {
		return CraftRecipes.countToken(snapshot(player), itemId);
	}

	private static Map<String, Integer> snapshot(LocalPlayer player) {
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

	private static void clear() {
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

		private Job(String output, int target, boolean smelt, String input, int ignored) {
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
