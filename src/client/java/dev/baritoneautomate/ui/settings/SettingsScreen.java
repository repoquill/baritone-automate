package dev.baritoneautomate.ui.settings;

import dev.baritoneautomate.bootstrap.BaritoneAutomateClient;
import dev.baritoneautomate.bridge.BaritoneGateway;
import dev.baritoneautomate.bridge.GatewayStatus;
import dev.baritoneautomate.bridge.SettingKind;
import dev.baritoneautomate.bridge.SettingView;
import dev.baritoneautomate.ui.common.ApplyBox;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Edits Baritone settings through {@link BaritoneGateway#setSetting(String, String)}
 * and {@link BaritoneGateway#resetSetting(String)}. There is no description on
 * {@link SettingView}; search uses the id, kind, serialized value, and a small hint
 * table for the common ids. Reset-all walks every setting because the gateway has
 * no bulk reset.
 */
public final class SettingsScreen extends Screen {
	static final List<String> QUICK = List.of(
			"allowBreak",
			"allowPlace",
			"allowSprint",
			"allowParkour",
			"allowParkourPlace",
			"allowInventory",
			"enterPortal");

	private static final Map<String, String> HINTS = Map.of(
			"allowBreak", "允许破坏方块 allow break",
			"allowPlace", "允许放置方块 allow place",
			"allowSprint", "允许疾跑 allow sprint",
			"allowParkour", "允许跑酷 allow parkour",
			"allowParkourPlace", "跑酷时放置 place while parkour",
			"allowInventory", "允许打开容器 allow inventory",
			"enterPortal", "允许进入传送门 enter portal");

	private final Screen parent;
	private final List<SettingView> settings = new ArrayList<>();
	private String query = "";
	private int scroll;
	private int listTop;
	private int visibleRows = 1;
	private String feedback = "";
	private boolean feedbackOk = true;
	private boolean suppress;
	private boolean focusSearch;
	private EditBox search;

	public SettingsScreen(Screen parent) {
		super(Component.translatable("screen.baritone-automate.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (settings.isEmpty()) {
			reload();
		}
		int searchY = 28;
		search = new EditBox(this.font, 8, searchY, this.width - 16, 20, Component.translatable("screen.baritone-automate.settings.search"));
		search.setMaxLength(80);
		search.setHint(Component.translatable("screen.baritone-automate.settings.search_hint"));
		suppress = true;
		search.setValue(query);
		suppress = false;
		search.setResponder(text -> {
			if (suppress || text.equals(query)) {
				return;
			}
			query = text;
			scroll = 0;
			focusSearch = true;
			rebuildWidgets();
		});
		addRenderableWidget(search);
		boolean grabSearch = focusSearch;

		addRenderableWidget(Button.builder(Component.translatable("screen.baritone-automate.settings.reset_all"), button -> confirmResetAll())
				.bounds(this.width - 156, 4, 72, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("screen.baritone-automate.back"), button -> onClose())
				.bounds(this.width - 80, 4, 72, 20)
				.build());

		int x = 8;
		int y = 52;
		int shown = 0;
		for (String name : QUICK) {
			SettingView view = find(name).orElse(null);
			if (view == null || view.kind() != SettingKind.BOOLEAN) {
				continue;
			}
			if (x > 8 && x + 124 > this.width - 8) {
				x = 8;
				y += 22;
			}
			boolean on = isTrue(view.value());
			String captured = name;
			Button toggle = Button.builder(Component.translatable(
					on ? "screen.baritone-automate.settings.quick_on" : "screen.baritone-automate.settings.quick_off",
					name), button -> toggleBoolean(captured))
					.bounds(x, y, 120, 20)
					.build();
			String hint = HINTS.get(name);
			if (hint != null) {
				toggle.setTooltip(Tooltip.create(Component.literal(hint)));
			}
			addRenderableWidget(toggle);
			x += 124;
			shown++;
		}
		listTop = shown == 0 ? 54 : y + 24;
		int listHeight = this.height - listTop - 48;
		visibleRows = Math.max(1, listHeight / 22);
		int maxScroll = Math.max(0, filtered().size() - visibleRows);
		if (scroll > maxScroll) {
			scroll = maxScroll;
		}

		List<SettingView> page = page();
		for (int i = 0; i < page.size(); i++) {
			placeRow(page.get(i), listTop + i * 22);
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.baritone-automate.page_prev"), button -> scrollBy(-1))
				.bounds(8, this.height - 24, 24, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("screen.baritone-automate.page_next"), button -> scrollBy(1))
				.bounds(36, this.height - 24, 24, 20)
				.build());
		if (grabSearch) {
			setInitialFocus(search);
			focusSearch = false;
		}
	}

	private void placeRow(SettingView view, int y) {
		int resetW = 46;
		int editorW = Math.min(150, Math.max(70, this.width / 4));
		int editorX = this.width - 12 - resetW - editorW;
		Tooltip tip = tooltip(view);
		Button reset = Button.builder(Component.translatable("screen.baritone-automate.settings.reset"), button -> resetOne(view.name()))
				.bounds(this.width - 8 - resetW, y, resetW, 20)
				.build();
		reset.setTooltip(tip);
		addRenderableWidget(reset);
		if (view.kind() == SettingKind.BOOLEAN) {
			boolean on = isTrue(view.value());
			Button toggle = Button.builder(Component.translatable(on
					? "screen.baritone-automate.settings.on"
					: "screen.baritone-automate.settings.off"), button -> toggleBoolean(view.name()))
					.bounds(editorX, y, editorW, 20)
					.build();
			toggle.setTooltip(tip);
			addRenderableWidget(toggle);
			return;
		}
		boolean structured = view.kind() == SettingKind.ENUM
				|| view.kind() == SettingKind.LIST
				|| view.kind() == SettingKind.MAP
				|| view.kind() == SettingKind.OTHER
				|| view.kind() == SettingKind.STRING;
		if (structured) {
			int applyW = 36;
			ApplyBox box = new ApplyBox(this.font, editorX, y, editorW - applyW - 2, 20, Component.literal(view.name()));
			box.setMaxLength(256);
			box.setValue(view.value());
			box.setTooltip(tip);
			box.setOnApply(() -> commit(view.name(), box.getValue()));
			addRenderableWidget(box);
			Button apply = Button.builder(Component.translatable("screen.baritone-automate.settings.apply"), button -> commit(view.name(), box.getValue()))
					.bounds(editorX + editorW - applyW, y, applyW, 20)
					.build();
			apply.setTooltip(tip);
			addRenderableWidget(apply);
			return;
		}
		EditBox box = new EditBox(this.font, editorX, y, editorW, 20, Component.literal(view.name()));
		box.setMaxLength(64);
		suppress = true;
		box.setValue(view.value());
		suppress = false;
		box.setTooltip(tip);
		box.setResponder(text -> {
			if (suppress || text.equals(view.value())) {
				return;
			}
			if (!numeric(view.kind(), text)) {
				feedbackOk = false;
				feedback = Component.translatable("screen.baritone-automate.settings.bad_value", view.name()).getString();
				return;
			}
			commit(view.name(), text.trim());
		});
		addRenderableWidget(box);
	}

	private void toggleBoolean(String name) {
		SettingView view = find(name).orElse(null);
		if (view == null) {
			return;
		}
		commit(name, isTrue(view.value()) ? "false" : "true");
		rebuildWidgets();
	}

	private void commit(String name, String value) {
		GatewayStatus status = gateway().setSetting(name, value);
		feedbackOk = status.ok();
		feedback = status.message();
		if (status.ok()) {
			reload();
		}
	}

	private void resetOne(String name) {
		GatewayStatus status = gateway().resetSetting(name);
		feedbackOk = status.ok();
		feedback = status.message();
		if (status.ok()) {
			reload();
			rebuildWidgets();
		}
	}

	private void confirmResetAll() {
		this.minecraft.setScreenAndShow(new ConfirmScreen(accepted -> {
			if (accepted) {
				resetAll();
			}
			this.minecraft.setScreenAndShow(this);
		}, Component.translatable("screen.baritone-automate.settings.reset_all_title"),
				Component.translatable("screen.baritone-automate.settings.reset_all_body")));
	}

	private void resetAll() {
		int ok = 0;
		int failed = 0;
		String last = "";
		for (SettingView view : gateway().getAllSettings()) {
			GatewayStatus status = gateway().resetSetting(view.name());
			if (status.ok()) {
				ok++;
			} else {
				failed++;
				last = status.message();
			}
		}
		reload();
		feedbackOk = failed == 0;
		feedback = Component.translatable("screen.baritone-automate.settings.reset_all_done", ok, failed, last).getString();
	}

	private void scrollBy(int delta) {
		int max = Math.max(0, filtered().size() - visibleRows);
		int next = Math.max(0, Math.min(max, scroll + delta));
		if (next != scroll) {
			scroll = next;
			focusSearch = false;
			rebuildWidgets();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical == 0.0) {
			return false;
		}
		scrollBy(vertical > 0 ? -1 : 1);
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xF0101014);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(this.font, getTitle(), 8, 8, 0xFFFFFFFF, true);
		List<SettingView> page = page();
		if (settings.isEmpty()) {
			graphics.text(this.font, Component.translatable("screen.baritone-automate.settings.empty"), 8, listTop, 0xFFFFCC66, false);
		} else if (filtered().isEmpty()) {
			graphics.text(this.font, Component.translatable("screen.baritone-automate.settings.no_match"), 8, listTop, 0xFFB0B0B0, false);
		}
		for (int i = 0; i < page.size(); i++) {
			SettingView view = page.get(i);
			int y = listTop + i * 22 + 6;
			String star = view.modified() ? "* " : "";
			String javaOnly = view.javaOnly() ? " java " : " ";
			int color = view.modified() ? 0xFFFFE08A : 0xFFE4E4E4;
			String hint = HINTS.getOrDefault(view.name(), view.kind().name());
			int resetW = 46;
			int editorW = Math.min(150, Math.max(70, this.width / 4));
			int editorX = this.width - 12 - resetW - editorW;
			String text = star + view.name() + javaOnly + hint;
			int limit = Math.max(24, editorX - 12);
			while (text.length() > 4 && this.font.width(text) > limit) {
				text = text.substring(0, text.length() - 1);
			}
			graphics.text(this.font, text, 8, y, color, false);
		}
		int max = Math.max(0, filtered().size() - visibleRows);
		graphics.text(
				this.font,
				Component.translatable("screen.baritone-automate.settings.count", filtered().size(), scroll, max),
				68,
				this.height - 18,
				0xFFB0B0B0,
				false);
		if (!feedback.isEmpty()) {
			graphics.text(this.font, feedback, 8, this.height - 36, feedbackOk ? 0xFFB7E3B0 : 0xFFFFB0B0, false);
		}
	}

	private List<SettingView> page() {
		List<SettingView> filtered = filtered();
		int from = Math.min(scroll, filtered.size());
		int to = Math.min(filtered.size(), from + visibleRows);
		return filtered.subList(from, to);
	}

	private List<SettingView> filtered() {
		String needle = query.trim().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			return settings;
		}
		List<SettingView> matched = new ArrayList<>();
		for (SettingView view : settings) {
			if (haystack(view).contains(needle)) {
				matched.add(view);
			}
		}
		return matched;
	}

	private static String haystack(SettingView view) {
		return (view.name() + " " + view.kind().name() + " " + view.valueClass() + " " + view.genericType()
				+ " " + view.value() + " " + view.defaultValue() + " " + HINTS.getOrDefault(view.name(), ""))
				.toLowerCase(Locale.ROOT);
	}

	private Tooltip tooltip(SettingView view) {
		String hint = HINTS.getOrDefault(view.name(), "");
		return Tooltip.create(Component.translatable(
				"screen.baritone-automate.settings.tip",
				view.name(),
				view.kind().name(),
				view.value(),
				view.defaultValue(),
				hint));
	}

	private Optional<SettingView> find(String name) {
		for (SettingView view : settings) {
			if (view.name().equals(name)) {
				return Optional.of(view);
			}
		}
		return Optional.empty();
	}

	private void reload() {
		settings.clear();
		settings.addAll(gateway().getAllSettings());
		settings.sort(Comparator.comparing(SettingView::name));
	}

	private BaritoneGateway gateway() {
		return BaritoneAutomateClient.gateway();
	}

	private static boolean isTrue(String value) {
		return "true".equalsIgnoreCase(value);
	}

	private static boolean numeric(SettingKind kind, String text) {
		try {
			String trimmed = text.trim();
			return switch (kind) {
				case INTEGER -> {
					Integer.parseInt(trimmed);
					yield true;
				}
				case LONG -> {
					Long.parseLong(trimmed);
					yield true;
				}
				case FLOAT -> {
					Float.parseFloat(trimmed);
					yield true;
				}
				case DOUBLE -> {
					Double.parseDouble(trimmed);
					yield true;
				}
				default -> false;
			};
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	@Override
	public void onClose() {
		if (parent == null) {
			super.onClose();
		} else {
			this.minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
