package dev.baritoneautomate.ui.control.panels;

import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.automate.RunSnapshot;
import dev.baritoneautomate.automate.RunState;
import dev.baritoneautomate.ui.control.ControlScreen;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** One-click run through the dragon, with pause, continue, cancel, and skip. */
public final class AutomatePanel implements ControlPanel {
	@Override
	public void build(ControlScreen screen) {
		int x = screen.contentX();
		int y = screen.contentY();
		RunSnapshot snap = screen.runs().snapshot();
		var start = screen.addButton(Component.translatable("screen.baritone-automate.run.start"), x, y, 150, false, button -> {
			boolean ok = screen.runs().start();
			screen.report(ok, Component.translatable(ok
					? "screen.baritone-automate.run.started"
					: "screen.baritone-automate.run.already").getString());
			screen.rebuild();
		});
		start.active = !snap.state().active();
		var pause = screen.addButton(Component.translatable("screen.baritone-automate.pause"), x + 154, y, 52, false, button -> {
			boolean ok = screen.runs().pause();
			screen.report(ok, Component.translatable(ok
					? "screen.baritone-automate.run.paused"
					: "screen.baritone-automate.run.cannot_pause").getString());
			screen.rebuild();
		});
		pause.active = snap.state() == RunState.RUNNING || snap.state() == RunState.COMBAT;
		var resume = screen.addButton(Component.translatable("screen.baritone-automate.run.continue"), x + 210, y, 52, false, button -> {
			boolean ok = screen.runs().continueRun();
			screen.report(ok, Component.translatable(ok
					? "screen.baritone-automate.run.continued"
					: "screen.baritone-automate.run.cannot_continue").getString());
			screen.rebuild();
		});
		resume.active = snap.state() == RunState.PAUSED || snap.state() == RunState.FAILED;
		int y2 = y + 24;
		screen.addButton(Component.translatable("screen.baritone-automate.cancel"), x, y2, 52, false, button -> {
			screen.runs().cancel();
			screen.report(true, Component.translatable("screen.baritone-automate.run.cancelled").getString());
			screen.rebuild();
		});
		var skip = screen.addButton(Component.translatable("screen.baritone-automate.run.skip"), x + 56, y2, 88, false, button -> {
			boolean ok = screen.runs().skipPhase();
			screen.report(ok, Component.translatable(ok
					? "screen.baritone-automate.run.skipped"
					: "screen.baritone-automate.run.cannot_skip").getString());
			if (ok) {
				screen.rebuild();
			}
		});
		skip.active = snap.state().active();
		screen.addButton(Component.translatable(snap.skipDiamond()
				? "screen.baritone-automate.run.diamond_on"
				: "screen.baritone-automate.run.diamond_off"), x + 148, y2, 100, false, button -> {
			screen.runs().setSkipDiamond(!snap.skipDiamond());
			screen.rebuild();
		});
		screen.addButton(Component.translatable("screen.baritone-automate.run.reset"), x + 252, y2, 72, false, button -> {
			screen.runs().reset();
			screen.report(true, Component.translatable("screen.baritone-automate.run.reset_done").getString());
			screen.rebuild();
		});
	}

	@Override
	public void paint(GuiGraphicsExtractor graphics, ControlScreen screen) {
		RunSnapshot snap = screen.runs().snapshot();
		RunPhase[] phases = RunPhase.values();
		int x = screen.contentX();
		int y = screen.contentY() + 52;
		int bottom = screen.contentY() + screen.contentHeight();
		if (y >= bottom) {
			return;
		}
		int index = snap.phase().ordinal() + 1;
		graphics.text(screen.font(), Component.translatable(
				"screen.baritone-automate.run.progress",
				snap.state().label(),
				snap.state().name(),
				snap.phase().label(),
				snap.phase().name(),
				index,
				phases.length,
				snap.attempt()), x, y, 0xFFE6E6E6, false);
		y += 12;
		int barW = Math.max(20, screen.contentWidth());
		int filled = (int) (barW * (index / (double) phases.length));
		graphics.fill(x, y, x + barW, y + 6, 0xFF2A2A2A);
		graphics.fill(x, y, x + filled, y + 6, 0xFF6FCF97);
		y += 10;
		if (snap.state() == RunState.FAILED) {
			graphics.text(screen.font(), Component.translatable("screen.baritone-automate.run.failed_hint"), x, y, 0xFFFFCC66, false);
			y += 11;
			if (!snap.failureReason().isBlank()) {
				graphics.text(screen.font(), snap.failureReason(), x, y, 0xFFFFB0B0, false);
				y += 11;
			}
		}
		if (snap.detail().toLowerCase(Locale.ROOT).contains("low health")) {
			graphics.text(screen.font(), Component.translatable("screen.baritone-automate.run.low_health"), x, y, 0xFFFFCC66, false);
			y += 11;
		}
		for (RunPhase phase : phases) {
			if (y + 10 > bottom) {
				break;
			}
			boolean current = phase == snap.phase();
			String prefix = current ? "> " : "  ";
			graphics.text(
					screen.font(),
					prefix + (phase.ordinal() + 1) + "/" + phases.length + " " + phase.name() + " " + phase.label(),
					x,
					y,
					current ? 0xFFFFE08A : 0xFFB0B0B0,
					false);
			y += 10;
		}
	}
}
