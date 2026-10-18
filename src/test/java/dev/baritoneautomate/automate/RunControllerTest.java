package dev.baritoneautomate.automate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baritoneautomate.bridge.AbsentBaritoneGateway;
import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.bridge.CustomGoalRequest;
import dev.baritoneautomate.bridge.GatewayStatus;
import dev.baritoneautomate.bridge.PathingSnapshot;
import dev.baritoneautomate.bridge.ProcessKind;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.bridge.ProcessSnapshot;
import dev.baritoneautomate.bridge.SettingKind;
import dev.baritoneautomate.bridge.SettingView;
import dev.baritoneautomate.bridge.WaypointView;
import dev.baritoneautomate.combat.CombatMode;
import dev.baritoneautomate.combat.CombatOutcome;
import dev.baritoneautomate.combat.CombatSession;
import dev.baritoneautomate.combat.CombatSnapshot;
import dev.baritoneautomate.inventory.ActionResult;
import dev.baritoneautomate.inventory.InventoryAccess;
import dev.baritoneautomate.inventory.ItemIds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RunControllerTest {
	@Test
	void phaseOrderMatchesTheRoute() {
		assertEquals(
				List.of(
						RunPhase.CHECK,
						RunPhase.EARLY_WOOD,
						RunPhase.IRON,
						RunPhase.DIAMOND,
						RunPhase.NETHER_PORTAL,
						RunPhase.BLAZE_PEARL,
						RunPhase.STRONGHOLD,
						RunPhase.ENTER_END,
						RunPhase.END_APPROACH,
						RunPhase.CRYSTALS,
						RunPhase.DRAGON,
						RunPhase.DONE),
				List.of(RunPhase.values()));
	}

	@Test
	void checkFailsWhenBaritoneIsMissing() {
		Harness harness = Harness.create();
		harness.gateway.present = false;
		assertTrue(harness.runs.start());
		harness.runs.tick();
		assertEquals(RunState.FAILED, harness.runs.snapshot().state());
		assertEquals("Baritone is not installed", harness.runs.snapshot().failureReason());
	}

	@Test
	void woodMineFinishingShortDoesNotAdvance() {
		Harness harness = Harness.create();
		assertTrue(harness.runs.start());
		harness.runs.tick();
		assertEquals(RunPhase.EARLY_WOOD, harness.runs.snapshot().phase());
		assertEquals(1, harness.gateway.mines);
		harness.gateway.completeProcess();
		harness.runs.tick();
		assertEquals(RunPhase.EARLY_WOOD, harness.runs.snapshot().phase());
		assertEquals(2, harness.gateway.mines);
		harness.inventory.set("oak_log", 4);
		harness.runs.tick();
		assertEquals(RunPhase.IRON, harness.runs.snapshot().phase());
		assertEquals(RunState.RUNNING, harness.runs.snapshot().state());
	}

	@Test
	void existingLogsSkipTheMineCall() {
		Harness harness = Harness.create();
		harness.inventory.set("oak_log", 8);
		harness.runs.start();
		harness.runs.tick();
		assertEquals(RunPhase.EARLY_WOOD, harness.runs.snapshot().phase());
		assertEquals(0, harness.gateway.mines);
		harness.runs.tick();
		assertEquals(RunPhase.IRON, harness.runs.snapshot().phase());
	}

	@Test
	void skipDiamondJumpsToThePortal() {
		Harness harness = Harness.create();
		harness.runs.setSkipDiamond(true);
		harness.runs.start();
		harness.runs.tick();
		assertTrue(harness.runs.skipPhase());
		assertEquals(RunPhase.IRON, harness.runs.snapshot().phase());
		assertTrue(harness.runs.skipPhase());
		assertEquals(RunPhase.NETHER_PORTAL, harness.runs.snapshot().phase());
	}

	@Test
	void pauseResumeAndCancel() {
		Harness harness = Harness.create();
		harness.runs.start();
		harness.runs.tick();
		assertTrue(harness.runs.pause());
		harness.clock.now += 3_600_000L;
		harness.runs.tick();
		assertEquals(RunState.PAUSED, harness.runs.snapshot().state());
		assertTrue(harness.runs.resume());
		assertEquals(RunState.RUNNING, harness.runs.snapshot().state());
		harness.runs.cancel();
		assertEquals(RunState.IDLE, harness.runs.snapshot().state());
		assertEquals(RunPhase.CHECK, harness.runs.snapshot().phase());
		assertEquals("cancelled", harness.runs.snapshot().detail());
		assertEquals(1, harness.gateway.forceCancels);
		assertFalse(harness.runs.snapshot().state().active());
		assertTrue(harness.runs.start());
	}

	@Test
	void timeoutRetriesOnceThenFails() {
		Harness harness = Harness.create();
		harness.runs.start();
		harness.clock.now = 21_000L;
		harness.runs.tick();
		assertEquals(RunState.RUNNING, harness.runs.snapshot().state());
		assertEquals(RunPhase.CHECK, harness.runs.snapshot().phase());
		assertEquals(1, harness.runs.snapshot().attempt());
		harness.clock.now = 50_000L;
		harness.runs.tick();
		assertEquals(RunState.FAILED, harness.runs.snapshot().state());
		assertTrue(harness.runs.snapshot().failureReason().contains("timed out"));
	}

	@Test
	void continueAfterFailureReentersThePhase() {
		Harness harness = Harness.create();
		harness.gateway.present = false;
		harness.runs.start();
		harness.runs.tick();
		assertEquals(RunState.FAILED, harness.runs.snapshot().state());
		harness.gateway.present = true;
		assertTrue(harness.runs.continueRun());
		assertEquals(RunPhase.CHECK, harness.runs.snapshot().phase());
		assertEquals(RunState.RUNNING, harness.runs.snapshot().state());
		harness.runs.tick();
		assertEquals(RunPhase.EARLY_WOOD, harness.runs.snapshot().phase());
	}

	@Test
	void crystalsAndDragonHandOffToCombat() {
		Harness harness = Harness.create();
		harness.runs.start();
		harness.runs.tick();
		int guard = 0;
		while (harness.runs.snapshot().phase() != RunPhase.CRYSTALS && guard++ < 12) {
			assertTrue(harness.runs.skipPhase(), "stuck in " + harness.runs.snapshot().phase());
		}
		assertEquals(RunPhase.CRYSTALS, harness.runs.snapshot().phase());
		assertEquals(RunState.COMBAT, harness.runs.snapshot().state());
		assertEquals(1, harness.combat.crystalStarts);
		harness.combat.snap = new CombatSnapshot(CombatMode.CRYSTALS, CombatOutcome.CLEARED, "clear", "");
		harness.runs.tick();
		assertEquals(RunPhase.DRAGON, harness.runs.snapshot().phase());
		assertEquals(1, harness.combat.dragonStarts);
		harness.combat.snap = new CombatSnapshot(CombatMode.DRAGON, CombatOutcome.DONE, "dead", "melee");
		harness.runs.tick();
		assertEquals(RunState.DONE, harness.runs.snapshot().state());
		assertEquals(RunPhase.DONE, harness.runs.snapshot().phase());
	}

	@Test
	void absentGatewayRejectsCalls() {
		BaritoneGateway gateway = new AbsentBaritoneGateway();
		assertFalse(gateway.isBaritonePresent());
		assertFalse(gateway.mine(1, List.of("stone")).ok());
		assertTrue(gateway.getAllSettings().isEmpty());
		assertTrue(gateway.listWaypoints().isEmpty());
		assertTrue(gateway.getSetting("allowBreak").isEmpty());
		assertFalse(gateway.observe().baritonePresent());
		assertFalse(gateway.resetAllSettings().ok());
	}

	@Test
	void itemIdsAndSettingKinds() {
		assertEquals("minecraft:oak_log", ItemIds.normalize("Oak_Log"));
		assertEquals("minecraft:oak_log", ItemIds.normalize("minecraft:Oak_Log"));
		assertTrue(ItemIds.matches("minecraft:bow", "bow"));
		assertEquals(SettingKind.BOOLEAN, SettingKind.of(Boolean.class));
		assertEquals(SettingKind.LIST, SettingKind.of(List.class));
		assertEquals(SettingKind.OTHER, SettingKind.of(StringBuilder.class));
	}

	@Test
	void portalDoesNotAdvanceWhenTheObsidianMineFinishesShort() {
		Harness harness = reach(RunPhase.NETHER_PORTAL);
		assertTrue(harness.gateway.mines >= 1);
		int mines = harness.gateway.mines;
		harness.gateway.completeProcess();
		harness.runs.tick();
		assertEquals(RunPhase.NETHER_PORTAL, harness.runs.snapshot().phase());
		assertEquals(mines + 1, harness.gateway.mines);
		assertEquals(0, harness.world.lights);
	}

	@Test
	void portalAdvancesAfterTheDimensionChanges() {
		Harness harness = reach(RunPhase.NETHER_PORTAL);
		harness.world.dimension = "minecraft:the_nether";
		harness.runs.tick();
		assertEquals(RunPhase.BLAZE_PEARL, harness.runs.snapshot().phase());
	}

	@Test
	void portalTriesToIgniteOnceObsidianIsInInventory() {
		Harness harness = Harness.create();
		harness.runs.setSkipDiamond(true);
		harness.runs.start();
		harness.runs.tick();
		assertTrue(harness.runs.skipPhase());
		harness.inventory.set("obsidian", 10);
		assertTrue(harness.runs.skipPhase());
		assertEquals(RunPhase.NETHER_PORTAL, harness.runs.snapshot().phase());
		harness.runs.tick();
		assertEquals(RunPhase.NETHER_PORTAL, harness.runs.snapshot().phase());
		assertEquals(1, harness.world.lights);
		assertEquals(RunState.RUNNING, harness.runs.snapshot().state());
	}

	@Test
	void blazeFailsInTheOverworldWithoutAPortal() {
		Harness harness = reach(RunPhase.BLAZE_PEARL);
		harness.runs.tick();
		assertEquals(RunState.FAILED, harness.runs.snapshot().state());
		assertTrue(harness.runs.snapshot().failureReason().contains("nether"));
	}

	@Test
	void blazeExploresTheNetherUntilATargetAppears() {
		Harness harness = reach(RunPhase.BLAZE_PEARL);
		harness.world.dimension = "minecraft:the_nether";
		harness.runs.tick();
		assertEquals(RunPhase.BLAZE_PEARL, harness.runs.snapshot().phase());
		assertEquals(RunState.RUNNING, harness.runs.snapshot().state());
		assertTrue(harness.gateway.explores >= 1);
	}

	@Test
	void strongholdFailsWithoutEyes() {
		Harness harness = reach(RunPhase.STRONGHOLD);
		harness.world.dimension = "minecraft:the_nether";
		harness.runs.tick();
		assertEquals(RunState.FAILED, harness.runs.snapshot().state());
		assertTrue(harness.runs.snapshot().failureReason().contains("ender eyes"));
	}

	@Test
	void strongholdAdvancesBesideAPortalFrame() {
		Harness harness = reach(RunPhase.STRONGHOLD);
		harness.world.dimension = "minecraft:overworld";
		harness.world.nearbyBlockId = "end_portal_frame";
		harness.world.nearbyBlock = harness.world.pos;
		harness.runs.tick();
		assertEquals(RunPhase.ENTER_END, harness.runs.snapshot().phase());
	}

	@Test
	void enterEndFailsWhenNoPortalIsNearby() {
		Harness harness = reach(RunPhase.ENTER_END);
		harness.runs.tick();
		assertEquals(RunState.FAILED, harness.runs.snapshot().state());
		assertTrue(harness.runs.snapshot().failureReason().contains("end portal"));
	}

	@Test
	void enterEndAdvancesOnceTheDimensionIsTheEnd() {
		Harness harness = reach(RunPhase.ENTER_END);
		harness.world.dimension = "minecraft:the_end";
		harness.runs.tick();
		assertEquals(RunPhase.END_APPROACH, harness.runs.snapshot().phase());
	}

	private static Harness reach(RunPhase target) {
		Harness harness = Harness.create();
		harness.runs.setSkipDiamond(true);
		assertTrue(harness.runs.start());
		harness.runs.tick();
		int guard = 0;
		while (harness.runs.snapshot().phase() != target && guard++ < 12) {
			assertTrue(harness.runs.skipPhase(), "stuck in " + harness.runs.snapshot().phase());
		}
		assertEquals(target, harness.runs.snapshot().phase());
		return harness;
	}

	private static final class Harness {
		final ManualClock clock = new ManualClock();
		final FakeGateway gateway = new FakeGateway();
		final FakeInventory inventory = new FakeInventory();
		final FakeCombat combat = new FakeCombat();
		final FakeView world = new FakeView();
		final RunController runs;

		private Harness() {
			runs = new RunController(
					gateway,
					inventory,
					combat,
					world,
					clock,
					LogSink.none(),
					TimeoutPolicy.retryOnceThenFail());
		}

		static Harness create() {
			return new Harness();
		}
	}

	private static final class ManualClock implements RunClock {
		long now;

		@Override
		public long millis() {
			return now;
		}
	}

	private static final class FakeView implements ClientView {
		boolean inGame = true;
		boolean alive = true;
		String dimension = "minecraft:overworld";
		BlockPosView pos = new BlockPosView(100, 64, 100);
		String nearbyBlockId;
		BlockPosView nearbyBlock;
		String nearbyEntityId;
		BlockPosView nearbyEntity;
		int lights;
		int uses;
		int attacks;
		ActionResult light = ActionResult.ok("placing the portal frame");
		ActionResult used = ActionResult.ok("used");
		ActionResult attacked = ActionResult.ok("attacked");

		@Override
		public boolean inGame() {
			return inGame;
		}

		@Override
		public boolean alive() {
			return alive;
		}

		@Override
		public float health() {
			return 20.0f;
		}

		@Override
		public String dimensionId() {
			return dimension;
		}

		@Override
		public BlockPosView blockPos() {
			return pos;
		}

		@Override
		public BlockPosView findNearbyBlock(String blockId, int radius) {
			if (nearbyBlock != null && nearbyBlockId != null && ItemIds.matches(nearbyBlockId, blockId)) {
				return nearbyBlock;
			}
			return null;
		}

		@Override
		public BlockPosView findNearbyEntity(String entityId, double radius) {
			if (nearbyEntity != null && nearbyEntityId != null && ItemIds.matches(nearbyEntityId, entityId)) {
				return nearbyEntity;
			}
			return null;
		}

		@Override
		public ActionResult useItem(String itemId) {
			uses++;
			return used;
		}

		@Override
		public ActionResult attackNearest(String entityId, double reach) {
			attacks++;
			return attacked;
		}

		@Override
		public ActionResult lightNetherPortal() {
			lights++;
			return light;
		}
	}

	private static final class FakeInventory implements InventoryAccess {
		private final Map<String, Integer> counts = new HashMap<>();

		void set(String id, int count) {
			counts.put(ItemIds.normalize(id), count);
		}

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public int count(String itemId) {
			return counts.getOrDefault(ItemIds.normalize(itemId), 0);
		}

		@Override
		public int countWhere(Predicate<String> normalizedItemId) {
			int total = 0;
			for (Map.Entry<String, Integer> entry : counts.entrySet()) {
				if (normalizedItemId.test(entry.getKey())) {
					total += entry.getValue();
				}
			}
			return total;
		}

		@Override
		public boolean selectHotbarItem(String itemId) {
			return count(itemId) > 0;
		}

		@Override
		public int selectedHotbarSlot() {
			return 0;
		}

		@Override
		public ActionResult craft(String outputItemId, int count) {
			return ActionResult.unsupported("stub");
		}

		@Override
		public ActionResult smelt(String inputItemId, int count) {
			return ActionResult.unsupported("stub");
		}
	}

	private static final class FakeCombat implements CombatSession {
		int crystalStarts;
		int dragonStarts;
		boolean paused;
		CombatSnapshot snap = CombatSnapshot.idle();

		@Override
		public void startCrystals() {
			crystalStarts++;
			snap = new CombatSnapshot(CombatMode.CRYSTALS, CombatOutcome.RUNNING, "crystals", "search");
		}

		@Override
		public void startDragon() {
			dragonStarts++;
			snap = new CombatSnapshot(CombatMode.DRAGON, CombatOutcome.RUNNING, "dragon", "search");
		}

		@Override
		public void cancel() {
			snap = new CombatSnapshot(CombatMode.IDLE, CombatOutcome.CANCELLED, "cancelled", "");
		}

		@Override
		public void tick() {
		}

		@Override
		public CombatSnapshot snapshot() {
			return snap;
		}

		@Override
		public void setLowHealthHook(Consumer<Float> hook) {
		}

		@Override
		public void setPaused(boolean paused) {
			this.paused = paused;
		}
	}

	private static final class FakeGateway implements BaritoneGateway {
		boolean present = true;
		int mines;
		int explores;
		int forceCancels;
		int cancels;
		long generation;
		ProcessSnapshot process = ProcessSnapshot.idle();
		final List<String> calls = new ArrayList<>();

		void completeProcess() {
			process = new ProcessSnapshot(process.kind(), ProcessOutcome.SUCCEEDED, "done", process.startedAtMillis(), process.generation());
		}

		@Override
		public boolean isBaritonePresent() {
			return present;
		}

		@Override
		public void tick() {
		}

		@Override
		public GatewayStatus pause() {
			calls.add("pause");
			return GatewayStatus.ok("paused");
		}

		@Override
		public GatewayStatus resume() {
			calls.add("resume");
			return GatewayStatus.ok("resumed");
		}

		@Override
		public GatewayStatus cancel() {
			cancels++;
			if (process.outcome() == ProcessOutcome.RUNNING) {
				process = new ProcessSnapshot(process.kind(), ProcessOutcome.CANCELLED, "cancelled", process.startedAtMillis(), process.generation());
			}
			return GatewayStatus.ok("cancelled");
		}

		@Override
		public GatewayStatus forceCancel() {
			forceCancels++;
			return cancel();
		}

		@Override
		public GatewayStatus customGoal(CustomGoalRequest request) {
			return start(ProcessKind.CUSTOM_GOAL, "goal");
		}

		@Override
		public GatewayStatus gotoBlock(int x, int y, int z) {
			return start(ProcessKind.GOTO, "goto");
		}

		@Override
		public GatewayStatus gotoXZ(int x, int z) {
			return start(ProcessKind.GOTO, "goto xz");
		}

		@Override
		public GatewayStatus thisway(double distance) {
			return start(ProcessKind.THISWAY, "thisway");
		}

		@Override
		public GatewayStatus mine(int quantity, List<String> blockIds) {
			mines++;
			return start(ProcessKind.MINE, "mine");
		}

		@Override
		public GatewayStatus explore(int x, int z) {
			explores++;
			return start(ProcessKind.EXPLORE, "explore");
		}

		@Override
		public GatewayStatus followPlayer(String name) {
			return start(ProcessKind.FOLLOW, "follow");
		}

		@Override
		public GatewayStatus followEntity(String entityTypeId) {
			return start(ProcessKind.FOLLOW, "follow");
		}

		@Override
		public GatewayStatus farm(int range) {
			return start(ProcessKind.FARM, "farm");
		}

		@Override
		public GatewayStatus execute(String commandLine) {
			return GatewayStatus.ok("executed");
		}

		@Override
		public List<WaypointView> listWaypoints() {
			return List.of();
		}

		@Override
		public GatewayStatus saveWaypoint(String name, String tag, int x, int y, int z) {
			return GatewayStatus.ok("saved");
		}

		@Override
		public GatewayStatus gotoWaypoint(String name) {
			return start(ProcessKind.GOTO, "waypoint");
		}

		@Override
		public GatewayStatus deleteWaypoint(String name) {
			return GatewayStatus.ok("deleted");
		}

		@Override
		public List<SettingView> getAllSettings() {
			return List.of();
		}

		@Override
		public Optional<SettingView> getSetting(String name) {
			return Optional.empty();
		}

		@Override
		public GatewayStatus setSetting(String name, String serializedValue) {
			return GatewayStatus.ok("set");
		}

		@Override
		public GatewayStatus resetSetting(String name) {
			return GatewayStatus.ok("reset");
		}

		@Override
		public GatewayStatus resetAllSettings() {
			return GatewayStatus.ok("reset all");
		}

		@Override
		public PathingSnapshot observe() {
			return PathingSnapshot.absent("fake");
		}

		@Override
		public ProcessSnapshot currentProcess() {
			return process;
		}

		private GatewayStatus start(ProcessKind kind, String detail) {
			generation++;
			process = new ProcessSnapshot(kind, ProcessOutcome.RUNNING, detail, 0L, generation);
			return GatewayStatus.ok(detail);
		}
	}
}
