package dev.baritoneautomate.inventory;

import java.util.Locale;

/** Accepts {@code oak_log} and {@code minecraft:oak_log}. */
public final class ItemIds {
	private ItemIds() {
	}

	public static String normalize(String id) {
		if (id == null) {
			return "";
		}
		String trimmed = id.trim().toLowerCase(Locale.ROOT);
		if (trimmed.isEmpty()) {
			return "";
		}
		return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
	}

	public static boolean matches(String stackId, String query) {
		return normalize(stackId).equals(normalize(query));
	}

	public static String path(String id) {
		String normalized = normalize(id);
		int split = normalized.indexOf(':');
		return split < 0 ? normalized : normalized.substring(split + 1);
	}
}
