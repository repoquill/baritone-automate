package dev.baritoneautomate.bootstrap;

import dev.baritoneautomate.automate.LogSink;
import dev.baritoneautomate.automate.RunController;
import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.bridge.BaritoneGateways;
import dev.baritoneautomate.combat.CombatController;
import dev.baritoneautomate.combat.CombatSession;
import dev.baritoneautomate.inventory.InventoryAccess;
import dev.baritoneautomate.inventory.PlayerInventoryAccess;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry. UI screens are not created here.
 * Frontend code should take {@link #gateway()}, {@link #runs()}, {@link #combat()}, and {@link #inventory()}.
 */
public final class BaritoneAutomateClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("baritone-automate");

	private static BaritoneGateway gateway;
	private static RunController runs;
	private static CombatController combat;
	private static InventoryAccess inventory;
	private static KeybindStubs keys;

	@Override
	public void onInitializeClient() {
		gateway = BaritoneGateways.open(LOGGER);
		inventory = new PlayerInventoryAccess();
		combat = new CombatController(gateway, inventory);
		runs = RunController.createLive(gateway, inventory, combat, new MinecraftClientView(), new LogSink() {
			@Override
			public void info(String message) {
				LOGGER.info(message);
			}

			@Override
			public void warn(String message) {
				LOGGER.warn(message);
			}
		});
		combat.setLowHealthHook(health -> runs.pauseFor("low health " + health));
		keys = new KeybindStubs(runs, LOGGER);
		keys.register();
		ClientLifecycle.install(gateway, runs, combat, keys, LOGGER);
		if (!gateway.isBaritonePresent()) {
			LOGGER.warn("Baritone is not ready yet. The gateway will keep trying; pathing calls fail until it is.");
		}
		LOGGER.info("Baritone Automate backend ready. Control screen, settings, and HUD are left to the UI teammate.");
	}

	public static BaritoneGateway gateway() {
		return require(gateway, "gateway");
	}

	public static RunController runs() {
		return require(runs, "run controller");
	}

	public static CombatSession combat() {
		return require(combat, "combat");
	}

	public static InventoryAccess inventory() {
		return require(inventory, "inventory");
	}

	public static KeybindStubs keys() {
		return require(keys, "keybinds");
	}

	private static <T> T require(T value, String name) {
		if (value == null) {
			throw new IllegalStateException(name + " is not initialized");
		}
		return value;
	}
}
