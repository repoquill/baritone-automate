package dev.baritoneautomate.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.baritoneautomate.Automate;
import dev.baritoneautomate.automate.RunController;
import dev.baritoneautomate.automate.RunState;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import org.slf4j.Logger;

/**
 * Key mappings the frontend can rebind. Pause, cancel, and force-cancel already drive
 * {@link RunController}. Opening the control screen is a hook — this slice does not open a Screen.
 */
public final class KeybindStubs {
	public enum Action {
		OPEN_CONTROLS,
		TOGGLE_PAUSE,
		CANCEL,
		FORCE_CANCEL
	}

	private final RunController runs;
	private final Logger logger;
	private Runnable openControlsHook;
	private Consumer<Action> listener = action -> { };
	private KeyMapping openControls;
	private KeyMapping togglePause;
	private KeyMapping cancelRun;
	private KeyMapping forceCancel;
	private boolean announcedStub;

	public KeybindStubs(RunController runs, Logger logger) {
		this.runs = runs;
		this.logger = logger;
		this.openControlsHook = () -> {
			if (!announcedStub) {
				announcedStub = true;
				logger.info("Open-controls key pressed. No screen is registered; the UI teammate should call KeybindStubs.setOpenControlsHook.");
			}
		};
	}

	public void register() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(Automate.MOD_ID, "controls"));
		openControls = register(category, "key.baritone-automate.open_controls", InputConstants.KEY_K);
		togglePause = register(category, "key.baritone-automate.toggle_pause", InputConstants.KEY_RBRACKET);
		cancelRun = register(category, "key.baritone-automate.cancel_run", InputConstants.KEY_BACKSLASH);
		forceCancel = register(category, "key.baritone-automate.force_cancel", InputConstants.KEY_END);
	}

	public void poll() {
		while (openControls != null && openControls.consumeClick()) {
			fire(Action.OPEN_CONTROLS);
		}
		while (togglePause != null && togglePause.consumeClick()) {
			fire(Action.TOGGLE_PAUSE);
		}
		while (cancelRun != null && cancelRun.consumeClick()) {
			fire(Action.CANCEL);
		}
		while (forceCancel != null && forceCancel.consumeClick()) {
			fire(Action.FORCE_CANCEL);
		}
	}

	public void setOpenControlsHook(Runnable hook) {
		openControlsHook = hook == null ? () -> { } : hook;
	}

	public void setActionListener(Consumer<Action> listener) {
		this.listener = listener == null ? action -> { } : listener;
	}

	public KeyMapping openControls() {
		return openControls;
	}

	public KeyMapping togglePause() {
		return togglePause;
	}

	public KeyMapping cancelRun() {
		return cancelRun;
	}

	public KeyMapping forceCancel() {
		return forceCancel;
	}

	private void fire(Action action) {
		switch (action) {
			case OPEN_CONTROLS -> openControlsHook.run();
			case TOGGLE_PAUSE -> {
				if (runs.snapshot().state() == RunState.PAUSED) {
					runs.resume();
				} else {
					runs.pause();
				}
			}
			case CANCEL, FORCE_CANCEL -> runs.cancel();
		}
		listener.accept(action);
	}

	private static KeyMapping register(KeyMapping.Category category, String translationKey, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
				translationKey,
				InputConstants.Type.KEYBOARD,
				key,
				category));
	}
}
