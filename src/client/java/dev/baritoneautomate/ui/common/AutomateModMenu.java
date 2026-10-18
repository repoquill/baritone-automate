package dev.baritoneautomate.ui.common;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.baritoneautomate.ui.settings.SettingsScreen;

/**
 * Optional Mod Menu entry. The class is only loaded when Mod Menu asks for the
 * {@code modmenu} entrypoint, so a missing Mod Menu does not block the mod.
 */
public final class AutomateModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
