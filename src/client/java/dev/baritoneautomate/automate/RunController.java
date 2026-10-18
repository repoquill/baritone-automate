package dev.baritoneautomate.automate;

import dev.baritoneautomate.automate.phases.EarlyPhases;
import dev.baritoneautomate.automate.phases.LatePhases;
import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.combat.CombatSession;
import dev.baritoneautomate.inventory.InventoryAccess;
import java.util.EnumMap;
import java.util.List;

/**
 * Speedrun-shaped state machine. It only orchestrates the gateway, inventory, and combat.
 * Cancel stops combat, clears our pause, force-cancels Baritone, and lands on {@link RunState#IDLE}.
 * {@link #start()} accepts that idle state and any other state that is not active.
 */
public final class RunController {
	private final BaritoneGateway gateway;
	private final InventoryAccess inventory;
	private final CombatSession combat;
	private final ClientView world;
	private final RunClock clock;
	private final LogSink log;
	private final EnumMap<RunPhase, PhaseHandler> handlers;
	private TimeoutPolicy timeoutPolicy;
	private RunState state = RunState.IDLE;
	private RunPhase phase = RunPhase.CHECK;
	private RunState resumeState = RunState.RUNNING;
	private int attempt;
	private long phaseStartedAt;
	private String detail = "";
	private String failureReason = "";
	private boolean skipDiamond;

	public RunController(
			BaritoneGateway gateway,
			InventoryAccess inventory,
			CombatSession combat,
			ClientView world,
			RunClock clock,
			LogSink log,
			TimeoutPolicy timeoutPolicy
	) {
		this.gateway = gateway;
		this.inventory = inventory;
		this.combat = combat;
		this.world = world;
		this.clock = clock;
		this.log = log == null ? LogSink.none() : log;
		this.timeoutPolicy = timeoutPolicy == null ? TimeoutPolicy.retryOnceThenFail() : timeoutPolicy;
		this.handlers = index(EarlyPhases.all(), LatePhases.all());
	}

	public static RunController createLive(
			BaritoneGateway gateway,
			InventoryAccess inventory,
			CombatSession combat,
			ClientView world,
			LogSink log
	) {
		return new RunController(
				gateway,
				inventory,
				combat,
				world,
				System::currentTimeMillis,
				log,
				TimeoutPolicy.retryOnceThenFail());
	}

	public void setTimeoutPolicy(TimeoutPolicy timeoutPolicy) {
		if (timeoutPolicy != null) {
			this.timeoutPolicy = timeoutPolicy;
		}
	}

	public void setSkipDiamond(boolean skipDiamond) {
		this.skipDiamond = skipDiamond;
	}

	public boolean start() {
		if (state.active()) {
			return false;
		}
		combat.cancel();
		gateway.cancel();
		failureReason = "";
		detail = "starting";
		log.info("Run starting at CHECK");
		enter(RunPhase.CHECK);
		return true;
	}

	public boolean pause() {
		if (state != RunState.RUNNING && state != RunState.COMBAT) {
			return false;
		}
		resumeState = state;
		gateway.pause();
		combat.setPaused(true);
		state = RunState.PAUSED;
		log.info("Run paused in " + phase);
		return true;
	}

	/** Pause from the low-health hook. Keeps the reason even if already paused. */
	public boolean pauseFor(String reason) {
		boolean changed = pause();
		if (reason != null && !reason.isBlank()) {
			detail = reason;
		}
		return changed || state == RunState.PAUSED;
	}

	public boolean resume() {
		if (state != RunState.PAUSED) {
			return false;
		}
		gateway.resume();
		combat.setPaused(false);
		state = resumeState == RunState.PAUSED ? RunState.RUNNING : resumeState;
		phaseStartedAt = clock.millis();
		log.info("Run resumed in " + phase);
		return true;
	}

	/**
	 * From {@link RunState#PAUSED}, same as {@link #resume()}.
	 * From {@link RunState#FAILED}, re-enter the current phase so the player can continue after a manual fix.
	 */
	public boolean continueRun() {
		if (state == RunState.PAUSED) {
			return resume();
		}
		if (state != RunState.FAILED) {
			return false;
		}
		failureReason = "";
		detail = "continuing " + phase;
		log.info("Continuing failed run at " + phase);
		enter(phase);
		return true;
	}

	public void cancel() {
		combat.cancel();
		combat.setPaused(false);
		gateway.resume();
		gateway.forceCancel();
		if (!state.active()) {
			return;
		}
		state = RunState.IDLE;
		phase = RunPhase.CHECK;
		attempt = 0;
		failureReason = "";
		detail = "cancelled";
		log.info("Run cancelled");
	}

	public void reset() {
		if (state.active()) {
			cancel();
		}
		state = RunState.IDLE;
		phase = RunPhase.CHECK;
		attempt = 0;
		detail = "";
		failureReason = "";
		combat.setPaused(false);
	}

	public boolean skipPhase() {
		if (!state.active()) {
			return false;
		}
		PhaseHandler handler = handlers.get(phase);
		if (handler == null || !handler.skippable()) {
			return false;
		}
		if (state == RunState.PAUSED) {
			gateway.resume();
			combat.setPaused(false);
		}
		log.info("Skipping " + phase);
		combat.cancel();
		advance();
		return true;
	}

	public void tick() {
		if (!state.active() || state == RunState.PAUSED) {
			return;
		}
		if (phase == RunPhase.DONE) {
			state = RunState.DONE;
			detail = "run complete";
			return;
		}
		if (phase == RunPhase.DIAMOND && skipDiamond) {
			log.info("Diamond phase skipped by default");
			advance();
			return;
		}
		PhaseHandler handler = handlers.get(phase);
		if (handler == null) {
			fail("no handler for " + phase);
			return;
		}
		if (clock.millis() - phaseStartedAt > handler.timeoutMillis()) {
			onTimeout(handler);
			return;
		}
		PhaseTick result = handler.onTick(context());
		if (!result.detail().isBlank()) {
			detail = result.detail();
		}
		switch (result.signal()) {
			case ADVANCE -> advance();
			case FAIL -> fail(result.detail());
			case CONTINUE -> {
			}
		}
	}

	public RunSnapshot snapshot() {
		long elapsed = state == RunState.IDLE ? 0L : Math.max(0L, clock.millis() - phaseStartedAt);
		return new RunSnapshot(state, phase, attempt, detail, failureReason, elapsed, skipDiamond);
	}

	private void onTimeout(PhaseHandler handler) {
		TimeoutAction action = timeoutPolicy.onTimeout(phase, attempt);
		log.warn("Timeout in " + phase + " attempt " + attempt + " -> " + action);
		switch (action) {
			case RETRY -> {
				gateway.cancel();
				if (phase.usesCombat()) {
					combat.cancel();
				}
				attempt++;
				phaseStartedAt = clock.millis();
				detail = "retry " + attempt;
				state = phase.usesCombat() ? RunState.COMBAT : RunState.RUNNING;
				handler.onEnter(context());
			}
			case PAUSE -> {
				phaseStartedAt = clock.millis();
				pause();
				detail = "timed out in " + phase + "; paused";
			}
			case FAIL -> fail("timed out in " + phase);
		}
	}

	private void advance() {
		gateway.cancel();
		RunPhase next = phase.next();
		if (next == null) {
			enter(RunPhase.DONE);
			return;
		}
		if (next == RunPhase.DIAMOND && skipDiamond) {
			log.info("Skipping diamond");
			next = next.next();
		}
		if (next == null) {
			enter(RunPhase.DONE);
			return;
		}
		enter(next);
	}

	private void enter(RunPhase next) {
		phase = next;
		attempt = 0;
		phaseStartedAt = clock.millis();
		if (next == RunPhase.DONE) {
			state = RunState.DONE;
			detail = "run complete";
			log.info("Run done");
			return;
		}
		state = next.usesCombat() ? RunState.COMBAT : RunState.RUNNING;
		detail = next.name();
		log.info("Entered " + next);
		PhaseHandler handler = handlers.get(next);
		if (handler != null) {
			handler.onEnter(context());
		}
	}

	private void fail(String reason) {
		gateway.cancel();
		combat.cancel();
		combat.setPaused(false);
		state = RunState.FAILED;
		failureReason = reason == null || reason.isBlank() ? "failed" : reason;
		detail = failureReason;
		log.warn("Run failed in " + phase + ": " + failureReason);
	}

	private PhaseContext context() {
		return new PhaseContext(gateway, inventory, combat, world, clock.millis());
	}

	@SafeVarargs
	private static EnumMap<RunPhase, PhaseHandler> index(List<PhaseHandler>... groups) {
		EnumMap<RunPhase, PhaseHandler> map = new EnumMap<>(RunPhase.class);
		for (List<PhaseHandler> group : groups) {
			for (PhaseHandler handler : group) {
				if (map.put(handler.phase(), handler) != null) {
					throw new IllegalStateException("Duplicate handler for " + handler.phase());
				}
			}
		}
		return map;
	}
}
