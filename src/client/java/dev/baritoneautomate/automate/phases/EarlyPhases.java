package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
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
				"iron gathered",
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
		return new NetherPortalPhase(TEN_MINUTES);
	}

	private static int logCount(PhaseContext context) {
		return context.inventory().countWhere(id -> {
			String path = ItemIds.path(id);
			return path.endsWith("_log") || path.endsWith("_stem");
		});
	}
}
