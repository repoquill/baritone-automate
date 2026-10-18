package dev.baritoneautomate.bootstrap;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.ClientView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class MinecraftClientView implements ClientView {
	@Override
	public boolean inGame() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && client.level != null;
	}

	@Override
	public boolean alive() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.isAlive();
	}

	@Override
	public float health() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? 0.0f : player.getHealth();
	}

	@Override
	public String dimensionId() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		return player.level().dimension().identifier().toString();
	}

	@Override
	public BlockPosView blockPos() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		BlockPos pos = player.blockPosition();
		return new BlockPosView(pos.getX(), pos.getY(), pos.getZ());
	}
}
