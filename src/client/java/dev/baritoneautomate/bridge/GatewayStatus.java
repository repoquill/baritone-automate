package dev.baritoneautomate.bridge;

/**
 * Result of a gateway call. UI and the run state machine should branch on {@link #ok()}
 * instead of catching Baritone exceptions.
 */
public record GatewayStatus(boolean ok, String message) {
	public GatewayStatus {
		if (message == null || message.isBlank()) {
			message = ok ? "ok" : "failed";
		}
	}

	public static GatewayStatus ok(String message) {
		return new GatewayStatus(true, message);
	}

	public static GatewayStatus fail(String message) {
		return new GatewayStatus(false, message);
	}
}
