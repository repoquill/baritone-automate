package dev.baritoneautomate.automate;

import dev.baritoneautomate.inventory.ActionResult;

/** What the state machine needs from the client, without a Minecraft import. */
public interface ClientView {
	boolean inGame();

	boolean alive();

	float health();

	String dimensionId();

	BlockPosView blockPos();

	/** Closest block of this id inside {@code radius}, or null. */
	BlockPosView findNearbyBlock(String blockId, int radius);

	/** Closest living entity of this id inside {@code radius}, or null. */
	BlockPosView findNearbyEntity(String entityId, double radius);

	/** Select the item on the hotbar and use it once. */
	ActionResult useItem(String itemId);

	/** Attack the closest entity of this id inside {@code reach}. */
	ActionResult attackNearest(String entityId, double reach);

	/**
	 * Place one obsidian frame block, or use flint and steel / a fire charge once the frame is complete.
	 * Does not mean the portal is lit yet.
	 */
	ActionResult lightNetherPortal();

	default boolean portalBuildInProgress() {
		return false;
	}

	/** End portal frames in range whose {@code eye} property is still false. */
	int emptyEndFrames(int radius);

	/** Closest empty end portal frame inside {@code radius}, or null. */
	BlockPosView findEmptyEndFrame(int radius);

	/**
	 * Use one eye of ender on the closest empty frame within reach.
	 * OK means the use was accepted, not that every frame is filled.
	 */
	ActionResult fillEndPortalFrame();

	/** Forget construction plans when a run is cancelled or restarted. */
	default void resetActions() {
	}
}
