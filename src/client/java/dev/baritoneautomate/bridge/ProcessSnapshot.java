package dev.baritoneautomate.bridge;

/**
 * Last process the gateway started, plus whether it finished.
 * {@code generation} increments on every new process so a later phase does not
 * treat the previous process's success as its own.
 */
public record ProcessSnapshot(
		ProcessKind kind,
		ProcessOutcome outcome,
		String detail,
		long startedAtMillis,
		long generation
) {
	public ProcessSnapshot {
		if (detail == null) {
			detail = "";
		}
	}

	public static ProcessSnapshot idle() {
		return new ProcessSnapshot(ProcessKind.NONE, ProcessOutcome.IDLE, "", 0L, 0L);
	}
}
