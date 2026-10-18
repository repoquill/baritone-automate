package dev.baritoneautomate.automate;

import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.combat.CombatSession;
import dev.baritoneautomate.inventory.InventoryAccess;

public record PhaseContext(
		BaritoneGateway gateway,
		InventoryAccess inventory,
		CombatSession combat,
		ClientView world,
		long nowMillis
) {
}
