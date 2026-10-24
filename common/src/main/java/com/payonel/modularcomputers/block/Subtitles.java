package com.payonel.modularcomputers.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.payonel.modularcomputers.ModularComputers;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.locale.Language;

/**
 * What a sound sounds like, in words: the subtitle players get for it, in English ("Pig oinks",
 * "Door creaks"), whatever language a player picked, so programs read the same everywhere. Which
 * subtitle a sound shows is the game's sounds.json's, which servers never have: Minecraft's from a
 * table made from it ({@code scripts/data/sound_subtitles.py}), the mod's own from its own. The
 * text is the game's and the mod's English, from their jars. A sound with no subtitle (or another
 * mod's, whose aren't known here) has none.
 */
public final class Subtitles {
	private static final String TABLE = "/data/modularcomputers/sound_subtitles.json";
	private static final String ENGLISH = "/assets/%s/lang/en_us.json";
	private static final String SOUNDS = "/assets/%s/sounds.json";
	private static Map<String, String> bySound;

	private Subtitles() {
	}

	/** A sound's subtitle (its id: {@code minecraft:entity.pig.ambient}), in English; null for none. */
	public static synchronized String of(String sound) {
		if (bySound == null) {
			bySound = load();
		}
		return bySound.get(sound);
	}

	private static Map<String, String> load() {
		JsonObject english = read(Language.class, ENGLISH.formatted("minecraft"));
		JsonObject ours = read(ModularComputers.class, ENGLISH.formatted(ModularComputers.MOD_ID));
		Map<String, String> table = new HashMap<>();
		read(ModularComputers.class, TABLE).entrySet().forEach(entry -> {
			String key = entry.getValue().getAsString();
			if (english.has(key)) {
				table.put(entry.getKey(), english.get(key).getAsString());
			}
		});
		read(ModularComputers.class, SOUNDS.formatted(ModularComputers.MOD_ID)).entrySet().forEach(entry -> {
			JsonObject event = entry.getValue().getAsJsonObject();
			if (event.has("subtitle") && ours.has(event.get("subtitle").getAsString())) {
				table.put(ModularComputers.MOD_ID + ":" + entry.getKey(), ours.get(event.get("subtitle").getAsString()).getAsString());
			}
		});
		return table;
	}

	private static JsonObject read(Class<?> owner, String path) {
		try (InputStream in = owner.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("missing " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
