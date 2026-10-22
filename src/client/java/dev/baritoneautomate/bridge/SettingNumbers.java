package dev.baritoneautomate.bridge;

/** Validate numeric edits before they reach Baritone's setting parser. */
public final class SettingNumbers {
	private SettingNumbers() {
	}

	public static boolean valid(SettingKind kind, String text) {
		if (text == null) {
			return false;
		}
		try {
			String value = text.trim();
			return switch (kind) {
				case INTEGER -> {
					Integer.parseInt(value);
					yield true;
				}
				case LONG -> {
					Long.parseLong(value);
					yield true;
				}
				case FLOAT -> Float.isFinite(Float.parseFloat(value));
				case DOUBLE -> Double.isFinite(Double.parseDouble(value));
				default -> false;
			};
		} catch (NumberFormatException failure) {
			return false;
		}
	}
}
