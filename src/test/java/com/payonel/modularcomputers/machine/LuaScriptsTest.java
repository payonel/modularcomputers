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
		assertThat(LuaScripts.read("eeprom/hello")).startsWith("-- hello, world").contains("board.halt()");
		assertThatThrownBy(() -> LuaScripts.read("eeprom/nothing")).hasMessageContaining("eeprom/nothing.lua");
	}

	@Test
	void everyScriptInTheModCompiles() throws Exception {
		Path root = Path.of(LuaScripts.class.getResource(LuaScripts.ROOT).toURI());
		List<Path> scripts;
		try (Stream<Path> files = Files.walk(root)) {
			scripts = files.filter(file -> file.toString().endsWith(".lua")).toList();
		}
		assertThat(scripts).isNotEmpty();
		for (Path script : scripts) {
			String name = root.relativize(script).toString().replace('\\', '/').replaceFirst("\\.lua$", "");
			try {
				new LuaRunner(LuaScripts.read(name));
			} catch (LuaRunner.LoadFailed failed) {
				throw new AssertionError(name + ".lua doesn't compile: " + failed.getMessage());
			}
		}
	}
}
