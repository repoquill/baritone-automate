package dev.baritoneautomate.ui.control.panels;

import dev.baritoneautomate.ui.control.ControlScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface ControlPanel {
	void build(ControlScreen screen);

	default void paint(GuiGraphicsExtractor graphics, ControlScreen screen) {
	}
}
