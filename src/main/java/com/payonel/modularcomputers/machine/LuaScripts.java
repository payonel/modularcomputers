package com.payonel.modularcomputers.machine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The Lua scripts that come with the mod (user design, 2026-10-13): plain UTF-8 {@code .lua} files
 * under {@code data/modularcomputers/lua/} in its resources (firmware in {@code eeprom/}, and the OS
 * and utilities as they come), copied onto items when the game makes them: a new EEPROM, and later
 * trades, loot and special crafting. A script is read as its bytes, one character a byte
 * (ISO-8859-1), as an EEPROM holds it and Lua compiles it, so UTF-8 text in it stays as written.
 */
public final class LuaScripts {
	/** Where they are, on the class path. */
	public static final String ROOT = "/data/modularcomputers/lua/";

	private LuaScripts() {
	}

	/** {@code name}'s script ({@code "eeprom/hello"} is {@code eeprom/hello.lua}). A missing one is a bug in the mod. */
	public static String read(String name) {
		String path = ROOT + name + ".lua";
		try (InputStream in = LuaScripts.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("no Lua script " + path + " in the mod");
			}
			return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
