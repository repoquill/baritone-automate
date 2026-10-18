package dev.baritoneautomate.bootstrap;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.inventory.ActionResult;
import dev.baritoneautomate.inventory.ItemIds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Client scans and the one-block portal ignition step. Not used by unit tests. */
public final class ClientWorldActions {
	private static final int MAX_BLOCK_RADIUS = 16;

	private ClientWorldActions() {
	}

	public static BlockPosView findNearbyBlock(String blockId, int radius) {
		LocalPlayer player = player();
		if (player == null || player.level() == null) {
			return null;
		}
		Block block = block(blockId);
		if (block == null) {
			return null;
		}
		int span = Math.max(1, Math.min(radius, MAX_BLOCK_RADIUS));
		BlockPos origin = player.blockPosition();
		BlockPosView closest = null;
		double best = Double.MAX_VALUE;
		for (int dy = -span; dy <= span; dy++) {
			for (int dx = -span; dx <= span; dx++) {
				for (int dz = -span; dz <= span; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					if (player.level().getBlockState(pos).getBlock() != block) {
						continue;
					}
					double distance = origin.distSqr(pos);
					if (distance < best) {
						best = distance;
						closest = new BlockPosView(pos.getX(), pos.getY(), pos.getZ());
					}
				}
			}
		}
		return closest;
	}

	public static BlockPosView findNearbyEntity(String entityId, double radius) {
		LocalPlayer player = player();
		Entity entity = nearest(player, entityId, radius, Entity::isAlive);
		if (entity == null) {
			return null;
		}
		BlockPos pos = entity.blockPosition();
		return new BlockPosView(pos.getX(), pos.getY(), pos.getZ());
	}

	public static ActionResult useItem(String itemId) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = player();
		if (player == null || minecraft.gameMode == null) {
			return ActionResult.unavailable("no world");
		}
		if (!selectHotbar(player, itemId)) {
			return ActionResult.missing("put " + itemId + " on the hotbar");
		}
		minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		return ActionResult.ok("used " + itemId);
	}

	public static ActionResult attackNearest(String entityId, double reach) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = player();
		if (player == null || minecraft.gameMode == null) {
			return ActionResult.unavailable("no world");
		}
		Entity entity = nearest(player, entityId, reach, Entity::isAlive);
		if (entity == null) {
			return ActionResult.missing("no " + entityId + " in reach");
		}
		minecraft.gameMode.attack(player, entity);
		return ActionResult.ok("attacked " + entityId);
	}

	public static ActionResult lightNetherPortal() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = player();
		if (player == null || player.level() == null || minecraft.gameMode == null) {
			return ActionResult.unavailable("no world");
		}
		if (findNearbyBlock("nether_portal", 8) != null) {
			return ActionResult.ok("portal is already lit");
		}
		if (!hotbarHas(player, "flint_and_steel") && !hotbarHas(player, "fire_charge")) {
			return ActionResult.missing("put flint and steel or a fire charge on the hotbar");
		}
		if (!hotbarHas(player, "obsidian")) {
			return ActionResult.missing("put obsidian on the hotbar");
		}
		List<BlockPos> frame = frame(player);
		for (BlockPos cell : frame) {
			BlockState state = player.level().getBlockState(cell);
			if (state.getBlock() == Blocks.OBSIDIAN) {
				continue;
			}
			if (!state.isAir() && !state.canBeReplaced()) {
				return ActionResult.unavailable("portal site is blocked at " + cell.getX() + " " + cell.getY() + " " + cell.getZ());
			}
			if (!placeAgainstNeighbor(minecraft, player, cell)) {
				return ActionResult.unavailable("could not place obsidian");
			}
			return ActionResult.ok("placing the portal frame");
		}
		if (!selectHotbar(player, "flint_and_steel") && !selectHotbar(player, "fire_charge")) {
			return ActionResult.missing("put flint and steel or a fire charge on the hotbar");
		}
		BlockPos base = frameBase(player);
		Vec3 hit = Vec3.atCenterOf(base).add(0.0, 0.5, 0.0);
		minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, base, false));
		return ActionResult.ok("used the igniter");
	}

	private static boolean placeAgainstNeighbor(Minecraft minecraft, LocalPlayer player, BlockPos cell) {
		if (!selectHotbar(player, "obsidian")) {
			return false;
		}
		BlockPos clicked = cell.below();
		Direction face = Direction.UP;
		BlockState below = player.level().getBlockState(clicked);
		if (below.isAir() || below.canBeReplaced()) {
			clicked = null;
			for (Direction direction : Direction.values()) {
				if (direction.getAxis().isVertical()) {
					continue;
				}
				BlockPos neighbor = cell.relative(direction);
				BlockState state = player.level().getBlockState(neighbor);
				if (!state.isAir() && !state.canBeReplaced()) {
					clicked = neighbor;
					face = direction.getOpposite();
					break;
				}
			}
			if (clicked == null) {
				clicked = cell.below();
				face = Direction.UP;
			}
		}
		Vec3 hit = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, clicked, false));
		return true;
	}

	/** Vanilla minimum frame: 4 by 5 with the four corners omitted, ten obsidian. */
	private static List<BlockPos> frame(LocalPlayer player) {
		Direction facing = player.getDirection();
		Direction right = facing.getClockWise();
		BlockPos corner = player.blockPosition().relative(facing, 2);
		List<BlockPos> cells = new ArrayList<>();
		for (int dy = 0; dy <= 4; dy++) {
			for (int dx = 0; dx <= 3; dx++) {
				boolean isCorner = (dx == 0 || dx == 3) && (dy == 0 || dy == 4);
				boolean interior = dx != 0 && dx != 3 && dy != 0 && dy != 4;
				if (isCorner || interior) {
					continue;
				}
				cells.add(corner.relative(right, dx).above(dy));
			}
		}
		return cells;
	}

	private static BlockPos frameBase(LocalPlayer player) {
		Direction facing = player.getDirection();
		Direction right = facing.getClockWise();
		return player.blockPosition().relative(facing, 2).relative(right, 1);
	}

	private static Entity nearest(LocalPlayer player, String entityId, double radius, Predicate<Entity> filter) {
		if (player == null || player.level() == null || radius <= 0.0) {
			return null;
		}
		AABB area = player.getBoundingBox().inflate(radius);
		Entity closest = null;
		double best = Double.MAX_VALUE;
		for (Entity entity : player.level().getEntities(player, area, entity -> filter.test(entity) && matchesEntity(entity, entityId))) {
			double distance = player.distanceToSqr(entity);
			if (distance < best) {
				best = distance;
				closest = entity;
			}
		}
		return closest;
	}

	private static boolean matchesEntity(Entity entity, String entityId) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return id != null && ItemIds.matches(id.toString(), entityId);
	}

	private static boolean hotbarHas(LocalPlayer player, String itemId) {
		return hotbarSlot(player, itemId) >= 0;
	}

	private static boolean selectHotbar(LocalPlayer player, String itemId) {
		int slot = hotbarSlot(player, itemId);
		if (slot < 0) {
			return false;
		}
		player.getInventory().setSelectedSlot(slot);
		return true;
	}

	private static int hotbarSlot(LocalPlayer player, String itemId) {
		Inventory inventory = player.getInventory();
		int hotbar = Math.min(9, inventory.getContainerSize());
		for (int slot = 0; slot < hotbar; slot++) {
			if (matchesItem(inventory.getItem(slot), itemId)) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean matchesItem(ItemStack stack, String itemId) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id != null && ItemIds.matches(id.toString(), itemId);
	}

	private static Block block(String blockId) {
		Identifier id;
		try {
			id = Identifier.parse(ItemIds.normalize(blockId));
		} catch (RuntimeException exception) {
			return null;
		}
		return BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
	}

	private static LocalPlayer player() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft == null ? null : minecraft.player;
	}
}
