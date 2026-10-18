package dev.baritoneautomate.automate;

public record BlockPosView(int x, int y, int z) {
	public double horizontalDistanceTo(int otherX, int otherZ) {
		double dx = (double) x - otherX;
		double dz = (double) z - otherZ;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
