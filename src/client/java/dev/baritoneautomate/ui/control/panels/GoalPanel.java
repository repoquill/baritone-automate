package dev.baritoneautomate.ui.control.panels;

import dev.baritoneautomate.bridge.CustomGoalRequest;
import dev.baritoneautomate.ui.common.ClientAim;
import dev.baritoneautomate.ui.control.ControlScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class GoalPanel implements ControlPanel {
	@Override
	public void build(ControlScreen screen) {
		int x = screen.contentX();
		int y = screen.contentY();
		screen.addBox(x, y, 52, screen.goalX, Component.translatable("screen.baritone-automate.axis.x"), text -> screen.goalX = text);
		screen.addBox(x + 56, y, 52, screen.goalY, Component.translatable("screen.baritone-automate.axis.y"), text -> screen.goalY = text);
		screen.addBox(x + 112, y, 52, screen.goalZ, Component.translatable("screen.baritone-automate.axis.z"), text -> screen.goalZ = text);
		screen.addButton(Component.translatable("screen.baritone-automate.goal.feet"), x + 170, y, 72, false, button -> fillFeet(screen));
		screen.addButton(Component.translatable("screen.baritone-automate.goal.crosshair"), x + 246, y, 88, false, button -> fillCrosshair(screen));

		int y2 = y + 24;
		screen.addButton(Component.translatable("screen.baritone-automate.goal.set"), x, y2, 110, true, button -> {
			int[] pos = parse(screen);
			if (pos == null) {
				return;
			}
			screen.report(screen.gateway().customGoal(CustomGoalRequest.block(pos[0], pos[1], pos[2])));
		});
		screen.addButton(Component.translatable("screen.baritone-automate.goal.goto"), x + 114, y2, 72, true, button -> {
			int[] pos = parse(screen);
			if (pos == null) {
				return;
			}
			screen.report(screen.gateway().gotoBlock(pos[0], pos[1], pos[2]));
		});
		screen.addButton(Component.translatable("screen.baritone-automate.goal.gotoxz"), x + 190, y2, 80, true, button -> {
			int[] pos = parse(screen);
			if (pos == null) {
				return;
			}
			screen.report(screen.gateway().gotoXZ(pos[0], pos[2]));
		});

		int y3 = y2 + 24;
		screen.addBox(x, y3, 52, screen.thisway, Component.translatable("screen.baritone-automate.goal.distance"), text -> screen.thisway = text);
		screen.addButton(Component.translatable("screen.baritone-automate.goal.thisway"), x + 56, y3, 100, true, button -> {
			try {
				double distance = Double.parseDouble(screen.thisway.trim());
				screen.report(screen.gateway().thisway(distance));
			} catch (NumberFormatException exception) {
				screen.report(false, Component.translatable("screen.baritone-automate.bad_number").getString());
			}
		});
	}

	@Override
	public void paint(GuiGraphicsExtractor graphics, ControlScreen screen) {
		int y = screen.contentY() + 76;
		if (y < screen.contentY() + screen.contentHeight()) {
			graphics.text(screen.font(), Component.translatable("screen.baritone-automate.goal.hint"), screen.contentX(), y, 0xFF9A9A9A, false);
		}
	}

	private static void fillFeet(ControlScreen screen) {
		var pos = ClientAim.feet();
		if (pos == null) {
			screen.report(false, Component.translatable("screen.baritone-automate.no_player").getString());
			return;
		}
		screen.goalX = Integer.toString(pos.x());
		screen.goalY = Integer.toString(pos.y());
		screen.goalZ = Integer.toString(pos.z());
		screen.rebuild();
	}

	private static void fillCrosshair(ControlScreen screen) {
		var aimed = ClientAim.crosshair();
		if (aimed == null) {
			screen.report(false, Component.translatable("screen.baritone-automate.no_crosshair").getString());
			return;
		}
		screen.goalX = Integer.toString(aimed.x());
		screen.goalY = Integer.toString(aimed.y());
		screen.goalZ = Integer.toString(aimed.z());
		screen.rebuild();
	}

	private static int[] parse(ControlScreen screen) {
		try {
			return new int[] {
					Integer.parseInt(screen.goalX.trim()),
					Integer.parseInt(screen.goalY.trim()),
					Integer.parseInt(screen.goalZ.trim())
			};
		} catch (NumberFormatException exception) {
			screen.report(false, Component.translatable("screen.baritone-automate.bad_number").getString());
			return null;
		}
	}
}
