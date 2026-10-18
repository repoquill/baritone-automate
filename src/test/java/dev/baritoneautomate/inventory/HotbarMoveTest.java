package dev.baritoneautomate.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HotbarMoveTest {
	@Test
	void selectsAnIgniterAlreadyOnTheHotbar() {
		List<String> slots = empty();
		slots.set(3, "minecraft:flint_and_steel");
		HotbarMove.Decision decision = HotbarMove.decide(slots, "no flint and steel or fire charge in the inventory", "flint_and_steel", "fire_charge");
		assertEquals(HotbarMove.Kind.SELECT, decision.kind());
		assertEquals(3, decision.hotbarSlot());
	}

	@Test
	void swapsAnIgniterOutOfStorage() {
		List<String> slots = empty();
		slots.set(12, "minecraft:fire_charge");
		HotbarMove.Decision decision = HotbarMove.decide(slots, "no flint and steel or fire charge in the inventory", "flint_and_steel", "fire_charge");
		assertEquals(HotbarMove.Kind.SWAP, decision.kind());
		assertEquals(12, decision.inventorySlot());
		assertEquals(0, decision.hotbarSlot());
		assertTrueMessage(decision.message());
	}

	@Test
	void swapsTheOffhand() {
		List<String> slots = empty();
		slots.set(40, "minecraft:flint_and_steel");
		HotbarMove.Decision decision = HotbarMove.decide(slots, "no flint and steel or fire charge in the inventory", "flint_and_steel", "fire_charge");
		assertEquals(HotbarMove.Kind.SWAP, decision.kind());
		assertEquals(40, decision.inventorySlot());
	}

	@Test
	void missingIgniterIsExplicit() {
		HotbarMove.Decision decision = HotbarMove.decide(empty(), "no flint and steel or fire charge in the inventory", "flint_and_steel", "fire_charge");
		assertEquals(HotbarMove.Kind.MISSING, decision.kind());
		assertEquals("no flint and steel or fire charge in the inventory", decision.message());
	}

	private static void assertTrueMessage(String message) {
		assertEquals("moving fire_charge to the hotbar", message);
	}

	private static List<String> empty() {
		List<String> slots = new ArrayList<>();
		for (int i = 0; i < 41; i++) {
			slots.add("");
		}
		return slots;
	}
}
