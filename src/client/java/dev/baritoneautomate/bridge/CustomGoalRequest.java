package dev.baritoneautomate.bridge;

/**
 * A custom goal that does not leak Baritone's {@code Goal} type to the UI or the state machine.
 */
public record CustomGoalRequest(Kind kind, int x, int y, int z, int range) {
	public enum Kind {
		BLOCK,
		XZ,
		NEAR,
		Y_LEVEL,
		TWO_BLOCKS,
		GET_TO_BLOCK
	}

	public static CustomGoalRequest block(int x, int y, int z) {
		return new CustomGoalRequest(Kind.BLOCK, x, y, z, 0);
	}

	public static CustomGoalRequest xz(int x, int z) {
		return new CustomGoalRequest(Kind.XZ, x, 0, z, 0);
	}

	public static CustomGoalRequest near(int x, int y, int z, int range) {
		return new CustomGoalRequest(Kind.NEAR, x, y, z, range);
	}

	public static CustomGoalRequest yLevel(int y) {
		return new CustomGoalRequest(Kind.Y_LEVEL, 0, y, 0, 0);
	}
}
