package dev.baritoneautomate.inventory;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Live player inventory. Craft and smelt run through {@link dev.baritoneautomate.bootstrap.ClientStation}. */
public final class PlayerInventoryAccess implements InventoryAccess {
	@Override
	public boolean available() {
		return player() != null;
	}

	@Override
	public int count(String itemId) {
		String wanted = ItemIds.normalize(itemId);
		if (wanted.isEmpty()) {
			return 0;
		}
		return countWhere(wanted::equals);
	}

	@Override
	public int countWhere(Predicate<String> normalizedItemId) {
		LocalPlayer player = player();
		if (player == null || normalizedItemId == null) {
			return 0;
		}
		int total = 0;
		Inventory inventory = player.getInventory();
		// getContainerSize() already includes equipment slots, offhand included.
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			total += matching(inventory.getItem(slot), normalizedItemId);
		}
		return total;
	}

	@Override
	public boolean selectHotbarItem(String itemId) {
		LocalPlayer player = player();
		if (player == null) {
			return false;
		}
		String wanted = ItemIds.normalize(itemId);
		Inventory inventory = player.getInventory();
		int hotbar = Math.min(9, inventory.getContainerSize());
		for (int slot = 0; slot < hotbar; slot++) {
			if (!wanted.equals(itemId(inventory.getItem(slot)))) {
				continue;
			}
			inventory.setSelectedSlot(slot);
			return true;
		}
		return false;
	}

	@Override
	public int selectedHotbarSlot() {
		LocalPlayer player = player();
		if (player == null) {
			return -1;
		}
		return player.getInventory().getSelectedSlot();
	}

	@Override
	public ActionResult craft(String outputItemId, int count) {
		return dev.baritoneautomate.bootstrap.ClientStation.craft(outputItemId, count);
	}

	@Override
	public ActionResult smelt(String inputItemId, int count) {
		return dev.baritoneautomate.bootstrap.ClientStation.smelt(inputItemId, count);
	}

	private static int matching(ItemStack stack, Predicate<String> normalizedItemId) {
		String id = itemId(stack);
		if (id.isEmpty() || !normalizedItemId.test(id)) {
			return 0;
		}
		return stack.getCount();
	}

	private static String itemId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id == null ? "" : id.toString();
	}

	private static LocalPlayer player() {
		Minecraft client = Minecraft.getInstance();
		return client == null ? null : client.player;
	}
}
