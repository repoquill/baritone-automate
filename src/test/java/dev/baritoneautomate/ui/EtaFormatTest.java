package dev.baritoneautomate.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.baritoneautomate.bridge.PathingSnapshot;
import dev.baritoneautomate.ui.common.EtaFormat;
import org.junit.jupiter.api.Test;

class EtaFormatTest {
	@Test
	void missingEstimateIsADash() {
		assertEquals("—", EtaFormat.duration((Double) null));
		assertEquals("—", EtaFormat.duration(Double.NaN));
	}

	@Test
	void ticksUseTwentyPerSecond() {
		assertEquals("0s", EtaFormat.duration(0.0));
		assertEquals("2s", EtaFormat.duration(40.0));
		assertEquals("1:05", EtaFormat.duration(20.0 * 65));
	}

	@Test
	void snapshotPrefersGoalEstimate() {
		PathingSnapshot snapshot = new PathingSnapshot(true, true, false, "Mine", "goal", 80.0, 20.0, "estimate");
		assertEquals("4s", EtaFormat.duration(snapshot));
	}

	@Test
	void snapshotFallsBackToTheSegment() {
		PathingSnapshot snapshot = new PathingSnapshot(true, true, false, "Mine", "", null, 60.0, "estimate");
		assertEquals("3s", EtaFormat.duration(snapshot));
	}
}
