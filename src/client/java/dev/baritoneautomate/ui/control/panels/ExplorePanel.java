package dev.baritoneautomate.ui.control.panels;

import dev.baritoneautomate.ui.common.ClientAim;
import dev.baritoneautomate.ui.control.ControlScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Explore center plus a block radius. The radius goes to
 * {@link dev.baritoneautomate.bridge.BaritoneGateway#explore(int, int, int)}.
 * Baritone's explore call itself only has a center; the gateway turns the radius
 * into an inverted chunk filter.
 */
public final class ExplorePanel implements ControlPanel {
	@Override
	public void build(ControlScreen screen) {
		int x = screen.contentX();
		int y = screen.contentY();
		screen.addButton(Component.translatable(screen.exploreAtFeet
				? "screen.baritone-automate.explore.feet_on"
				: "screen.baritone-automate.explore.feet_off"), x, y, 120, false, button -> {
			screen.exploreAtFeet = !screen.exploreAtFeet;
			screen.rebuild();
		});
		screen.addBox(x + 124, y, 52, screen.exploreX, Component.translatable("screen.baritone-automate.axis.x"), text -> screen.exploreX = text);
		screen.addBox(x + 180, y, 52, screen.exploreZ, Component.translatable("screen.baritone-automate.axis.z"), text -> screen.exploreZ = text);
		screen.addBox(x, y + 24, 52, screen.exploreRange, Component.translatable("screen.baritone-automate.explore.range"), text -> screen.exploreRange = text);
		screen.addButton(Component.translatable("screen.baritone-automate.explore.start"), x + 56, y + 24, 100, true, button -> start(screen));
	}

	@Override
	public void paint(GuiGraphicsExtractor graphics, ControlScreen screen) {
		int y = screen.contentY() + 52;
		if (y < screen.contentY() + screen.contentHeight()) {
			graphics.textWithWordWrap(
					screen.font(),
					Component.translatable("screen.baritone-automate.explore.note"),
					screen.contentX(),
					y,
					Math.max(40, screen.contentWidth()),
					0xFF9A9A9A);
		}
	}

	private static void start(ControlScreen screen) {
		int x;
		int z;
		if (screen.exploreAtFeet) {
			var feet = ClientAim.feet();
			if (feet == null) {
				screen.report(false, Component.translatable("screen.baritone-automate.no_player").getString());
				return;
			}
			x = feet.x();
			z = feet.z();
		} else {
			try {
				x = Integer.parseInt(screen.exploreX.trim());
				z = Integer.parseInt(screen.exploreZ.trim());
			} catch (NumberFormatException exception) {
				screen.report(false, Component.translatable("screen.baritone-automate.bad_number").getString());
				return;
			}
		}
		int range;
		try {
			range = Integer.parseInt(screen.exploreRange.trim());
		} catch (NumberFormatException exception) {
			screen.report(false, Component.translatable("screen.baritone-automate.bad_number").getString());
			return;
		}
		screen.report(screen.gateway().explore(x, z, range));
	}
}
