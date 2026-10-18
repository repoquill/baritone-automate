package dev.baritoneautomate.bridge;

import java.util.ArrayList;
import java.util.List;

/** Enum constant names for a setting value class. No Baritone types. */
public final class SettingEnums {
	private SettingEnums() {
	}

	public static List<String> names(Class<?> valueClass) {
		if (valueClass == null || !valueClass.isEnum()) {
			return List.of();
		}
		Object[] constants = valueClass.getEnumConstants();
		if (constants == null || constants.length == 0) {
			return List.of();
		}
		List<String> names = new ArrayList<>(constants.length);
		for (Object constant : constants) {
			if (constant instanceof Enum<?> value) {
				names.add(value.name());
			}
		}
		return List.copyOf(names);
	}
}
