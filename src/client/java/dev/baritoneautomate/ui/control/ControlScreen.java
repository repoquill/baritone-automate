package dev.baritoneautomate.ui.control;

import dev.baritoneautomate.bootstrap.BaritoneAutomateClient;
import dev.baritoneautomate.automate.RunPhase;
import dev.baritoneautomate.automate.RunState;
import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.bridge.GatewayStatus;
import dev.baritoneautomate.bridge.PathingSnapshot;
import dev.baritoneautomate.bridge.WaypointView;
import dev.baritoneautomate.ui.common.ApplyBox;
import dev.baritoneautomate.ui.common.BaritoneMissingScreen;
import dev.baritoneautomate.ui.common.EtaFormat;
import dev.baritoneautomate.ui.common.ModScreens;
import dev.baritoneautomate.ui.control.panels.AutomatePanel;
import dev.baritoneautomate.ui.control.panels.ControlPanel;
import dev.baritoneautomate.ui.control.panels.ExplorePanel;
import dev.baritoneautomate.ui.control.panels.FollowPanel;
import dev.baritoneautomate.ui.control.panels.GoalPanel;
import dev.baritoneautomate.ui.control.panels.MinePanel;
import dev.baritoneautomate.ui.control.panels.ProcessPanel;
import dev.baritoneautomate.ui.control.panels.WaypointPanel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game control desk. Every pathing call goes through {@link BaritoneGateway}.
 * The screen does not pause the world, so a running path stays visible behind it.
 */
public final class ControlScreen extends Screen {
	public enum Tab {
		CONTROL("screen.baritone-automate.tab.control", new ProcessPanel()),
		GOAL("screen.baritone-automate.tab.goal", new GoalPanel()),
		MINE("screen.baritone-automate.tab.mine", new MinePanel()),
		EXPLORE("screen.baritone-automate.tab.explore", new ExplorePanel()),
		FOLLOW("screen.baritone-automate.tab.follow", new FollowPanel()),
		WAYPOINT("screen.baritone-automate.tab.waypoint", new WaypointPanel()),
		RUN("screen.baritone-automate.tab.run", new AutomatePanel());

		final String key;
		final ControlPanel panel;

		Tab(String key, ControlPanel panel) {
			this.key = key;
			this.panel = panel;
		}
	}

	private Tab tab = Tab.CONTROL;
	private String feedback = "";
	private boolean feedbackOk = true;
	private boolean wide;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;
	private RunState builtRunState;
	private RunPhase builtRunPhase;
	private boolean builtSkipDiamond;
	private boolean builtReady;

	public String goalX = "0";
	public String goalY = "64";
	public String goalZ = "0";
	public String thisway = "16";
	public String blockQuery = "";
	public final List<String> mineIds = new ArrayList<>();
	public String mineQty = "64";
	public boolean exploreAtFeet = true;
	public String exploreX = "0";
	public String exploreZ = "0";
	public String exploreRange = "256";
	public boolean followPlayers = true;
	public String followName = "";
	public String followType = "minecraft:cow";
	public String waypointName = "home";
	public int waypointPage;
	public List<WaypointView> waypoints = List.of();

	public ControlScreen() {
		super(Component.translatable("screen.baritone-automate.control"));
	}

	public boolean ready() {
		return gateway().isBaritonePresent();
	}

	public BaritoneGateway gateway() {
		return BaritoneAutomateClient.gateway();
	}

	public dev.baritoneautomate.automate.RunController runs() {
		return BaritoneAutomateClient.runs();
	}

	public Font font() {
		return this.font;
	}

	public int contentX() {
		return contentX;
	}

	public int contentY() {
		return contentY;
	}

	public int contentWidth() {
		return contentW;
	}

	public int contentHeight() {
		return contentH;
	}

	public void report(GatewayStatus status) {
		if (status == null) {
			feedback = "";
			feedbackOk = false;
			return;
		}
		feedbackOk = status.ok();
		feedback = status.message();
	}

	public void report(boolean ok, String message) {
		feedbackOk = ok;
		feedback = message == null ? "" : message;
	}

	public void rebuild() {
		rebuildWidgets();
	}

	public void confirmForceCancel() {
		this.minecraft.setScreenAndShow(new ConfirmScreen(accepted -> {
			if (accepted) {
				if (runs().snapshot().state().active()) {
					runs().cancel();
					report(true, Component.translatable("screen.baritone-automate.run.cancelled").getString());
				} else {
					report(gateway().forceCancel());
				}
			}
			this.minecraft.setScreenAndShow(this);
		}, Component.translatable("screen.baritone-automate.force.title"),
				Component.translatable("screen.baritone-automate.force.body")));
	}

	public void pauseCurrent() {
		if (runs().snapshot().state().active()) {
			boolean changed = runs().pause();
			report(changed, Component.translatable(changed
					? "screen.baritone-automate.run.paused" : "screen.baritone-automate.run.cannot_pause").getString());
		} else {
			report(gateway().pause());
		}
	}

	public void resumeCurrent() {
		if (runs().snapshot().state() == RunState.PAUSED) {
			report(runs().resume(), Component.translatable("screen.baritone-automate.run.continued").getString());
		} else {
			report(gateway().resume());
		}
	}

	public void cancelCurrent() {
		if (runs().snapshot().state().active()) {
			runs().cancel();
			report(true, Component.translatable("screen.baritone-automate.run.cancelled").getString());
		} else {
			report(gateway().cancel());
		}
	}

	public Button addButton(Component label, int x, int y, int width, boolean requiresBaritone, Button.OnPress onPress) {
		Button button = Button.builder(label, onPress).bounds(x, y, width, 20).build();
		if (requiresBaritone && !ready()) {
			button.active = false;
		}
		return addRenderableWidget(button);
	}

	public EditBox addBox(int x, int y, int width, String value, Component narration, Consumer<String> responder) {
		EditBox box = new EditBox(this.font, x, y, width, 20, narration);
		box.setMaxLength(80);
		box.setValue(value == null ? "" : value);
		if (responder != null) {
			box.setResponder(responder);
		}
		return addRenderableWidget(box);
	}

	public ApplyBox addApplyBox(int x, int y, int width, String value, Component narration, Consumer<String> responder) {
		ApplyBox box = new ApplyBox(this.font, x, y, width, 20, narration);
		box.setMaxLength(80);
		box.setValue(value == null ? "" : value);
		if (responder != null) {
			box.setResponder(responder);
		}
		return addRenderableWidget(box);
	}

	public <T extends GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T add(T widget) {
		return addRenderableWidget(widget);
	}

	@Override
	protected void init() {
		var run = runs().snapshot();
		builtRunState = run.state();
		builtRunPhase = run.phase();
		builtSkipDiamond = run.skipDiamond();
		builtReady = ready();
		wide = this.width >= 520;
		int header = ready() ? 40 : 62;
		contentY = header;
		int footer = 46;
		if (wide) {
			contentX = 90;
			contentW = this.width - contentX - 8;
			contentH = this.height - header - footer;
			int y = contentY;
			for (Tab candidate : Tab.values()) {
				addTab(candidate, 8, y, 78);
				y += 22;
			}
		} else {
			contentX = 8;
			int tabW = Math.max(36, (this.width - 16) / Tab.values().length);
			int x = 8;
			for (Tab candidate : Tab.values()) {
				addTab(candidate, x, contentY, tabW - 2);
				x += tabW;
			}
			contentY += 24;
			contentW = this.width - 16;
			contentH = this.height - contentY - footer;
		}
		tab.panel.build(this);
		int y = this.height - 24;
		int x = 8;
		int w = 52;
		addButton(Component.translatable("screen.baritone-automate.pause"), x, y, w, true, button -> pauseCurrent());
		x += w + 4;
		addButton(Component.translatable("screen.baritone-automate.resume"), x, y, w, true, button -> resumeCurrent());
		x += w + 4;
		addButton(Component.translatable("screen.baritone-automate.cancel"), x, y, w, true, button -> cancelCurrent());
		x += w + 4;
		addButton(Component.translatable("screen.baritone-automate.force"), x, y, w, true, button -> confirmForceCancel());
		addButton(Component.translatable("screen.baritone-automate.settings"), this.width - 78, y, 70, false,
				button -> ModScreens.openSettings(this));
		if (!ready()) {
			addButton(Component.translatable("screen.baritone-automate.missing.more"), this.width - 78, 36, 70, false,
					button -> this.minecraft.setScreenAndShow(new BaritoneMissingScreen(this)));
		}
	}

	@Override
	public void tick() {
		super.tick();
		var run = runs().snapshot();
		if (ready() != builtReady || (tab == Tab.RUN && (run.state() != builtRunState
				|| run.phase() != builtRunPhase || run.skipDiamond() != builtSkipDiamond))) {
			rebuild();
		}
	}

	private void addTab(Tab candidate, int x, int y, int width) {
		Component label = Component.translatable(candidate.key);
		if (candidate == tab) {
			label = Component.literal("[").append(label).append("]");
		}
		addRenderableWidget(Button.builder(label, button -> {
			tab = candidate;
			rebuild();
		}).bounds(x, y, width, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xF0101014);
		graphics.fill(0, 0, this.width, 24, 0xF0162433);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(this.font, getTitle(), 8, 6, 0xFFFFFFFF, true);
		PathingSnapshot snap = gateway().observe();
		graphics.text(this.font, statusText(snap), 8, 26, 0xFFD0D0D0, false);
		if (!ready()) {
			graphics.fill(8, 38, this.width - 84, 56, 0xE06A3A22);
			graphics.text(this.font, Component.translatable("screen.baritone-automate.missing.banner"), 12, 42, 0xFFFFD0B0, false);
		}
		if (!feedback.isEmpty()) {
			graphics.text(this.font, feedback, 8, this.height - 40, feedbackOk ? 0xFFB7E3B0 : 0xFFFFB0B0, false);
		}
		tab.panel.paint(graphics, this);
	}

	private Component statusText(PathingSnapshot snap) {
		String process = snap.processName().isBlank()
				? Component.translatable("screen.baritone-automate.none").getString()
				: snap.processName();
		String pathing = Component.translatable(snap.pathing()
				? "screen.baritone-automate.yes"
				: "screen.baritone-automate.no").getString();
		String paused = snap.paused()
				? " · " + Component.translatable("screen.baritone-automate.paused").getString()
				: "";
		String goal = snap.goalSummary().isBlank() ? "" : " · " + snap.goalSummary();
		return Component.translatable(
				"screen.baritone-automate.status",
				process,
				pathing,
				EtaFormat.duration(snap),
				paused + goal);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
