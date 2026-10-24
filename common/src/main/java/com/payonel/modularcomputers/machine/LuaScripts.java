package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.platform.Platform;
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

	/**
	 * Copies a set of the mod's Lua files ({@code <name>/} under {@link #ROOT}, mcos's say) into
	 * {@code target}, as they are, folders and all: a disk that comes with files gets them this way.
	 */
	public static void copyTree(String name, java.nio.file.Path target) {
		copyTree(name, target, false);
	}

	/**
	 * {@link #copyTree(String, java.nio.file.Path)}, writing over files already there when
	 * {@code replace} (an update: everything else on the disk is left). Returns what it wrote: the
	 * files, and the folders it made.
	 */
	public static java.util.List<java.nio.file.Path> copyTree(String name, java.nio.file.Path target, boolean replace) {
		String folder = ROOT + name;
		java.util.List<String> files = Platform.INSTANCE.files(folder);
		if (files.isEmpty()) {
			throw new IllegalStateException("no Lua files " + folder + " in the mod");
		}
		java.util.List<java.nio.file.Path> written = new java.util.ArrayList<>();
		try {
			for (String file : files) {
				java.nio.file.Path to = target.resolve(file);
				for (java.nio.file.Path dir = to.getParent(); dir != null && dir.startsWith(target) && !java.nio.file.Files.exists(dir); dir = dir.getParent()) {
					written.add(0, dir);
				}
				java.nio.file.Files.createDirectories(to.getParent());
				try (InputStream in = LuaScripts.class.getResourceAsStream(folder + "/" + file)) {
					if (replace) {
						java.nio.file.Files.copy(in, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					} else {
						java.nio.file.Files.copy(in, to);
					}
				}
				written.add(to);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return written;
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
