package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payonel.modularcomputers.runtime.LuaRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The mod's Lua scripts: read as their bytes, and every one of them compiles. */
class LuaScriptsTest {
	@Test
	void aScriptIsReadAsItsBytes() {
		assertThat(LuaScripts.read("eeprom/hello")).startsWith("-- hello, world").contains("board.waitForInterrupt()");
		assertThatThrownBy(() -> LuaScripts.read("eeprom/nothing")).hasMessageContaining("eeprom/nothing.lua");
	}

	@Test
	void everyScriptInTheModCompiles() throws Exception {
		Path root = Path.of(LuaScripts.class.getResource(LuaScripts.ROOT).toURI());
		List<Path> scripts;
		try (Stream<Path> files = Files.walk(root)) {
			// every file is Lua (mcos's programs have no .lua, as Linux's), but data: man pages, under share/,
			// mcos's settings and sh scripts in /etc (its services, in /etc/rc.d, are Lua), and the user's in /home
			scripts = files.filter(Files::isRegularFile).map(file -> root.relativize(file).toString().replace('\\', '/'))
				.filter(name -> !name.contains("/share/") && !(name.startsWith("mcos/etc/") && !name.startsWith("mcos/etc/rc.d/"))
					&& !name.startsWith("mcos/" + LuaScripts.HOME))
				.map(root::resolve).toList();
		}
		assertThat(scripts).isNotEmpty();
		for (Path script : scripts) {
			String name = root.relativize(script).toString().replace('\\', '/');
			String error = LuaRunner.check(Files.readString(script, java.nio.charset.StandardCharsets.ISO_8859_1), name);
			assertThat(error).as(name + " compiles").isNull();
		}
	}

	@Test
	void aSetOfFilesIsCopiedAsItIs(@org.junit.jupiter.api.io.TempDir Path target) throws Exception {
		LuaScripts.copyTree("mcos", target.resolve("disk"));
		assertThat(target.resolve("disk/init.lua")).isRegularFile();
		assertThat(target.resolve("disk/bin/sh")).isRegularFile();
		assertThat(Files.readString(target.resolve("disk/init.lua"))).isEqualTo(LuaScripts.read("mcos/init"));
	}
}
