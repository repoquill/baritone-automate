package dev.baritoneautomate.bridge;

import java.util.List;

/**
 * Immutable snapshot of one Baritone setting. Values are already serialized;
 * the UI must not touch {@code Settings.Setting} directly.
 * <p>
 * {@code description} is empty when the Baritone API does not expose one.
 * {@code enumValues} is empty unless the value class is an enum.
 */
public record SettingView(
		String name,
		SettingKind kind,
		String valueClass,
		String genericType,
		String value,
		String defaultValue,
		boolean javaOnly,
		boolean modified,
		String description,
		List<String> enumValues
) {
	public SettingView {
		if (description == null) {
			description = "";
		}
		enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
	}
}
