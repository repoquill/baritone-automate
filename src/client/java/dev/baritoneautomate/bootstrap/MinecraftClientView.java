package dev.baritoneautomate.bootstrap;

import dev.baritoneautomate.automate.BlockPosView;
import dev.baritoneautomate.automate.ClientView;
import dev.baritoneautomate.inventory.ActionResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class MinecraftClientView implements ClientView {
	@Override
	public void resetActions() {
		ClientWorldActions.resetPortalPlan();
	}

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

	@Override
	public BlockPosView findNearbyBlock(String blockId, int radius) {
		return ClientWorldActions.findNearbyBlock(blockId, radius);
	}

	@Override
	public BlockPosView findNearbyEntity(String entityId, double radius) {
		return ClientWorldActions.findNearbyEntity(entityId, radius);
	}

	@Override
	public ActionResult useItem(String itemId) {
		return ClientWorldActions.useItem(itemId);
	}

	@Override
	public ActionResult attackNearest(String entityId, double reach) {
		return ClientWorldActions.attackNearest(entityId, reach);
	}

	@Override
	public ActionResult lightNetherPortal() {
		return ClientWorldActions.lightNetherPortal();
	}

	@Override
	public boolean portalBuildInProgress() {
		return ClientWorldActions.portalBuildInProgress();
	}

	@Override
	public int emptyEndFrames(int radius) {
		return ClientWorldActions.emptyEndFrames(radius);
	}

	@Override
	public BlockPosView findEmptyEndFrame(int radius) {
		return ClientWorldActions.findEmptyEndFrame(radius);
	}

	@Override
	public ActionResult fillEndPortalFrame() {
		return ClientWorldActions.fillEndPortalFrame();
	}
}
