package dev.baritoneautomate.ui.control.panels;

import dev.baritoneautomate.bridge.PathingSnapshot;
import dev.baritoneautomate.bridge.ProcessSnapshot;
import dev.baritoneautomate.ui.common.EtaFormat;
import dev.baritoneautomate.ui.control.ControlScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Current Baritone process, plus the same four controls as the bottom bar. */
public final class ProcessPanel implements ControlPanel {
	@Override
	public void build(ControlScreen screen) {
		int x = screen.contentX();
		int y = screen.contentY();
		int w = 52;
		screen.addButton(Component.translatable("screen.baritone-automate.pause"), x, y, w, true,
				button -> screen.pauseCurrent());
		screen.addButton(Component.translatable("screen.baritone-automate.resume"), x + 56, y, w, true,
				button -> screen.resumeCurrent());
		screen.addButton(Component.translatable("screen.baritone-automate.cancel"), x + 112, y, w, true,
				button -> screen.cancelCurrent());
		screen.addButton(Component.translatable("screen.baritone-automate.force"), x + 168, y, w, true,
				button -> screen.confirmForceCancel());
	}

	@Override
	public void paint(GuiGraphicsExtractor graphics, ControlScreen screen) {
		PathingSnapshot snap = screen.gateway().observe();
		ProcessSnapshot process = screen.gateway().currentProcess();
		int x = screen.contentX();
		int y = screen.contentY() + 28;
		int color = 0xFFE6E6E6;
		graphics.text(screen.font(), Component.translatable(
				"screen.baritone-automate.process.line",
				textOrNone(screen, snap.processName()),
				yesNo(screen, snap.pathing()),
				yesNo(screen, snap.paused()),
				EtaFormat.duration(snap)), x, y, color, false);
		y += 12;
		String goal = snap.goalSummary().isBlank()
				? Component.translatable("screen.baritone-automate.none").getString()
				: snap.goalSummary();
		graphics.text(screen.font(), Component.translatable("screen.baritone-automate.process.goal", goal), x, y, color, false);
		y += 12;
		graphics.text(screen.font(), Component.translatable(
				"screen.baritone-automate.process.last",
				process.kind().name(),
				process.outcome().name(),
				process.detail()), x, y, 0xFFC8C8C8, false);
		y += 16;
		graphics.text(screen.font(), Component.translatable("screen.baritone-automate.process.hint"), x, y, 0xFF9A9A9A, false);
	}

	private static String textOrNone(ControlScreen screen, String value) {
		if (value == null || value.isBlank()) {
			return Component.translatable("screen.baritone-automate.none").getString();
		}
		return value;
	}

	private static String yesNo(ControlScreen screen, boolean value) {
		return Component.translatable(value ? "screen.baritone-automate.yes" : "screen.baritone-automate.no").getString();
	}
}
