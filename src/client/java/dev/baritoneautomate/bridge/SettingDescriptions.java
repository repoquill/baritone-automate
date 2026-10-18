package dev.baritoneautomate.bridge;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Readable blurbs for Baritone settings. The API {@code Setting} type has no
 * description field; the text comes from the field comments in Baritone's
 * {@code Settings.java}. Names that are not in that table are split into words.
 */
public final class SettingDescriptions {
	private static final Map<String, String> FROM_SOURCE = load();

	private SettingDescriptions() {
	}

	public static String of(String name) {
		if (name == null || name.isBlank()) {
			return "Setting";
		}
		String sourced = FROM_SOURCE.get(name);
		if (sourced != null && !sourced.isBlank()) {
			return sourced;
		}
		return words(name);
	}

	/** Camel-case fallback, for example {@code allowBreak} becomes {@code Allow Break}. */
	public static String words(String name) {
		if (name == null || name.isBlank()) {
			return "Setting";
		}
		String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim();
		if (spaced.isEmpty()) {
			return "Setting";
		}
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private static Map<String, String> load() {
		Map<String, String> descriptions = new HashMap<>();
		InputStream stream = SettingDescriptions.class.getClassLoader().getResourceAsStream("baritone-setting-descriptions.tsv");
		if (stream == null) {
			return Map.of();
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				int split = line.indexOf('\t');
				if (split <= 0) {
					continue;
				}
				String name = line.substring(0, split).trim();
				String text = line.substring(split + 1).trim();
				if (!name.isEmpty() && !text.isEmpty()) {
					descriptions.put(name, text);
				}
			}
		} catch (Exception ignored) {
			return Map.copyOf(descriptions);
		}
		return Map.copyOf(descriptions);
	}
}
