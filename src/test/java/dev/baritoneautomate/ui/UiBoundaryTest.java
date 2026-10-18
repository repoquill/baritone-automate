package dev.baritoneautomate.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class UiBoundaryTest {
	@Test
	void uiAndHudDoNotCallBaritoneApi() throws IOException {
		assertNoBaritoneApi(Path.of("src/client/java/dev/baritoneautomate/ui"));
		assertNoBaritoneApi(Path.of("src/client/java/dev/baritoneautomate/hud"));
	}

	@Test
	void screensExist() {
		assertTrue(Files.exists(Path.of("src/client/java/dev/baritoneautomate/ui/control/ControlScreen.java")));
		assertTrue(Files.exists(Path.of("src/client/java/dev/baritoneautomate/ui/settings/SettingsScreen.java")));
		assertTrue(Files.exists(Path.of("src/client/java/dev/baritoneautomate/hud/AutomateHud.java")));
	}

	@Test
	void explorePanelSendsTheRange() throws IOException {
		String panel = Files.readString(Path.of("src/client/java/dev/baritoneautomate/ui/control/panels/ExplorePanel.java"));
		assertTrue(panel.contains("explore(x, z, range)"));
		assertFalse(panel.contains("is not sent"));
		String english = Files.readString(Path.of("src/main/resources/assets/baritone-automate/lang/en_us.json"));
		String chinese = Files.readString(Path.of("src/main/resources/assets/baritone-automate/lang/zh_cn.json"));
		assertFalse(english.contains("is not sent"));
		assertFalse(chinese.contains("不会发给"));
		assertTrue(english.contains("inverted explore filter"));
		assertTrue(chinese.contains("反向探索过滤"));
	}

	private static void assertNoBaritoneApi(Path root) throws IOException {
		try (Stream<Path> files = Files.walk(root)) {
			files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
				try {
					String source = Files.readString(path);
					assertFalse(source.contains("baritone.api"), path + " imports Baritone");
					assertFalse(source.contains("BaritoneAPI"), path + " calls BaritoneAPI");
				} catch (IOException exception) {
					throw new IllegalStateException(exception);
				}
			});
		}
	}
}
