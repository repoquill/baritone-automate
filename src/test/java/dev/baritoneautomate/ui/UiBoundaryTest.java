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
