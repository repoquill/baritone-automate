package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.Dimensions;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.bridge.GatewayStatus;
import dev.baritoneautomate.bridge.ProcessKind;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.bridge.ProcessSnapshot;
import dev.baritoneautomate.inventory.ItemIds;
import java.util.List;

public final class EarlyPhases {
	private static final long THREE_MINUTES = 3 * 60_000L;
	private static final long EIGHT_MINUTES = 8 * 60_000L;
	private static final long TEN_MINUTES = 10 * 60_000L;
	private static final List<String> LOGS = List.of(
			"oak_log",
			"spruce_log",
			"birch_log",
			"jungle_log",
			"acacia_log",
			"dark_oak_log",
			"mangrove_log",
			"cherry_log");

	private EarlyPhases() {
	}

	public static List<PhaseHandler> all() {
		return List.of(check(), wood(), iron(), diamond(), netherPortal());
	}

	private static PhaseHandler check() {
		return new PhaseHandler() {
			@Override
			public RunPhase phase() {
				return RunPhase.CHECK;
			}

			@Override
			public void onEnter(PhaseContext context) {
			}

			@Override
			public PhaseTick onTick(PhaseContext context) {
				if (!context.gateway().isBaritonePresent()) {
					return PhaseTick.fail("Baritone is not installed");
				}
				if (!context.world().inGame()) {
					return PhaseTick.cont("waiting for a world");
				}
				if (!context.world().alive()) {
					return PhaseTick.fail("player is not alive");
				}
				return PhaseTick.advance("ready");
			}

			@Override
			public long timeoutMillis() {
				return 20_000L;
			}
		};
	}

	private static PhaseHandler wood() {
		return new GatherPhase(
				RunPhase.EARLY_WOOD,
				16,
				LOGS,
				context -> logCount(context) >= 4,
				"mining logs",
				"logs gathered",
				THREE_MINUTES);
	}

	private static PhaseHandler iron() {
		return new GatherPhase(
				RunPhase.IRON,
				16,
				List.of("iron_ore", "deepslate_iron_ore"),
				context -> context.inventory().countAny("raw_iron", "iron_ingot", "iron_ore", "deepslate_iron_ore") > 0,
				"mining iron",
				"iron step finished; smelting is an inventory stub",
				EIGHT_MINUTES);
	}

	private static PhaseHandler diamond() {
		return new GatherPhase(
				RunPhase.DIAMOND,
				8,
				List.of("diamond_ore", "deepslate_diamond_ore"),
				context -> context.inventory().countAny("diamond", "diamond_ore", "deepslate_diamond_ore") > 0,
				"mining diamonds",
				"diamond step finished",
				TEN_MINUTES);
	}

	private static PhaseHandler netherPortal() {
		return new PhaseHandler() {
			private String error;
			private long tracked = -1L;

			@Override
			public RunPhase phase() {
				return RunPhase.NETHER_PORTAL;
			}

			@Override
			public void onEnter(PhaseContext context) {
				error = null;
				tracked = -1L;
				if (leftOverworld(context)) {
					return;
				}
				if (context.inventory().count("obsidian") >= 10) {
					return;
				}
				GatewayStatus status = context.gateway().mine(10, List.of("obsidian"));
				if (!status.ok()) {
					error = status.message();
					return;
				}
				tracked = context.gateway().currentProcess().generation();
			}

			@Override
			public PhaseTick onTick(PhaseContext context) {
				if (error != null) {
					return PhaseTick.fail(error);
				}
				if (leftOverworld(context)) {
					return PhaseTick.advance("already left the overworld");
				}
				if (context.inventory().count("obsidian") >= 10) {
					return PhaseTick.advance("obsidian gathered; portal ignition is an inventory stub");
				}
				ProcessSnapshot process = context.gateway().currentProcess();
				if (process.generation() == tracked && tracked >= 0L && process.kind() == ProcessKind.MINE) {
					if (process.outcome() == ProcessOutcome.SUCCEEDED) {
						return PhaseTick.advance("obsidian mine finished; portal ignition is an inventory stub");
					}
					if (process.outcome() == ProcessOutcome.FAILED) {
						return PhaseTick.fail(process.detail().isBlank() ? "obsidian mine failed" : process.detail());
					}
				}
				return PhaseTick.cont("mining obsidian");
			}

			@Override
			public long timeoutMillis() {
				return TEN_MINUTES;
			}
		};
	}

	private static boolean leftOverworld(PhaseContext context) {
		String dimension = context.world().dimensionId();
		return Dimensions.isNether(dimension) || Dimensions.isEnd(dimension);
	}

	private static int logCount(PhaseContext context) {
		return context.inventory().countWhere(id -> {
			String path = ItemIds.path(id);
			return path.endsWith("_log") || path.endsWith("_stem");
		});
	}
}
