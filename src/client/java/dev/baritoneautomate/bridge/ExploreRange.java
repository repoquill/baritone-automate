package dev.baritoneautomate.bridge;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunks whose blocks overlap a circle of {@code rangeBlocks} around a center.
 * Baritone's {@code explore(int, int)} has no radius; an inverted explore filter
 * of these chunks is how a range is expressed.
 */
public final class ExploreRange {
	/** Hard cap so a typed range cannot emit a huge chunk list. */
	public static final int MAX_BLOCKS = 2048;

	private ExploreRange() {
	}

	public static String json(int centerX, int centerZ, int rangeBlocks) {
		StringBuilder out = new StringBuilder("[");
		boolean first = true;
		for (int[] chunk : chunks(centerX, centerZ, rangeBlocks)) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append("{\"x\":").append(chunk[0]).append(",\"z\":").append(chunk[1]).append('}');
		}
		out.append(']');
		return out.toString();
	}

	public static List<int[]> chunks(int centerX, int centerZ, int rangeBlocks) {
		if (rangeBlocks <= 0) {
			return List.of();
		}
		long radiusSquared = (long) rangeBlocks * rangeBlocks;
		int minChunkX = Math.floorDiv(centerX - rangeBlocks, 16);
		int maxChunkX = Math.floorDiv(centerX + rangeBlocks, 16);
		int minChunkZ = Math.floorDiv(centerZ - rangeBlocks, 16);
		int maxChunkZ = Math.floorDiv(centerZ + rangeBlocks, 16);
		List<int[]> included = new ArrayList<>();
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (overlaps(centerX, centerZ, radiusSquared, chunkX, chunkZ)) {
					included.add(new int[] {chunkX, chunkZ});
				}
			}
		}
		return List.copyOf(included);
	}

	public static boolean contains(int centerX, int centerZ, int rangeBlocks, int chunkX, int chunkZ) {
		if (rangeBlocks <= 0) {
			return false;
		}
		return overlaps(centerX, centerZ, (long) rangeBlocks * rangeBlocks, chunkX, chunkZ);
	}

	private static boolean overlaps(int x, int z, long radiusSquared, int chunkX, int chunkZ) {
		int minX = chunkX << 4;
		int maxX = minX + 15;
		int minZ = chunkZ << 4;
		int maxZ = minZ + 15;
		int nearestX = Math.min(Math.max(x, minX), maxX);
		int nearestZ = Math.min(Math.max(z, minZ), maxZ);
		long dx = (long) x - nearestX;
		long dz = (long) z - nearestZ;
		return dx * dx + dz * dz <= radiusSquared;
	}
}
