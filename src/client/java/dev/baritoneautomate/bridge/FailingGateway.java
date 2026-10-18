package dev.baritoneautomate.bridge;

import java.util.List;
import java.util.Optional;

/**
 * Shared no-op used when Baritone cannot be called. Subclasses only supply the reason.
 */
abstract class FailingGateway implements BaritoneGateway {
	protected abstract String reason();

	@Override
	public boolean isBaritonePresent() {
		return false;
	}

	@Override
	public void tick() {
	}

	@Override
	public GatewayStatus pause() {
		return fail();
	}

	@Override
	public GatewayStatus resume() {
		return fail();
	}

	@Override
	public GatewayStatus cancel() {
		return fail();
	}

	@Override
	public GatewayStatus forceCancel() {
		return fail();
	}

	@Override
	public GatewayStatus customGoal(CustomGoalRequest request) {
		return fail();
	}

	@Override
	public GatewayStatus gotoBlock(int x, int y, int z) {
		return fail();
	}

	@Override
	public GatewayStatus gotoXZ(int x, int z) {
		return fail();
	}

	@Override
	public GatewayStatus thisway(double distance) {
		return fail();
	}

	@Override
	public GatewayStatus mine(int quantity, List<String> blockIds) {
		return fail();
	}

	@Override
	public GatewayStatus explore(int x, int z) {
		return fail();
	}

	@Override
	public GatewayStatus followPlayer(String name) {
		return fail();
	}

	@Override
	public GatewayStatus followEntity(String entityTypeId) {
		return fail();
	}

	@Override
	public GatewayStatus farm(int range) {
		return fail();
	}

	@Override
	public GatewayStatus execute(String commandLine) {
		return fail();
	}

	@Override
	public List<WaypointView> listWaypoints() {
		return List.of();
	}

	@Override
	public GatewayStatus saveWaypoint(String name, String tag, int x, int y, int z) {
		return fail();
	}

	@Override
	public GatewayStatus gotoWaypoint(String name) {
		return fail();
	}

	@Override
	public GatewayStatus deleteWaypoint(String name) {
		return fail();
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
		return fail();
	}

	@Override
	public GatewayStatus resetSetting(String name) {
		return fail();
	}

	@Override
	public GatewayStatus resetAllSettings() {
		return fail();
	}

	@Override
	public PathingSnapshot observe() {
		return PathingSnapshot.absent(reason());
	}

	@Override
	public ProcessSnapshot currentProcess() {
		return ProcessSnapshot.idle();
	}

	private GatewayStatus fail() {
		return GatewayStatus.fail(reason());
	}
}
