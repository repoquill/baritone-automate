package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.Dimensions;
import dev.baritoneautomate.automate.PhaseContext;
import dev.baritoneautomate.automate.PhaseHandler;
import dev.baritoneautomate.automate.PhaseTick;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.bridge.GatewayStatus;
import dev.baritoneautomate.bridge.ProcessKind;
import dev.baritoneautomate.bridge.ProcessOutcome;
import dev.baritoneautomate.bridge.ProcessSnapshot;
import dev.baritoneautomate.combat.CombatOutcome;
import dev.baritoneautomate.combat.CombatSnapshot;
import java.util.List;

/**
 * Back half of the route. Blaze/pearl, stronghold, and entering the end are stubs
 * that still sit on the chain. Crystals and the dragon call {@code CombatSession}.
 */
public final class LatePhases {
	private static final long FIFTEEN_MINUTES = 15 * 60_000L;
	private static final long FIVE_MINUTES = 5 * 60_000L;
	private static final long FOUR_MINUTES = 4 * 60_000L;
	private static final long SIX_MINUTES = 6 * 60_000L;

	private LatePhases() {
	}

	public static List<PhaseHandler> all() {
		return List.of(blazePearl(), stronghold(), enterEnd(), endApproach(), crystals(), dragon());
	}

	private static PhaseHandler blazePearl() {
		return waiting(
				RunPhase.BLAZE_PEARL,
				FIFTEEN_MINUTES,
				"stub: waiting for blaze_rod >= 6 and ender_pearl >= 12, or skip",
				context -> Dimensions.isEnd(context.world().dimensionId())
						|| (context.inventory().count("blaze_rod") >= 6 && context.inventory().count("ender_pearl") >= 12),
				"blaze rods and ender pearls ready");
	}

	private static PhaseHandler stronghold() {
		return waiting(
				RunPhase.STRONGHOLD,
				FIFTEEN_MINUTES,
				"stub: eye throw and stronghold path are not implemented; skip or enter the end",
				context -> Dimensions.isEnd(context.world().dimensionId()),
				"already in the end");
	}

	private static PhaseHandler enterEnd() {
		return waiting(
				RunPhase.ENTER_END,
				FIFTEEN_MINUTES,
				"stub: waiting for dimension minecraft:the_end",
				context -> Dimensions.isEnd(context.world().dimensionId()),
				"in the end");
	}

	private static PhaseHandler endApproach() {
		return new PhaseHandler() {
			private boolean requested;
			private String error;
			private long tracked = -1L;

			@Override
			public RunPhase phase() {
				return RunPhase.END_APPROACH;
			}

			@Override
			public void onEnter(PhaseContext context) {
				requested = false;
				error = null;
				tracked = -1L;
			}

			@Override
			public PhaseTick onTick(PhaseContext context) {
				if (!Dimensions.isEnd(context.world().dimensionId())) {
					return PhaseTick.cont("stub: not in the end yet");
				}
				if (error != null) {
					return PhaseTick.fail(error);
				}
				if (!requested) {
					GatewayStatus status = context.gateway().gotoXZ(0, 0);
					requested = true;
					if (!status.ok()) {
						error = status.message();
						return PhaseTick.fail(error);
					}
					tracked = context.gateway().currentProcess().generation();
				}
				BlockPosView pos = context.world().blockPos();
				if (pos != null && pos.horizontalDistanceTo(0, 0) <= 32.0) {
					return PhaseTick.advance("near 0, 0");
				}
				ProcessSnapshot process = context.gateway().currentProcess();
				if (process.generation() == tracked && process.kind() == ProcessKind.GOTO) {
					if (process.outcome() == ProcessOutcome.SUCCEEDED) {
						return PhaseTick.advance("reached the end approach goal");
					}
					if (process.outcome() == ProcessOutcome.FAILED) {
						return PhaseTick.fail(process.detail().isBlank() ? "end approach failed" : process.detail());
					}
				}
				return PhaseTick.cont("pathing toward 0, 0");
			}

			@Override
			public long timeoutMillis() {
				return FIVE_MINUTES;
			}
		};
	}

	private static PhaseHandler crystals() {
		return new PhaseHandler() {
			@Override
			public RunPhase phase() {
				return RunPhase.CRYSTALS;
			}

			@Override
			public void onEnter(PhaseContext context) {
				context.combat().startCrystals();
			}

			@Override
			public PhaseTick onTick(PhaseContext context) {
				return combatTick(context, CombatOutcome.CLEARED, "crystals cleared");
			}

			@Override
			public long timeoutMillis() {
				return FOUR_MINUTES;
			}
		};
	}

	private static PhaseHandler dragon() {
		return new PhaseHandler() {
			@Override
			public RunPhase phase() {
				return RunPhase.DRAGON;
			}

			@Override
			public void onEnter(PhaseContext context) {
				context.combat().startDragon();
			}

			@Override
			public PhaseTick onTick(PhaseContext context) {
				return combatTick(context, CombatOutcome.DONE, "dragon defeated");
			}

			@Override
			public long timeoutMillis() {
				return SIX_MINUTES;
			}
		};
	}

	private static PhaseTick combatTick(PhaseContext context, CombatOutcome success, String successDetail) {
		CombatSnapshot combat = context.combat().snapshot();
		return switch (combat.outcome()) {
			case CLEARED, DONE -> combat.outcome() == success
					? PhaseTick.advance(successDetail)
					: PhaseTick.cont(combat.detail());
			case FAILED, CANCELLED -> PhaseTick.fail(combat.detail().isBlank() ? "combat failed" : combat.detail());
			case IDLE, RUNNING -> PhaseTick.cont(combat.detail().isBlank() ? "in combat" : combat.detail());
		};
	}

	private static PhaseHandler waiting(
			RunPhase phase,
			long timeoutMillis,
			String waitingDetail,
			java.util.function.Predicate<PhaseContext> done,
			String doneDetail
	) {
		return new PhaseHandler() {
			@Override
			public RunPhase phase() {
				return phase;
			}

			@Override
			public void onEnter(PhaseContext context) {
			}

			@Override
			public PhaseTick onTick(PhaseContext context) {
				if (done.test(context)) {
					return PhaseTick.advance(doneDetail);
				}
				return PhaseTick.cont(waitingDetail);
			}

			@Override
			public long timeoutMillis() {
				return timeoutMillis;
			}
		};
	}
}
