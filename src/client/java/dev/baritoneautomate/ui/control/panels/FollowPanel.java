package dev.baritoneautomate.ui.control.panels;

import dev.baritoneautomate.ui.common.ClientAim;
import dev.baritoneautomate.ui.control.ControlScreen;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

public final class FollowPanel implements ControlPanel {
	private static final List<String> TYPES = List.of(
			"minecraft:cow",
			"minecraft:pig",
			"minecraft:sheep",
			"minecraft:chicken",
			"minecraft:zombie",
			"minecraft:skeleton",
			"minecraft:creeper",
			"minecraft:spider",
			"minecraft:enderman",
			"minecraft:blaze",
			"minecraft:villager",
			"minecraft:iron_golem");

	@Override
	public void build(ControlScreen screen) {
		int x = screen.contentX();
		int y = screen.contentY();
		screen.addButton(Component.translatable(screen.followPlayers
				? "screen.baritone-automate.follow.mode_player"
				: "screen.baritone-automate.follow.mode_entity"), x, y, 120, false, button -> {
			screen.followPlayers = !screen.followPlayers;
			screen.rebuild();
		});
		if (screen.followPlayers) {
			screen.addBox(x + 124, y, 120, screen.followName, Component.translatable("screen.baritone-automate.follow.name"), text -> screen.followName = text);
			screen.addButton(Component.translatable("screen.baritone-automate.follow.start"), x, y + 24, 100, true,
					button -> screen.report(screen.gateway().followPlayer(screen.followName.trim())));
			int px = x;
			int py = y + 48;
			for (String name : ClientAim.nearbyPlayerNames()) {
				String captured = name;
				int width = Math.min(100, 12 + captured.length() * 6);
				if (px + width > x + screen.contentWidth()) {
					break;
				}
				screen.addButton(Component.literal(captured), px, py, width, true, button -> {
					screen.followName = captured;
					screen.report(screen.gateway().followPlayer(captured));
				});
				px += width + 4;
			}
		} else {
			if (!TYPES.contains(screen.followType)) {
				screen.followType = TYPES.getFirst();
			}
			CycleButton<String> types = CycleButton.builder(Component::literal, screen.followType)
					.withValues(TYPES)
					.displayOnlyValue()
					.create(x, y + 24, 180, 20, Component.translatable("screen.baritone-automate.follow.type"),
							(button, value) -> screen.followType = value);
			screen.add(types);
			screen.addButton(Component.translatable("screen.baritone-automate.follow.start"), x + 184, y + 24, 100, true,
					button -> screen.report(screen.gateway().followEntity(screen.followType)));
		}
	}

	@Override
	public void paint(GuiGraphicsExtractor graphics, ControlScreen screen) {
		int y = screen.contentY() + (screen.followPlayers ? 72 : 52);
		if (y >= screen.contentY() + screen.contentHeight()) {
			return;
		}
		graphics.text(
				screen.font(),
				Component.translatable(screen.followPlayers
						? "screen.baritone-automate.follow.player_hint"
						: "screen.baritone-automate.follow.entity_hint"),
				screen.contentX(),
				y,
				0xFF9A9A9A,
				false);
	}
}
