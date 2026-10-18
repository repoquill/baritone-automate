package dev.baritoneautomate.inventory;

import java.util.Locale;

public record ActionResult(Status status, String message) {
	public enum Status {
		OK,
		PENDING,
		UNSUPPORTED,
		UNAVAILABLE,
		MISSING
	}

	public ActionResult {
		if (message == null || message.isBlank()) {
			message = status.name().toLowerCase(Locale.ROOT);
		}
	}

	public static ActionResult ok(String message) {
		return new ActionResult(Status.OK, message);
	}

	public static ActionResult pending(String message) {
		return new ActionResult(Status.PENDING, message);
	}

	public static ActionResult missing(String message) {
		return new ActionResult(Status.MISSING, message);
	}

	public static ActionResult unsupported(String message) {
		return new ActionResult(Status.UNSUPPORTED, message);
	}

	public static ActionResult unavailable(String message) {
		return new ActionResult(Status.UNAVAILABLE, message);
	}
}
