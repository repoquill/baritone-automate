package dev.baritoneautomate.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
