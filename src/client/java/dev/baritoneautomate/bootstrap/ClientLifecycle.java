package dev.baritoneautomate.bootstrap;

import dev.baritoneautomate.automate.RunController;
import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.combat.CombatSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
		boolean[] resumeAfterGamePause = {false};
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			guard("disconnect", () -> {
				runs.cancel();
				runs.reset();
				ClientWorldActions.resetPortalPlan();
				resumeAfterGamePause[0] = false;
			}, logger);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			guard("keys", keys::poll, logger);
			if (client.isPaused()) {
				if (runs.pause()) {
					resumeAfterGamePause[0] = true;
				}
				return;
			}
			if (resumeAfterGamePause[0]) {
				runs.resume();
				resumeAfterGamePause[0] = false;
			}
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
