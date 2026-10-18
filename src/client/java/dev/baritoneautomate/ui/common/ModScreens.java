package dev.baritoneautomate.ui.common;

import dev.baritoneautomate.bootstrap.BaritoneAutomateClient;
import dev.baritoneautomate.hud.AutomateHud;
import dev.baritoneautomate.ui.control.ControlScreen;
import dev.baritoneautomate.ui.settings.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opens the control screen and settings screen, and installs the key hook plus HUD. */
public final class ModScreens {
	private static boolean installed;

	private ModScreens() {
	}

	public static void install() {
		if (installed) {
			return;
		}
		installed = true;
		BaritoneAutomateClient.keys().setOpenControlsHook(ModScreens::openControls);
		AutomateHud.register();
	}

	public static void openControls() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof ControlScreen) {
			return;
		}
		minecraft.setScreenAndShow(new ControlScreen());
	}

	public static void openSettings(Screen parent) {
		Minecraft.getInstance().setScreenAndShow(new SettingsScreen(parent));
	}
}
