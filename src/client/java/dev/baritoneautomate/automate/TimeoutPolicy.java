package dev.baritoneautomate.automate;

@FunctionalInterface
public interface TimeoutPolicy {
	TimeoutAction onTimeout(RunPhase phase, int attempt);

	/** First timeout retries the phase. The next one fails the run. */
	static TimeoutPolicy retryOnceThenFail() {
		return (phase, attempt) -> attempt >= 1 ? TimeoutAction.FAIL : TimeoutAction.RETRY;
	}
}
