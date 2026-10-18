package dev.baritoneautomate.ui.common;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Edit box that commits when the player presses Enter. */
public final class ApplyBox extends EditBox {
	private Runnable onApply = () -> { };

	public ApplyBox(Font font, int x, int y, int width, int height, Component narration) {
		super(font, x, y, width, height, narration);
	}

	public void setOnApply(Runnable onApply) {
		this.onApply = onApply == null ? () -> { } : onApply;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			onApply.run();
			return true;
		}
		return super.keyPressed(event);
	}
}
