package dev.baritoneautomate.bootstrap;

import dev.baritoneautomate.automate.RunController;
import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.combat.CombatSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;

/** One client tick: keys, gateway observation, combat, then the run state machine. */
public final class ClientLifecycle {
	private ClientLifecycle() {
	}

	public static void install(
			BaritoneGateway gateway,
			RunController runs,
			CombatSession combat,
			KeybindStubs keys,
			Logger logger
	) {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			guard("keys", keys::poll, logger);
			guard("gateway", gateway::tick, logger);
			guard("combat", combat::tick, logger);
			guard("run", runs::tick, logger);
		});
	}

	private static void guard(String label, Runnable action, Logger logger) {
		try {
			action.run();
		} catch (Throwable failure) {
			logger.warn("Client tick failed in {}", label, failure);
		}
	}
}
