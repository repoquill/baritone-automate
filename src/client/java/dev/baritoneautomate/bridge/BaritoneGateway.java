package dev.baritoneautomate.bridge;

import java.util.List;
import java.util.Optional;

/**
 * The only Baritone wrapper. UI, HUD, and {@code RunController} depend on this type
 * and must not call {@code baritone.api} themselves.
 * <p>
 * All methods are client-thread only. When Baritone is missing, calls return a failed
 * {@link GatewayStatus} or an empty snapshot instead of throwing.
 */
public interface BaritoneGateway {
	boolean isBaritonePresent();

	/** Refresh process completion. Called once per client tick. */
	void tick();

	GatewayStatus pause();

	GatewayStatus resume();

	/** Drop the current process ({@code cancelEverything}) and clear our pause. */
	GatewayStatus cancel();

	/** {@code IPathingBehavior.forceCancel()}, then clear our pause. */
	GatewayStatus forceCancel();

	GatewayStatus customGoal(CustomGoalRequest request);

	GatewayStatus gotoBlock(int x, int y, int z);

	GatewayStatus gotoXZ(int x, int z);

	/** Walk {@code distance} blocks along the player's look, same idea as {@code #thisway}. */
	GatewayStatus thisway(double distance);

	GatewayStatus mine(int quantity, List<String> blockIds);

	GatewayStatus explore(int x, int z);

	/** {@code name} blank means any other player. */
	GatewayStatus followPlayer(String name);

	GatewayStatus followEntity(String entityTypeId);

	GatewayStatus farm(int range);

	/** Advanced/debug path. Leading {@code #} is stripped. */
	GatewayStatus execute(String commandLine);

	List<WaypointView> listWaypoints();

	GatewayStatus saveWaypoint(String name, String tag, int x, int y, int z);

	GatewayStatus gotoWaypoint(String name);

	GatewayStatus deleteWaypoint(String name);

	List<SettingView> getAllSettings();

	Optional<SettingView> getSetting(String name);

	/** Parse with Baritone's setting parser ({@code SettingsUtil.parseAndApply}). */
	GatewayStatus setSetting(String name, String serializedValue);

	GatewayStatus resetSetting(String name);

	/** Reset every Baritone setting. Absent when Baritone is not loaded. */
	GatewayStatus resetAllSettings();

	PathingSnapshot observe();

	ProcessSnapshot currentProcess();
}
