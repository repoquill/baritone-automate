package dev.baritoneautomate.automate;

public final class Dimensions {
	private Dimensions() {
	}

	public static boolean isNether(String id) {
		return matches(id, "the_nether");
	}

	public static boolean isEnd(String id) {
		return matches(id, "the_end");
	}

	public static boolean isOverworld(String id) {
		return matches(id, "overworld");
	}

	private static boolean matches(String id, String path) {
		if (id == null || id.isBlank()) {
			return false;
		}
		return id.equals(path) || id.endsWith(":" + path);
	}
}
