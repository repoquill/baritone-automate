package dev.baritoneautomate.automate;

/** What the state machine needs from the client, without a Minecraft import. */
public interface ClientView {
	boolean inGame();

	boolean alive();

	float health();

	String dimensionId();

	BlockPosView blockPos();
}
