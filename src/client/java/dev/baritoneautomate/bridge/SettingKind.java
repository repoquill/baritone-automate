package dev.baritoneautomate.bridge;

import java.util.List;
import java.util.Map;

/**
 * Coarse type tag for a Baritone setting. The UI should still read {@code valueClass}
 * for Minecraft types (blocks, items) that fall through to {@link #OTHER}.
 */
public enum SettingKind {
	BOOLEAN,
	INTEGER,
	LONG,
	FLOAT,
	DOUBLE,
	STRING,
	ENUM,
	LIST,
	MAP,
	OTHER;

	public static SettingKind of(Class<?> valueClass) {
		if (valueClass == null) {
			return OTHER;
		}
		if (valueClass == Boolean.class || valueClass == boolean.class) {
			return BOOLEAN;
		}
		if (valueClass == Integer.class || valueClass == int.class) {
			return INTEGER;
		}
		if (valueClass == Long.class || valueClass == long.class) {
			return LONG;
		}
		if (valueClass == Float.class || valueClass == float.class) {
			return FLOAT;
		}
		if (valueClass == Double.class || valueClass == double.class) {
			return DOUBLE;
		}
		if (valueClass == String.class) {
			return STRING;
		}
		if (valueClass.isEnum()) {
			return ENUM;
		}
		if (List.class.isAssignableFrom(valueClass)) {
			return LIST;
		}
		if (Map.class.isAssignableFrom(valueClass)) {
			return MAP;
		}
		return OTHER;
	}
}
