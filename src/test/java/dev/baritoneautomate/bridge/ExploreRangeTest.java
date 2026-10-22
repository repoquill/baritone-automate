package dev.baritoneautomate.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExploreRangeTest {
	@Test
	void zeroRangeIsUnlimited() {
		assertTrue(ExploreRange.chunks(0, 0, 0).isEmpty());
		assertEquals("[]", ExploreRange.json(10, 10, 0));
	}

	@Test
	void radiusIncludesOverlappingChunksAndSkipsTheRest() {
		assertTrue(ExploreRange.contains(0, 0, 32, 0, 0));
		assertTrue(ExploreRange.contains(0, 0, 32, 1, 0));
		assertFalse(ExploreRange.contains(0, 0, 32, 3, 0));
		String json = ExploreRange.json(0, 0, 32);
		assertTrue(json.startsWith("["));
		assertTrue(json.contains("{\"x\":0,\"z\":0}"));
		assertTrue(json.contains("{\"x\":1,\"z\":0}"));
		assertFalse(json.contains("\"x\":3"));
	}

	@Test
	void negativeCentersStillCoverTheirChunk() {
		assertTrue(ExploreRange.contains(-10, -10, 8, -1, -1));
		assertFalse(ExploreRange.contains(-10, -10, 8, 2, 2));
	}

	@Test
	void integerBoundaryCentersDoNotOverflowTheChunkBounds() {
		assertTrue(ExploreRange.chunks(Integer.MAX_VALUE, Integer.MAX_VALUE, 32).stream()
				.anyMatch(chunk -> chunk[0] == Math.floorDiv(Integer.MAX_VALUE, 16) && chunk[1] == Math.floorDiv(Integer.MAX_VALUE, 16)));
		assertTrue(ExploreRange.chunks(Integer.MIN_VALUE, Integer.MIN_VALUE, 32).stream()
				.anyMatch(chunk -> chunk[0] == Math.floorDiv(Integer.MIN_VALUE, 16) && chunk[1] == Math.floorDiv(Integer.MIN_VALUE, 16)));
	}

	@Test
	void oversizedRangesAreRejectedBeforeAllocatingChunks() {
		assertThrows(IllegalArgumentException.class, () -> ExploreRange.chunks(0, 0, ExploreRange.MAX_BLOCKS + 1));
	}

	@Test
	void distantChunksCannotWrapAroundToTheCenter() {
		assertFalse(ExploreRange.contains(0, 0, 8, 1 << 28, 0));
	}
}
