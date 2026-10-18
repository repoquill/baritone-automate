package dev.baritoneautomate.bootstrap;

/**
 * Classpath probe that does not link {@code baritone.api}. The real gateway class
 * is loaded only after this returns true, so a missing mod degrades instead of crashing.
 */
public final class BaritonePresence {
	private BaritonePresence() {
	}

	public static boolean classPresent() {
		try {
			Class.forName("baritone.api.BaritoneAPI", false, BaritonePresence.class.getClassLoader());
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}
}
