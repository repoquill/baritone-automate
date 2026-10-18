package dev.baritoneautomate.inventory;

import java.util.List;

/**
 * Chooses how to get an item onto the hotbar. Slots follow the player inventory:
 * 0-8 hotbar, 9-35 storage, 40 offhand. The client performs the click.
 */
public final class HotbarMove {
	public enum Kind {
		SELECT,
		SWAP,
		MISSING
	}

	public record Decision(Kind kind, int hotbarSlot, int inventorySlot, String itemId, String message) {
	}

	private HotbarMove() {
	}

	public static Decision decide(List<String> slots, String missingMessage, String... wanted) {
		if (slots == null || wanted == null || wanted.length == 0) {
			return new Decision(Kind.MISSING, -1, -1, "", missingMessage == null ? "missing item" : missingMessage);
		}
		for (int slot = 0; slot < Math.min(9, slots.size()); slot++) {
			String matched = match(slots.get(slot), wanted);
			if (matched != null) {
				return new Decision(Kind.SELECT, slot, slot, matched, "selected " + ItemIds.path(matched));
			}
		}
		int destination = emptyHotbar(slots);
		if (destination < 0) {
			destination = 0;
		}
		for (int slot = 0; slot < slots.size(); slot++) {
			if (slot < 9 || (slot >= 36 && slot != 40)) {
				continue;
			}
			String matched = match(slots.get(slot), wanted);
			if (matched != null) {
				return new Decision(Kind.SWAP, destination, slot, matched, "moving " + ItemIds.path(matched) + " to the hotbar");
			}
		}
		String message = missingMessage == null || missingMessage.isBlank() ? "missing item" : missingMessage;
		return new Decision(Kind.MISSING, -1, -1, "", message);
	}

	private static int emptyHotbar(List<String> slots) {
		for (int slot = 0; slot < Math.min(9, slots.size()); slot++) {
			String id = slots.get(slot);
			if (id == null || id.isBlank()) {
				return slot;
			}
		}
		return -1;
	}

	private static String match(String slotId, String[] wanted) {
		if (slotId == null || slotId.isBlank()) {
			return null;
		}
		for (String want : wanted) {
			if (ItemIds.matches(slotId, want)) {
				return ItemIds.normalize(want);
			}
		}
		return null;
	}
}
