package dev.baritoneautomate.combat;

import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.bridge.CustomGoalRequest;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.inventory.InventoryAccess;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * P0 combat skeleton. Crystals: path in with the gateway, then a client attack.
 * Dragon: melee when it is low and slow, otherwise draw a bow from the hotbar.
 * This is not a combat AI. Bed bombing and PvP are out of scope.
 */
public final class CombatController implements CombatSession {
	public static final float LOW_HEALTH = 6.0f;
	private static final float RECOVERED_HEALTH = 8.0f;
	private static final int[][] RING = {
			{42, 0},
			{33, 24},
			{12, 39},
			{-13, 39},
			{-34, 24},
			{-42, 0},
			{-34, -24},
			{-13, -39},
			{12, -39},
			{33, -24}
	};
	private static final double CRYSTAL_RANGE = 96.0;
	private static final double DRAGON_RANGE = 256.0;

	private final BaritoneGateway gateway;
	private final InventoryAccess inventory;
	private Consumer<Float> lowHealthHook = health -> { };
	private boolean paused;
	private boolean lowHealthLatched;
	private CombatMode mode = CombatMode.IDLE;
	private CombatOutcome outcome = CombatOutcome.IDLE;
	private String detail = "";
	private String approach = "";
	private int ticks;
	private int quietTicks;
	private int lastApproachTick = -1_000;
	private int lastTargetX = Integer.MIN_VALUE;
	private int lastTargetY = Integer.MIN_VALUE;
	private int lastTargetZ = Integer.MIN_VALUE;
	private boolean sawDragon;
	private boolean drawingBow;
	private final boolean[] ringChecked = new boolean[RING.length];
	private int ringCursor;

	public CombatController(BaritoneGateway gateway, InventoryAccess inventory) {
		this.gateway = gateway;
		this.inventory = inventory;
	}

	@Override
	public void startCrystals() {
		begin(CombatMode.CRYSTALS, "searching for end crystals");
	}

	@Override
	public void startDragon() {
		begin(CombatMode.DRAGON, "searching for the dragon");
		sawDragon = false;
	}

	@Override
	public void cancel() {
		stopBow();
		if (mode == CombatMode.IDLE && outcome != CombatOutcome.RUNNING) {
			mode = CombatMode.IDLE;
			paused = false;
			return;
		}
		gateway.cancel();
		mode = CombatMode.IDLE;
		outcome = CombatOutcome.CANCELLED;
		detail = "combat cancelled";
		approach = "";
		paused = false;
	}

	@Override
	public void tick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			return;
		}
		boolean fighting = mode != CombatMode.IDLE && outcome == CombatOutcome.RUNNING;
		if (fighting) {
			watchHealth(player.getHealth());
		} else if (player.getHealth() >= RECOVERED_HEALTH) {
			lowHealthLatched = false;
		}
		if (!fighting || paused) {
			return;
		}
		if (!player.isAlive()) {
			finish(CombatOutcome.FAILED, "player is not alive");
			return;
		}
		ticks++;
		if (!inTheEnd(player)) {
			detail = "waiting for the end";
			approach = "search";
			quietTicks = 0;
			return;
		}
		if (mode == CombatMode.CRYSTALS) {
			tickCrystals(client, player);
		} else {
			tickDragon(client, player);
		}
	}

	@Override
	public CombatSnapshot snapshot() {
		return new CombatSnapshot(mode, outcome, detail, approach);
	}

	@Override
	public void setLowHealthHook(Consumer<Float> hook) {
		lowHealthHook = hook == null ? health -> { } : hook;
	}

	@Override
	public void setPaused(boolean paused) {
		this.paused = paused;
		if (paused) {
			stopBow();
		}
	}

	private void begin(CombatMode next, String text) {
		stopBow();
		mode = next;
		outcome = CombatOutcome.RUNNING;
		detail = text;
		approach = "search";
		ticks = 0;
		quietTicks = 0;
		lastApproachTick = -1_000;
		lastTargetX = Integer.MIN_VALUE;
		paused = false;
		lowHealthLatched = false;
		ringCursor = 0;
		java.util.Arrays.fill(ringChecked, false);
	}

	private void tickCrystals(Minecraft client, LocalPlayer player) {
		EndCrystal crystal = nearest(client, player, EndCrystal.class, CRYSTAL_RANGE, Entity::isAlive);
		if (crystal != null) {
			java.util.Arrays.fill(ringChecked, false);
			quietTicks = 0;
			double distanceSq = player.distanceToSqr(crystal);
			double reach = Math.min(4.5, player.entityInteractionRange());
			if (distanceSq <= reach * reach) {
				approach = "melee";
				detail = "breaking crystal";
				if (ticks % 8 == 0) {
					gateway.cancel();
					client.gameMode.attack(player, crystal);
				}
				return;
			}
			approach = "path";
			detail = "walking to a crystal";
			approachBlock(crystal.blockPosition().getX(), crystal.blockPosition().getY(), crystal.blockPosition().getZ(), 2);
			return;
		}
		int[] sample = RING[ringCursor];
		double dx = player.getX() - sample[0];
		double dz = player.getZ() - sample[1];
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance > 10.0) {
			approach = "path";
			detail = "scanning pillar " + (ringCursor + 1) + "/" + RING.length;
			approachBlock(sample[0], player.blockPosition().getY(), sample[1], 6);
			return;
		}
		ringChecked[ringCursor] = true;
		if (CombatRules.ringCleared(false, ringChecked)) {
			finish(CombatOutcome.CLEARED, "scanned the ring and found no crystals");
			return;
		}
		ringCursor = (ringCursor + 1) % RING.length;
		approach = "search";
		detail = "pillar clear, next " + (ringCursor + 1) + "/" + RING.length;
	}

	private void tickDragon(Minecraft client, LocalPlayer player) {
		EnderDragon dragon = nearest(client, player, EnderDragon.class, DRAGON_RANGE, entity -> true);
		boolean present = dragon != null;
		boolean alive = present && dragon.isAlive() && dragon.getHealth() > 0.0f;
		if (sawDragon) {
			if (!alive) {
				stopBow();
				quietTicks++;
			}
			CombatOutcome decided = CombatRules.engagedDragon(present, alive, quietTicks);
			if (decided != null) {
				finish(decided, "dragon defeated");
				return;
			}
			if (!alive) {
				detail = "dragon gone after engagement";
				approach = "search";
				return;
			}
		} else if (!alive) {
			quietTicks++;
			detail = "dragon not in range";
			approach = "search";
			CombatOutcome missed = CombatRules.unseenDragon(quietTicks);
			if (missed != null) {
				finish(missed, "dragon not found");
			}
			return;
		}
		sawDragon = true;
		quietTicks = 0;
		double dy = dragon.getY() - player.getY();
		boolean perched = dy < 5.0 && dragon.getDeltaMovement().lengthSqr() < 0.1;
		Entity target = dragon.head;
		for (var part : dragon.getSubEntities()) {
			if (player.distanceToSqr(part) < player.distanceToSqr(target)) {
				target = part;
			}
		}
		double reach = Math.min(4.5, player.entityInteractionRange());
		if (perched && player.distanceToSqr(target) <= reach * reach) {
			stopBow();
			approach = "melee";
			detail = "melee on perched dragon";
			if (ticks % 8 == 0) {
				gateway.cancel();
				client.gameMode.attack(player, target);
			}
			return;
		}
		if (!perched && inventory.countAny("arrow", "spectral_arrow", "tipped_arrow") > 0
				&& inventory.selectHotbarItem("bow")) {
			approach = "bow";
			detail = "shooting the dragon";
			gateway.cancel();
			aimAt(player, dragon.head.getBoundingBox().getCenter());
			if (drawingBow && player.isUsingItem() && player.getTicksUsingItem() >= 20) {
				client.gameMode.releaseUsingItem(player);
				drawingBow = false;
			} else if (!player.isUsingItem()) {
				client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
				drawingBow = player.isUsingItem();
			}
			return;
		}
		stopBow();
		approach = "path";
		detail = perched ? "walking to the perched dragon" : "waiting near the fountain for the dragon";
		var pos = perched ? target.blockPosition() : dragon.getFightOrigin();
		approachBlock(pos.getX(), player.blockPosition().getY(), pos.getZ(), perched ? 2 : 6);
	}

	private static void aimAt(LocalPlayer player, Vec3 target) {
		Vec3 direction = target.subtract(player.getEyePosition());
		player.setYRot((float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0));
		player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
	}

	private void stopBow() {
		if (drawingBow) {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null && client.gameMode != null && client.player.isUsingItem()) {
				client.gameMode.releaseUsingItem(client.player);
			}
			drawingBow = false;
		}
	}

	private void approachBlock(int x, int y, int z, int range) {
		boolean same = x == lastTargetX && y == lastTargetY && z == lastTargetZ;
		if (ticks - lastApproachTick < 10
				|| (same && gateway.currentProcess().outcome() == ProcessOutcome.RUNNING)) {
			return;
		}
		lastTargetX = x;
		lastTargetY = y;
		lastTargetZ = z;
		lastApproachTick = ticks;
		var requested = gateway.customGoal(CustomGoalRequest.near(x, y, z, range));
		if (!requested.ok()) {
			finish(CombatOutcome.FAILED, requested.message());
		}
	}

	private void finish(CombatOutcome next, String text) {
		stopBow();
		gateway.cancel();
		mode = CombatMode.IDLE;
		outcome = next;
		detail = text;
		approach = "";
	}

	private void watchHealth(float health) {
		if (health <= LOW_HEALTH && !lowHealthLatched) {
			lowHealthLatched = true;
			lowHealthHook.accept(health);
			return;
		}
		if (health >= RECOVERED_HEALTH) {
			lowHealthLatched = false;
		}
	}

	private static boolean inTheEnd(LocalPlayer player) {
		String id = player.level().dimension().identifier().toString();
		return id.endsWith(":the_end") || id.equals("the_end");
	}

	private static <T extends Entity> T nearest(
			Minecraft client,
			LocalPlayer player,
			Class<T> type,
			double range,
			Predicate<T> filter
	) {
		AABB area = player.getBoundingBox().inflate(range);
		T nearest = null;
		double best = range * range;
		for (T entity : client.level.getEntitiesOfClass(type, area, filter)) {
			double distance = player.distanceToSqr(entity);
			if (distance < best) {
				best = distance;
				nearest = entity;
			}
		}
		return nearest;
	}
}
