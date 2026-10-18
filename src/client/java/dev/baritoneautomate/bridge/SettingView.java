package dev.baritoneautomate.bridge;

/**
 * Immutable snapshot of one Baritone setting. Values are already serialized;
 * the UI must not touch {@code Settings.Setting} directly.
 */
public record SettingView(
		String name,
		SettingKind kind,
		String valueClass,
		String genericType,
		String value,
		String defaultValue,
		boolean javaOnly,
		boolean modified
) {
}
