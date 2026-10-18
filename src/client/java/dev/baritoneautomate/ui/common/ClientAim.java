package dev.baritoneautomate.ui.common;

import dev.baritoneautomate.automate.BlockPosView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Client-only aim and position helpers. This class does not talk to Baritone. */
public final class ClientAim {
	public record AimedBlock(int x, int y, int z, String blockId) {
	}

	private ClientAim() {
	}

	public static LocalPlayer player() {
		return Minecraft.getInstance().player;
	}

	public static BlockPosView feet() {
		LocalPlayer player = player();
		if (player == null) {
			return null;
		}
		return new BlockPosView(player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ());
	}

	public static String dimensionId() {
		LocalPlayer player = player();
		if (player == null || player.level() == null) {
			return "user";
		}
		return player.level().dimension().identifier().toString();
	}

	public static AimedBlock crosshair() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || minecraft.level == null) {
			return null;
		}
		Block block = minecraft.level.getBlockState(hit.getBlockPos()).getBlock();
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		if (id == null) {
			return null;
		}
		String blockId = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
		return new AimedBlock(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ(), blockId);
	}

	public static List<String> nearbyPlayerNames() {
		Minecraft minecraft = Minecraft.getInstance();
		List<String> names = new ArrayList<>();
		if (minecraft.level == null) {
			return names;
		}
		for (AbstractClientPlayer player : minecraft.level.players()) {
			if (player == null || player == minecraft.player) {
				continue;
			}
			String name = player.getName().getString();
			if (!name.isBlank()) {
				names.add(name);
			}
			if (names.size() >= 6) {
				break;
			}
		}
		return names;
	}

	public static List<String> searchBlocks(String query, int limit) {
		String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
		List<String> matches = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			String full = id.toString();
			String path = id.getPath();
			if (!needle.isEmpty() && !full.contains(needle) && !path.contains(needle)) {
				continue;
			}
			matches.add("minecraft".equals(id.getNamespace()) ? path : full);
			if (matches.size() >= limit) {
				break;
			}
		}
		return matches;
	}
}
