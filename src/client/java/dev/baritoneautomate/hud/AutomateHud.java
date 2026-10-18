package dev.baritoneautomate.hud;

import dev.baritoneautomate.Automate;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.automate.RunSnapshot;
import dev.baritoneautomate.automate.RunState;
import dev.baritoneautomate.bootstrap.BaritoneAutomateClient;
import dev.baritoneautomate.bridge.PathingSnapshot;
import dev.baritoneautomate.combat.CombatMode;
import dev.baritoneautomate.combat.CombatSnapshot;
import dev.baritoneautomate.ui.common.EtaFormat;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Top-left HUD. It follows the GUI scale already applied by the HUD pass, and it
 * stays quiet while a screen is open so it does not cover chat or the control screen.
 */
public final class AutomateHud {
	private static boolean registered;

	private AutomateHud() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(Automate.MOD_ID, "hud"),
				AutomateHud::render);
	}

	static boolean shouldShow(RunSnapshot run, PathingSnapshot path, CombatSnapshot combat) {
		if (run.state() != RunState.IDLE) {
			return true;
		}
		if (path.pathing() || !path.processName().isBlank()) {
			return true;
		}
		return combat.mode() != CombatMode.IDLE;
	}

	private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.screen() != null || minecraft.font == null) {
			return;
		}
		RunSnapshot run = BaritoneAutomateClient.runs().snapshot();
		PathingSnapshot path = BaritoneAutomateClient.gateway().observe();
		CombatSnapshot combat = BaritoneAutomateClient.combat().snapshot();
		if (!shouldShow(run, path, combat)) {
			return;
		}
		int x = 6;
		int y = 6;
		int color = 0xFFE8E8E8;
		if (run.state() != RunState.IDLE) {
			int index = run.phase().ordinal() + 1;
			int total = RunPhase.values().length;
			graphics.text(
					minecraft.font,
					Component.translatable(
							"hud.baritone-automate.stage",
							run.phase().name(),
							run.phase().label(),
							index,
							total),
					x,
					y,
					color,
					true);
		} else {
			String process = path.processName().isBlank()
					? Component.translatable("hud.baritone-automate.no_process").getString()
					: path.processName();
			graphics.text(minecraft.font, Component.translatable("hud.baritone-automate.process", process), x, y, color, true);
		}
		y += 11;
		graphics.text(
				minecraft.font,
				Component.translatable(
						"hud.baritone-automate.status",
						run.state().label(),
						run.state().name(),
						EtaFormat.duration(path)),
				x,
				y,
				0xFFD6D6D6,
				true);
		boolean fighting = run.state() == RunState.COMBAT || combat.mode() != CombatMode.IDLE;
		if (fighting) {
			y += 11;
			graphics.text(
					minecraft.font,
					Component.translatable("hud.baritone-automate.combat", combat.mode().name(), combat.detail()),
					x,
					y,
					0xFFFFB0B0,
					true);
		}
		if (run.detail().toLowerCase(Locale.ROOT).contains("low health")) {
			y += 11;
			graphics.text(minecraft.font, Component.translatable("hud.baritone-automate.low_health"), x, y, 0xFFFFCC66, true);
		}
	}
}
