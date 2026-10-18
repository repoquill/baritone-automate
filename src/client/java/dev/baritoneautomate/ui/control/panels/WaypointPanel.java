package dev.baritoneautomate.ui.control.panels;

import dev.baritoneautomate.bridge.WaypointView;
import dev.baritoneautomate.ui.common.ClientAim;
import dev.baritoneautomate.ui.control.ControlScreen;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class WaypointPanel implements ControlPanel {
	@Override
	public void build(ControlScreen screen) {
		int x = screen.contentX();
		int y = screen.contentY();
		screen.addBox(x, y, 120, screen.waypointName, Component.translatable("screen.baritone-automate.waypoint.name"), text -> screen.waypointName = text);
		screen.addButton(Component.translatable("screen.baritone-automate.waypoint.save"), x + 124, y, 110, true, button -> save(screen));
		List<WaypointView> waypoints = screen.gateway().listWaypoints();
		screen.waypoints = waypoints;
		int rows = Math.max(1, (screen.contentHeight() - 52) / 22);
		int pages = Math.max(1, (waypoints.size() + rows - 1) / rows);
		if (screen.waypointPage >= pages) {
			screen.waypointPage = pages - 1;
		}
		if (screen.waypointPage < 0) {
			screen.waypointPage = 0;
		}
		screen.addButton(Component.translatable("screen.baritone-automate.page_prev"), x + 240, y, 24, false, button -> {
			screen.waypointPage = Math.max(0, screen.waypointPage - 1);
			screen.rebuild();
		});
		screen.addButton(Component.translatable("screen.baritone-automate.page_next"), x + 268, y, 24, false, button -> {
			screen.waypointPage = screen.waypointPage + 1;
			screen.rebuild();
		});
		int from = screen.waypointPage * rows;
		int rowY = y + 24;
		for (int i = from; i < Math.min(waypoints.size(), from + rows); i++) {
			WaypointView waypoint = waypoints.get(i);
			int nameW = Math.min(120, Math.max(48, screen.contentWidth() - 150));
			screen.addButton(Component.translatable("screen.baritone-automate.waypoint.goto"), x + nameW + 4, rowY, 52, true,
					button -> screen.report(screen.gateway().gotoWaypoint(waypoint.name())));
			screen.addButton(Component.translatable("screen.baritone-automate.waypoint.delete"), x + nameW + 60, rowY, 52, true,
					button -> {
						screen.report(screen.gateway().deleteWaypoint(waypoint.name()));
						screen.rebuild();
					});
			rowY += 22;
		}
	}

	@Override
	public void paint(GuiGraphicsExtractor graphics, ControlScreen screen) {
		List<WaypointView> waypoints = screen.waypoints;
		int rows = Math.max(1, (screen.contentHeight() - 52) / 22);
		int from = screen.waypointPage * rows;
		int y = screen.contentY() + 28;
		if (waypoints.isEmpty()) {
			graphics.text(screen.font(), Component.translatable("screen.baritone-automate.waypoint.empty"), screen.contentX(), y, 0xFFB0B0B0, false);
			return;
		}
		int nameW = Math.min(120, Math.max(48, screen.contentWidth() - 150));
		for (int i = from; i < Math.min(waypoints.size(), from + rows); i++) {
			WaypointView waypoint = waypoints.get(i);
			String tag = waypoint.tag() == null || waypoint.tag().isBlank() ? "" : " " + waypoint.tag();
			String label = waypoint.name() + " " + waypoint.x() + " " + waypoint.y() + " " + waypoint.z() + tag;
			while (label.length() > 1 && screen.font().width(label) > nameW) {
				label = label.substring(0, label.length() - 1);
			}
			graphics.text(screen.font(), label, screen.contentX(), y + 4, 0xFFE6E6E6, false);
			y += 22;
		}
	}

	private static void save(ControlScreen screen) {
		String name = screen.waypointName == null ? "" : screen.waypointName.trim();
		if (name.isEmpty()) {
			screen.report(false, Component.translatable("screen.baritone-automate.waypoint.need_name").getString());
			return;
		}
		var feet = ClientAim.feet();
		if (feet == null) {
			screen.report(false, Component.translatable("screen.baritone-automate.no_player").getString());
			return;
		}
		screen.report(screen.gateway().saveWaypoint(name, ClientAim.dimensionId(), feet.x(), feet.y(), feet.z()));
		screen.rebuild();
	}
}
