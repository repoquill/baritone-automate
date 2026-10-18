package dev.baritoneautomate.combat;

import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.bridge.CustomGoalRequest;
import dev.baritoneautomate.inventory.InventoryAccess;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;

/**
 * P0 combat skeleton. Crystals: path in with the gateway, then a client attack.
 * Dragon: melee when it is low and slow, otherwise draw a bow from the hotbar.
 * This is not a combat AI. Bed bombing and PvP are out of scope.
 */
public final class CombatController implements CombatSession {
	public static final float LOW_HEALTH = 6.0f;
	private static final float RECOVERED_HEALTH = 8.0f;
	private static final int CRYSTAL_CLEAR_TICKS = 80;
	private static final int DRAGON_SEARCH_TICKS = 200;
	private static final double CRYSTAL_RANGE = 160.0;
	private static final double DRAGON_RANGE = 256.0;
	private static final double MELEE_DISTANCE_SQ = 4.5 * 4.5;

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
		if (mode == CombatMode.IDLE && outcome != CombatOutcome.RUNNING) {
			mode = CombatMode.IDLE;
			return;
		}
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
	}

	private void begin(CombatMode next, String text) {
		mode = next;
		outcome = CombatOutcome.RUNNING;
		detail = text;
		approach = "search";
		ticks = 0;
		quietTicks = 0;
		lastApproachTick = -1_000;
		lastTargetX = Integer.MIN_VALUE;
		paused = false;
	}

	private void tickCrystals(Minecraft client, LocalPlayer player) {
		EndCrystal crystal = nearest(client, player, EndCrystal.class, CRYSTAL_RANGE);
		if (crystal == null) {
			quietTicks++;
			detail = "no end crystal in range";
			approach = "search";
			if (quietTicks >= CRYSTAL_CLEAR_TICKS) {
				finish(CombatOutcome.CLEARED, "end crystals cleared");
			}
			return;
		}
		quietTicks = 0;
		double distanceSq = player.distanceToSqr(crystal);
		if (distanceSq <= MELEE_DISTANCE_SQ) {
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
	}

	private void tickDragon(Minecraft client, LocalPlayer player) {
		EnderDragon dragon = nearest(client, player, EnderDragon.class, DRAGON_RANGE);
		if (dragon == null || !dragon.isAlive() || dragon.getHealth() <= 0.0f) {
			if (dragon != null && sawDragon) {
				finish(CombatOutcome.DONE, "dragon defeated");
				return;
			}
			quietTicks++;
			detail = "dragon not in range";
			approach = "search";
			if (quietTicks >= DRAGON_SEARCH_TICKS) {
				finish(CombatOutcome.FAILED, "dragon not found");
			}
			return;
		}
		sawDragon = true;
		quietTicks = 0;
		double dy = dragon.getY() - player.getY();
		boolean perched = dy < 5.0 && dragon.getDeltaMovement().lengthSqr() < 0.1;
		double distanceSq = player.distanceToSqr(dragon);
		if (perched && distanceSq <= MELEE_DISTANCE_SQ) {
			approach = "melee";
			detail = "melee on perched dragon";
			if (ticks % 8 == 0) {
				gateway.cancel();
				client.gameMode.attack(player, dragon);
			}
			return;
		}
		if (!perched && inventory.selectHotbarItem("bow")) {
			approach = "bow";
			detail = "shooting the dragon";
			if (ticks % 15 == 0) {
				client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			}
			return;
		}
		approach = "path";
		detail = perched ? "walking to the perched dragon" : "no bow in the hotbar; closing in";
		var pos = dragon.blockPosition();
		approachBlock(pos.getX(), pos.getY(), pos.getZ(), 2);
	}

	private void approachBlock(int x, int y, int z, int range) {
		boolean same = x == lastTargetX && y == lastTargetY && z == lastTargetZ;
		if (same && ticks - lastApproachTick < 40 && gateway.observe().pathing()) {
			return;
		}
		lastTargetX = x;
		lastTargetY = y;
		lastTargetZ = z;
		lastApproachTick = ticks;
		gateway.customGoal(CustomGoalRequest.near(x, y, z, range));
	}

	private void finish(CombatOutcome next, String text) {
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

	private static <T extends Entity> T nearest(Minecraft client, LocalPlayer player, Class<T> type, double range) {
		AABB area = player.getBoundingBox().inflate(range);
		T nearest = null;
		double best = Double.MAX_VALUE;
		for (T entity : client.level.getEntitiesOfClass(type, area, Entity::isAlive)) {
			double distance = player.distanceToSqr(entity);
			if (distance < best) {
				best = distance;
				nearest = entity;
			}
		}
		return nearest;
	}
}
