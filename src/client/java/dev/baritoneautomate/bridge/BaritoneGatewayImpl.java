package dev.baritoneautomate.bridge;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.cache.IWaypoint;
import baritone.api.cache.IWorldData;
import baritone.api.cache.Waypoint;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.SettingsUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * Real Baritone calls. Pause is our own high-priority process that returns
 * {@link PathingCommandType#REQUEST_PAUSE}. Baritone's {@code #pause} command is
 * intentionally private and not the API other mods are supposed to use.
 */
final class BaritoneGatewayImpl implements BaritoneGateway {
	private static final int COMPLETION_GRACE_TICKS = 5;
	private static final String ETA_NOTE = "heuristic; estimatedTicksToGoal and segment ticks are Baritone estimates, not wall-clock";

	private final Logger logger;
	private IBaritone bound;
	private boolean pauseRequested;
	private ProcessKind kind = ProcessKind.NONE;
	private ProcessOutcome outcome = ProcessOutcome.IDLE;
	private String detail = "";
	private long startedAtMillis;
	private long generation;
	private int inactiveTicks;
	private Goal lastGoal;
	private boolean loggedNotReady;

	BaritoneGatewayImpl(Logger logger) {
		this.logger = logger;
	}

	@Override
	public boolean isBaritonePresent() {
		try {
			return BaritoneAPI.getProvider() != null && BaritoneAPI.getProvider().getPrimaryBaritone() != null;
		} catch (Throwable ignored) {
			return false;
		}
	}

	@Override
	public void tick() {
		if (outcome != ProcessOutcome.RUNNING) {
			return;
		}
		try {
			observeRunning(require());
		} catch (Throwable failure) {
			if (!loggedNotReady) {
				loggedNotReady = true;
				logger.warn("Baritone observation skipped: {}", failure.toString());
			}
		}
	}

	@Override
	public GatewayStatus pause() {
		return call(baritone -> {
			pauseRequested = true;
			return GatewayStatus.ok("pause requested");
		});
	}

	@Override
	public GatewayStatus resume() {
		pauseRequested = false;
		return call(baritone -> {
			try {
				baritone.getBuilderProcess().resume();
			} catch (Throwable ignored) {
				// Builder pause is independent. Pathing pause is our process flag.
			}
			return GatewayStatus.ok("resumed");
		});
	}

	@Override
	public GatewayStatus cancel() {
		pauseRequested = false;
		noteCancelled();
		return call(baritone -> {
			releaseProcesses(baritone);
			baritone.getPathingBehavior().cancelEverything();
			return GatewayStatus.ok("cancelled");
		});
	}

	@Override
	public GatewayStatus forceCancel() {
		pauseRequested = false;
		noteCancelled();
		return call(baritone -> {
			releaseProcesses(baritone);
			baritone.getPathingBehavior().forceCancel();
			return GatewayStatus.ok("force cancelled");
		});
	}

	@Override
	public GatewayStatus customGoal(CustomGoalRequest request) {
		if (request == null) {
			return GatewayStatus.fail("goal is missing");
		}
		return call(baritone -> {
			Goal goal = toGoal(request);
			startGoal(baritone, ProcessKind.CUSTOM_GOAL, goal, request.kind().name());
			return GatewayStatus.ok("custom goal");
		});
	}

	@Override
	public GatewayStatus gotoBlock(int x, int y, int z) {
		return call(baritone -> {
			GoalBlock goal = new GoalBlock(x, y, z);
			startGoal(baritone, ProcessKind.GOTO, goal, goal.toString());
			return GatewayStatus.ok("goto");
		});
	}

	@Override
	public GatewayStatus gotoXZ(int x, int z) {
		return call(baritone -> {
			GoalXZ goal = new GoalXZ(x, z);
			startGoal(baritone, ProcessKind.GOTO, goal, goal.toString());
			return GatewayStatus.ok("goto xz");
		});
	}

	@Override
	public GatewayStatus thisway(double distance) {
		if (!Double.isFinite(distance) || distance <= 0.0) {
			return GatewayStatus.fail("distance must be positive");
		}
		return call(baritone -> {
			LocalPlayer player = baritone.getPlayerContext().player();
			if (player == null) {
				return GatewayStatus.fail("player not loaded");
			}
			GoalXZ goal = GoalXZ.fromDirection(player.position(), player.getYRot(), distance);
			startGoal(baritone, ProcessKind.THISWAY, goal, "thisway " + distance);
			return GatewayStatus.ok("thisway");
		});
	}

	@Override
	public GatewayStatus mine(int quantity, List<String> blockIds) {
		String[] names = blockNames(blockIds);
		if (names.length == 0) {
			return GatewayStatus.fail("no blocks");
		}
		int count = Math.max(quantity, 1);
		return call(baritone -> {
			prepareProcess(baritone);
			baritone.getMineProcess().mineByName(count, names);
			begin(ProcessKind.MINE, "mine " + String.join(",", names), null);
			return GatewayStatus.ok("mining");
		});
	}

	@Override
	public GatewayStatus explore(int x, int z) {
		return explore(x, z, 0);
	}

	/**
	 * {@code IExploreProcess.explore(int, int)} only takes a center. A positive range is
	 * written as {@code [{"x":chunkX,"z":chunkZ},...]} and applied with
	 * {@code applyJsonFilter(path, true)}, which treats listed chunks as still unexplored
	 * and every other chunk as already explored. Range {@code 0} writes {@code []} with
	 * invert false so a previous radius does not stick.
	 */
	@Override
	public GatewayStatus explore(int x, int z, int rangeBlocks) {
		if (rangeBlocks < 0) {
			return GatewayStatus.fail("explore range must be zero or positive");
		}
		if (rangeBlocks > ExploreRange.MAX_BLOCKS) {
			return GatewayStatus.fail("explore range is too large");
		}
		return call(baritone -> {
			try {
				Path filter = writeExploreFilter(x, z, rangeBlocks);
				prepareProcess(baritone);
				baritone.getExploreProcess().explore(x, z);
				baritone.getExploreProcess().applyJsonFilter(filter, rangeBlocks > 0);
			} catch (Throwable failure) {
				return GatewayStatus.fail(message(failure));
			}
			String text = rangeBlocks > 0
					? "explore " + x + " " + z + " radius " + rangeBlocks
					: "explore " + x + " " + z;
			begin(ProcessKind.EXPLORE, text, null);
			return GatewayStatus.ok(text);
		});
	}

	@Override
	public GatewayStatus followPlayer(String name) {
		return call(baritone -> {
			LocalPlayer self = baritone.getPlayerContext().player();
			if (self == null) {
				return GatewayStatus.fail("player not loaded");
			}
			String wanted = name == null ? "" : name.trim();
			prepareProcess(baritone);
			baritone.getFollowProcess().follow(entity -> matchesPlayer(entity, self, wanted));
			begin(ProcessKind.FOLLOW, wanted.isEmpty() ? "follow player" : "follow " + wanted, null);
			return GatewayStatus.ok("following");
		});
	}

	@Override
	public GatewayStatus followEntity(String entityTypeId) {
		EntityType<?> type;
		try {
			type = entityType(entityTypeId);
		} catch (RuntimeException failure) {
			return GatewayStatus.fail(failure.getMessage());
		}
		if (type == null) {
			return GatewayStatus.fail("unknown entity type " + entityTypeId);
		}
		return call(baritone -> {
			prepareProcess(baritone);
			baritone.getFollowProcess().follow(entity -> entity.getType() == type);
			begin(ProcessKind.FOLLOW, "follow " + entityTypeId, null);
			return GatewayStatus.ok("following");
		});
	}

	@Override
	public GatewayStatus farm(int range) {
		int size = Math.max(range, 1);
		return call(baritone -> {
			prepareProcess(baritone);
			baritone.getFarmProcess().farm(size);
			begin(ProcessKind.FARM, "farm " + size, null);
			return GatewayStatus.ok("farming");
		});
	}

	@Override
	public GatewayStatus execute(String commandLine) {
		if (commandLine == null || commandLine.isBlank()) {
			return GatewayStatus.fail("empty command");
		}
		String line = commandLine.trim();
		if (line.startsWith("#")) {
			line = line.substring(1).trim();
		}
		if (line.isEmpty()) {
			return GatewayStatus.fail("empty command");
		}
		String command = line;
		return call(baritone -> {
			boolean accepted = baritone.getCommandManager().execute(command);
			return accepted ? GatewayStatus.ok("executed") : GatewayStatus.fail("command rejected");
		});
	}

	@Override
	public List<WaypointView> listWaypoints() {
		try {
			IWorldData world = require().getWorldProvider().getCurrentWorld();
			if (world == null) {
				return List.of();
			}
			List<WaypointView> views = new ArrayList<>();
			for (IWaypoint waypoint : world.getWaypoints().getAllWaypoints()) {
				BetterBlockPos pos = waypoint.getLocation();
				views.add(new WaypointView(
						waypoint.getName(),
						waypoint.getTag().getName(),
						pos.x,
						pos.y,
						pos.z,
						waypoint.getCreationTimestamp()));
			}
			views.sort(Comparator.comparing(WaypointView::name).thenComparingLong(WaypointView::createdAtMillis));
			return List.copyOf(views);
		} catch (Throwable failure) {
			return List.of();
		}
	}

	@Override
	public GatewayStatus saveWaypoint(String name, String tag, int x, int y, int z) {
		if (name == null || name.isBlank()) {
			return GatewayStatus.fail("waypoint name is blank");
		}
		return call(baritone -> {
			IWorldData world = baritone.getWorldProvider().getCurrentWorld();
			if (world == null) {
				return GatewayStatus.fail("world not loaded");
			}
			world.getWaypoints().addWaypoint(new Waypoint(name.trim(), tag(tag), new BetterBlockPos(x, y, z)));
			return GatewayStatus.ok("waypoint saved");
		});
	}

	@Override
	public GatewayStatus gotoWaypoint(String name) {
		if (name == null || name.isBlank()) {
			return GatewayStatus.fail("waypoint name is blank");
		}
		return call(baritone -> {
			IWaypoint waypoint = newestNamed(baritone, name.trim());
			if (waypoint == null) {
				return GatewayStatus.fail("waypoint not found");
			}
			GoalBlock goal = new GoalBlock(waypoint.getLocation());
			startGoal(baritone, ProcessKind.GOTO, goal, "waypoint " + waypoint.getName());
			return GatewayStatus.ok("goto waypoint");
		});
	}

	@Override
	public GatewayStatus deleteWaypoint(String name) {
		if (name == null || name.isBlank()) {
			return GatewayStatus.fail("waypoint name is blank");
		}
		return call(baritone -> {
			IWorldData world = baritone.getWorldProvider().getCurrentWorld();
			if (world == null) {
				return GatewayStatus.fail("world not loaded");
			}
			String wanted = name.trim();
			int removed = 0;
			for (IWaypoint waypoint : Set.copyOf(world.getWaypoints().getAllWaypoints())) {
				if (waypoint.getName().equalsIgnoreCase(wanted)) {
					world.getWaypoints().removeWaypoint(waypoint);
					removed++;
				}
			}
			return removed == 0 ? GatewayStatus.fail("waypoint not found") : GatewayStatus.ok("deleted " + removed);
		});
	}

	@Override
	public List<SettingView> getAllSettings() {
		try {
			Settings settings = BaritoneAPI.getSettings();
			Set<String> modified = new HashSet<>();
			for (Settings.Setting<?> setting : SettingsUtil.modifiedSettings(settings)) {
				modified.add(setting.getName());
			}
			List<SettingView> views = new ArrayList<>();
			for (Settings.Setting<?> setting : settings.allSettings) {
				views.add(describe(setting, modified.contains(setting.getName())));
			}
			views.sort(Comparator.comparing(SettingView::name));
			return List.copyOf(views);
		} catch (Throwable failure) {
			return List.of();
		}
	}

	@Override
	public Optional<SettingView> getSetting(String name) {
		try {
			Settings.Setting<?> setting = findSetting(name);
			if (setting == null) {
				return Optional.empty();
			}
			boolean modified = false;
			for (Settings.Setting<?> candidate : SettingsUtil.modifiedSettings(BaritoneAPI.getSettings())) {
				if (candidate.getName().equals(setting.getName())) {
					modified = true;
					break;
				}
			}
			return Optional.of(describe(setting, modified));
		} catch (Throwable failure) {
			return Optional.empty();
		}
	}

	@Override
	public GatewayStatus setSetting(String name, String serializedValue) {
		if (serializedValue == null) {
			return GatewayStatus.fail("value is null");
		}
		return call(baritone -> {
			Settings.Setting<?> setting = findSetting(name);
			if (setting == null) {
				return GatewayStatus.fail("unknown setting");
			}
			SettingKind settingKind = SettingKind.of(setting.getValueClass());
			if ((settingKind == SettingKind.INTEGER || settingKind == SettingKind.LONG
					|| settingKind == SettingKind.FLOAT || settingKind == SettingKind.DOUBLE)
					&& !SettingNumbers.valid(settingKind, serializedValue)) {
				return GatewayStatus.fail("invalid numeric setting value");
			}
			SettingsUtil.parseAndApply(BaritoneAPI.getSettings(), setting.getName(), serializedValue);
			SettingsUtil.save(BaritoneAPI.getSettings());
			return GatewayStatus.ok("setting updated");
		});
	}

	@Override
	public GatewayStatus resetSetting(String name) {
		return call(baritone -> {
			Settings.Setting<?> setting = findSetting(name);
			if (setting == null) {
				return GatewayStatus.fail("unknown setting");
			}
			setting.reset();
			SettingsUtil.save(BaritoneAPI.getSettings());
			return GatewayStatus.ok("setting reset");
		});
	}

	@Override
	public GatewayStatus resetAllSettings() {
		return call(baritone -> {
			int count = 0;
			for (Settings.Setting<?> setting : BaritoneAPI.getSettings().allSettings) {
				setting.reset();
				count++;
			}
			SettingsUtil.save(BaritoneAPI.getSettings());
			return GatewayStatus.ok("reset " + count + " settings");
		});
	}

	@Override
	public PathingSnapshot observe() {
		try {
			IBaritone baritone = require();
			var pathing = baritone.getPathingBehavior();
			String process = baritone.getPathingControlManager().mostRecentInControl()
					.map(IBaritoneProcess::displayName)
					.orElse("");
			Goal goal = pathing.getGoal();
			Double eta = pathing.estimatedTicksToGoal().orElse(null);
			Double segment = pathing.ticksRemainingInSegment().orElse(null);
			return new PathingSnapshot(
					true,
					pathing.isPathing(),
					pauseRequested,
					process,
					goal == null ? "" : goal.toString(),
					eta,
					segment,
					ETA_NOTE);
		} catch (Throwable failure) {
			return PathingSnapshot.absent(message(failure));
		}
	}

	@Override
	public ProcessSnapshot currentProcess() {
		return new ProcessSnapshot(kind, outcome, detail, startedAtMillis, generation);
	}

	private void startGoal(IBaritone baritone, ProcessKind processKind, Goal goal, String text) {
		prepareProcess(baritone);
		baritone.getCustomGoalProcess().setGoalAndPath(goal);
		begin(processKind, text, goal);
	}

	private void prepareProcess(IBaritone baritone) {
		pauseRequested = false;
		noteCancelled();
		releaseProcesses(baritone);
		baritone.getPathingBehavior().cancelEverything();
	}

	private void begin(ProcessKind processKind, String text, Goal goal) {
		generation++;
		kind = processKind;
		outcome = ProcessOutcome.RUNNING;
		detail = text == null ? "" : text;
		startedAtMillis = System.currentTimeMillis();
		inactiveTicks = 0;
		lastGoal = goal;
		loggedNotReady = false;
	}

	private void noteCancelled() {
		if (outcome == ProcessOutcome.RUNNING) {
			outcome = ProcessOutcome.CANCELLED;
			detail = "cancelled";
		}
	}

	private void observeRunning(IBaritone baritone) {
		if (stillActive(baritone)) {
			inactiveTicks = 0;
			return;
		}
		if (inactiveTicks < COMPLETION_GRACE_TICKS) {
			inactiveTicks++;
			return;
		}
		if (kind == ProcessKind.CUSTOM_GOAL || kind == ProcessKind.GOTO || kind == ProcessKind.THISWAY) {
			finishGoal(baritone);
			return;
		}
		if (kind == ProcessKind.MINE) {
			outcome = ProcessOutcome.SUCCEEDED;
			detail = "mine finished";
			return;
		}
		if (kind == ProcessKind.EXPLORE || kind == ProcessKind.FOLLOW || kind == ProcessKind.FARM) {
			outcome = ProcessOutcome.SUCCEEDED;
			detail = kind.name().toLowerCase(Locale.ROOT) + " finished";
			return;
		}
		outcome = ProcessOutcome.FAILED;
		detail = kind.name().toLowerCase(Locale.ROOT) + " stopped";
	}

	private void finishGoal(IBaritone baritone) {
		try {
			BetterBlockPos feet = baritone.getPlayerContext().playerFeet();
			if (lastGoal != null && feet != null && lastGoal.isInGoal(feet)) {
				outcome = ProcessOutcome.SUCCEEDED;
				detail = "reached goal";
				return;
			}
		} catch (Throwable ignored) {
			// Player context can be empty between worlds.
		}
		outcome = ProcessOutcome.FAILED;
		detail = "stopped before the goal";
	}

	private boolean stillActive(IBaritone baritone) {
		return switch (kind) {
			case MINE -> baritone.getMineProcess().isActive();
			case EXPLORE -> baritone.getExploreProcess().isActive();
			case FOLLOW -> baritone.getFollowProcess().isActive();
			case FARM -> baritone.getFarmProcess().isActive();
			case CUSTOM_GOAL, GOTO, THISWAY -> baritone.getCustomGoalProcess().isActive();
			case NONE -> false;
		};
	}

	private IBaritone require() {
		IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		if (baritone == null) {
			throw new IllegalStateException("Primary Baritone is null");
		}
		if (baritone != bound) {
			baritone.getPathingControlManager().registerProcess(new PauseProcess(baritone, () -> pauseRequested));
			bound = baritone;
			logger.info("Registered pause process on the primary Baritone.");
		}
		return baritone;
	}

	private GatewayStatus call(BaritoneCall call) {
		try {
			return call.run(require());
		} catch (Throwable failure) {
			return GatewayStatus.fail(message(failure));
		}
	}

	private static void releaseProcesses(IBaritone baritone) {
		runQuiet(() -> baritone.getMineProcess().cancel());
		runQuiet(() -> baritone.getFollowProcess().cancel());
		runQuiet(() -> baritone.getCustomGoalProcess().onLostControl());
		runQuiet(() -> baritone.getExploreProcess().onLostControl());
		runQuiet(() -> baritone.getFarmProcess().onLostControl());
		runQuiet(() -> baritone.getGetToBlockProcess().onLostControl());
		runQuiet(() -> baritone.getBuilderProcess().onLostControl());
	}

	private static void runQuiet(Runnable action) {
		try {
			action.run();
		} catch (Throwable ignored) {
		}
	}

	private static Goal toGoal(CustomGoalRequest request) {
		return switch (request.kind()) {
			case BLOCK -> new GoalBlock(request.x(), request.y(), request.z());
			case XZ -> new GoalXZ(request.x(), request.z());
			case NEAR -> new GoalNear(new BlockPos(request.x(), request.y(), request.z()), Math.max(request.range(), 1));
			case Y_LEVEL -> new GoalYLevel(request.y());
			case TWO_BLOCKS -> new GoalTwoBlocks(request.x(), request.y(), request.z());
			case GET_TO_BLOCK -> new GoalGetToBlock(new BlockPos(request.x(), request.y(), request.z()));
		};
	}

	private static boolean matchesPlayer(Entity entity, LocalPlayer self, String wanted) {
		if (!(entity instanceof Player) || entity == self) {
			return false;
		}
		if (wanted.isEmpty()) {
			return true;
		}
		return entity.getName().getString().equalsIgnoreCase(wanted);
	}

	private static EntityType<?> entityType(String entityTypeId) {
		if (entityTypeId == null || entityTypeId.isBlank()) {
			throw new IllegalArgumentException("entity type is blank");
		}
		Identifier id = Identifier.parse(normalizeId(entityTypeId));
		return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
	}

	private static IWaypoint newestNamed(IBaritone baritone, String name) {
		IWorldData world = baritone.getWorldProvider().getCurrentWorld();
		if (world == null) {
			return null;
		}
		IWaypoint newest = null;
		for (IWaypoint waypoint : world.getWaypoints().getAllWaypoints()) {
			if (!waypoint.getName().equalsIgnoreCase(name)) {
				continue;
			}
			if (newest == null || waypoint.getCreationTimestamp() >= newest.getCreationTimestamp()) {
				newest = waypoint;
			}
		}
		return newest;
	}

	private static IWaypoint.Tag tag(String name) {
		if (name == null || name.isBlank()) {
			return IWaypoint.Tag.USER;
		}
		IWaypoint.Tag parsed = IWaypoint.Tag.getByName(name.trim());
		if (parsed != null) {
			return parsed;
		}
		try {
			return IWaypoint.Tag.valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return IWaypoint.Tag.USER;
		}
	}

	private static Settings.Setting<?> findSetting(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		return BaritoneAPI.getSettings().byLowerName.get(name.trim().toLowerCase(Locale.ROOT));
	}

	private static SettingView describe(Settings.Setting<?> setting, boolean modified) {
		Class<?> valueClass = setting.getValueClass();
		return new SettingView(
				setting.getName(),
				SettingKind.of(valueClass),
				valueClass == null ? "" : valueClass.getName(),
				String.valueOf(setting.getType()),
				safeText(() -> SettingsUtil.settingValueToString(setting), setting.value),
				safeText(() -> SettingsUtil.settingDefaultToString(setting), setting.defaultValue),
				setting.isJavaOnly(),
				modified,
				SettingDescriptions.of(setting.getName()),
				SettingEnums.names(valueClass));
	}

	private static String safeText(SettingText text, Object fallback) {
		try {
			String value = text.get();
			return value == null ? String.valueOf(fallback) : value;
		} catch (Throwable ignored) {
			return String.valueOf(fallback);
		}
	}

	private static Path writeExploreFilter(int x, int z, int rangeBlocks) throws IOException {
		Path dir = Path.of(System.getProperty("java.io.tmpdir"), "baritone-automate");
		Files.createDirectories(dir);
		Path file = Files.createTempFile(dir, "explore-filter-", ".json");
		file.toFile().deleteOnExit();
		String json = rangeBlocks > 0 ? ExploreRange.json(x, z, rangeBlocks) : "[]";
		Files.writeString(file, json);
		return file;
	}

	private static String[] blockNames(List<String> blockIds) {
		if (blockIds == null || blockIds.isEmpty()) {
			return new String[0];
		}
		return blockIds.stream()
				.filter(name -> name != null && !name.isBlank())
				.map(String::trim)
				.toArray(String[]::new);
	}

	private static String normalizeId(String id) {
		String trimmed = id.trim().toLowerCase(Locale.ROOT);
		return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
	}

	private static String message(Throwable failure) {
		String text = failure.getMessage();
		return text == null || text.isBlank() ? failure.toString() : text;
	}

	@FunctionalInterface
	private interface BaritoneCall {
		GatewayStatus run(IBaritone baritone);
	}

	@FunctionalInterface
	private interface SettingText {
		String get();
	}

	/**
	 * Same shape as Baritone's own pause command: a temporary process above the default
	 * priority that asks pathing to pause and releases movement keys.
	 */
	private static final class PauseProcess implements IBaritoneProcess {
		private final IBaritone baritone;
		private final java.util.function.BooleanSupplier paused;

		private PauseProcess(IBaritone baritone, java.util.function.BooleanSupplier paused) {
			this.baritone = baritone;
			this.paused = paused;
		}

		@Override
		public boolean isActive() {
			return paused.getAsBoolean();
		}

		@Override
		public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
			baritone.getInputOverrideHandler().clearAllKeys();
			return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
		}

		@Override
		public boolean isTemporary() {
			return true;
		}

		@Override
		public void onLostControl() {
		}

		@Override
		public double priority() {
			return DEFAULT_PRIORITY + 1.0;
		}

		@Override
		public String displayName0() {
			return "Baritone Automate Pause";
		}
	}
}
