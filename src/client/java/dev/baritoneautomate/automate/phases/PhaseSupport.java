package dev.baritoneautomate.automate.phases;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.PhaseContext;

final class PhaseSupport {
	private PhaseSupport() {
	}

	static boolean close(PhaseContext context, BlockPosView pos, double blocks) {
		BlockPosView feet = context.world().blockPos();
		if (feet == null || pos == null) {
			return false;
		}
		double horizontal = feet.horizontalDistanceTo(pos.x(), pos.z());
		return Math.hypot(horizontal, (double) feet.y() - pos.y()) <= blocks;
	}

	static BlockPosView firstBlock(PhaseContext context, int radius, String... blockIds) {
		if (blockIds == null) {
			return null;
		}
		for (String blockId : blockIds) {
			BlockPosView found = context.world().findNearbyBlock(blockId, radius);
			if (found != null) {
				return found;
			}
		}
		return null;
	}
}
