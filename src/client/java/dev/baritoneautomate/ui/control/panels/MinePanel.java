package dev.baritoneautomate.ui.control.panels;

import dev.baritoneautomate.ui.common.ClientAim;
import dev.baritoneautomate.ui.control.ControlScreen;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class MinePanel implements ControlPanel {
	@Override
	public void build(ControlScreen screen) {
		int x = screen.contentX();
		int y = screen.contentY();
		var query = screen.addApplyBox(x, y, 140, screen.blockQuery, Component.translatable("screen.baritone-automate.mine.search"), text -> screen.blockQuery = text);
		query.setOnApply(screen::rebuild);
		screen.addButton(Component.translatable("screen.baritone-automate.mine.find"), x + 144, y, 52, false, button -> screen.rebuild());
		screen.addButton(Component.translatable("screen.baritone-automate.mine.crosshair"), x + 200, y, 88, false, button -> addCrosshair(screen));

		int row = y + 24;
		List<String> matches = ClientAim.searchBlocks(screen.blockQuery, 5);
		int mx = x;
		for (String id : matches) {
			if (mx + 90 > x + screen.contentWidth()) {
				break;
			}
			String captured = id;
			screen.addButton(Component.literal("+" + captured), mx, row, Math.min(110, 8 + captured.length() * 6), false, button -> addBlock(screen, captured));
			mx += Math.min(110, 8 + captured.length() * 6) + 4;
		}

		int chips = row + 24;
		int cx = x;
		int shown = 0;
		for (String id : List.copyOf(screen.mineIds)) {
			if (shown >= 6) {
				break;
			}
			String captured = id;
			int width = Math.min(120, 16 + captured.length() * 6);
			if (cx + width > x + screen.contentWidth()) {
				break;
			}
			screen.addButton(Component.literal(captured), cx, chips, width, false, button -> {
				screen.mineIds.remove(captured);
				screen.rebuild();
			});
			cx += width + 4;
			shown++;
		}

		int actions = chips + 24;
		screen.addBox(x, actions, 48, screen.mineQty, Component.translatable("screen.baritone-automate.mine.qty"), text -> screen.mineQty = text);
		screen.addButton(Component.translatable("screen.baritone-automate.mine.start"), x + 52, actions, 88, true, button -> start(screen));
		screen.addButton(Component.translatable("screen.baritone-automate.mine.clear"), x + 144, actions, 52, false, button -> {
			screen.mineIds.clear();
			screen.rebuild();
		});
	}

	@Override
	public void paint(GuiGraphicsExtractor graphics, ControlScreen screen) {
		int y = screen.contentY() + 96;
		if (y >= screen.contentY() + screen.contentHeight()) {
			return;
		}
		graphics.text(screen.font(), Component.translatable("screen.baritone-automate.mine.hint", screen.mineIds.size()), screen.contentX(), y, 0xFF9A9A9A, false);
	}

	private static void addBlock(ControlScreen screen, String id) {
		if (!screen.mineIds.contains(id)) {
			screen.mineIds.add(id);
		}
		screen.rebuild();
	}

	private static void addCrosshair(ControlScreen screen) {
		var aimed = ClientAim.crosshair();
		if (aimed == null) {
			screen.report(false, Component.translatable("screen.baritone-automate.no_crosshair").getString());
			return;
		}
		addBlock(screen, aimed.blockId());
	}

	private static void start(ControlScreen screen) {
		if (screen.mineIds.isEmpty()) {
			screen.report(false, Component.translatable("screen.baritone-automate.mine.empty").getString());
			return;
		}
		int quantity;
		try {
			quantity = Integer.parseInt(screen.mineQty.trim());
		} catch (NumberFormatException exception) {
			screen.report(false, Component.translatable("screen.baritone-automate.bad_number").getString());
			return;
		}
		screen.report(screen.gateway().mine(quantity, List.copyOf(screen.mineIds)));
	}
}
