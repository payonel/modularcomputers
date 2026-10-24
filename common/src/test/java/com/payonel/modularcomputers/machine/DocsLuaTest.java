package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.LuaRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The manual's Lua (docs/): every ```lua block and every docs/examples program compiles, so a page
 * never shows a typo. A block that's only a fragment (a signature, an ellipsis) says so with a
 * {@code <!-- lua-fragment -->} line before it.
 */
class DocsLuaTest {
	/** A fence's opening line and body, and the line before it. */
	private static final Pattern BLOCK = Pattern.compile("(?m)^([^\\n]*)\\n```lua\\n(.*?)\\n```$", Pattern.DOTALL);
	private static final String FRAGMENT = "<!-- lua-fragment -->";
	/** A page shows an example program by including it. */
	private static final Pattern INCLUDE = Pattern.compile("\\{% include ([\\w.-]+\\.lua) %\\}");

	private static Path docs() {
		Path dir = Path.of("").toAbsolutePath();
		while (!Files.isDirectory(dir.resolve("docs/examples"))) {
			dir = dir.getParent();
		}
		return dir.resolve("docs");
	}

	@Test
	void everyLuaBlockInTheManualCompiles() throws IOException {
		Path docs = docs();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		try (Stream<Path> files = Files.walk(docs)) {
			for (Path page : files.filter(f -> f.toString().endsWith(".md")).toList()) {
				Matcher m = BLOCK.matcher(Files.readString(page));
				while (m.find()) {
					if (m.group(1).strip().equals(FRAGMENT)) {
						continue;
					}
					checked++;
					String code = m.group(2);
					Matcher include = INCLUDE.matcher(code);
					if (include.find()) {
						code = Files.readString(docs.resolve("examples/" + include.group(1)));
					}
					String error = LuaRunner.check(code, "=" + docs.relativize(page));
					if (error != null) {
						problems.add(docs.relativize(page) + ": " + error + "\n" + code);
					}
				}
			}
		}
		assertThat(checked).as("blocks checked").isPositive();
		assertThat(problems).as("blocks that do not compile").isEmpty();
	}

	@Test
	void everyExampleProgramCompiles() throws IOException {
		try (Stream<Path> files = Files.list(docs().resolve("examples"))) {
			List<Path> programs = files.filter(f -> f.toString().endsWith(".lua")).toList();
			assertThat(programs).isNotEmpty();
			for (Path program : programs) {
				assertThat(LuaRunner.check(Files.readString(program), program.getFileName().toString()))
					.as(program.getFileName() + " compiles").isNull();
			}
		}
	}
}
