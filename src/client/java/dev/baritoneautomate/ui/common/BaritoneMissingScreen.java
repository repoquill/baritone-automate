package dev.baritoneautomate.ui.common;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown from the control-screen banner. {@code fabric.mod.json} hard-depends on Baritone,
 * so a missing mod normally prevents this mod from loading at all. This screen covers the
 * case where the mod loaded but the Baritone provider is still not ready.
 */
public final class BaritoneMissingScreen extends Screen {
	private final Screen parent;

	public BaritoneMissingScreen(Screen parent) {
		super(Component.translatable("screen.baritone-automate.missing.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("screen.baritone-automate.back"), button -> onClose())
				.bounds(this.width / 2 - 60, this.height - 32, 120, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xF0101014);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(this.font, getTitle(), 16, 16, 0xFFFFFFFF, true);
		graphics.textWithWordWrap(
				this.font,
				Component.translatable("screen.baritone-automate.missing.body"),
				16,
				36,
				this.width - 32,
				0xFFE6E6E6);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
