package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * The manual's Java (docs/examples/*.java, the host's inbox page): compiled against LungoLua alone,
 * as a reader would, and run, so the page shows a program that works.
 */
class DocsJavaTest {
	/** How many messages the inbox example sends (its MESSAGES). */
	private static final int INBOX_MESSAGES = 5;

	private static Path examples() {
		Path dir = Path.of("").toAbsolutePath();
		while (!Files.isDirectory(dir.resolve("docs/examples"))) {
			dir = dir.getParent();
		}
		return dir.resolve("docs/examples");
	}

	@Test
	@ResourceLock(Resources.SYSTEM_OUT)
	void theInboxExampleCompilesWithLungoLuaAloneAndEveryMessageReachesTheProgram() throws Exception {
		Path source = examples().resolve("InboxExample.java");
		Path out = Files.createTempDirectory("inbox-example");
		// LungoLua's jar only, from the test's class path: the example needs nothing of the mod.
		String lungolua = java.util.Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
			.filter(entry -> Path.of(entry).getFileName().toString().startsWith("lungolua-")).findFirst().orElseThrow();
		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		ByteArrayOutputStream errors = new ByteArrayOutputStream();
		int status = javac.run(null, null, errors, "-cp", lungolua, "-d", out.toString(), source.toString());
		assertThat(status).as("it compiles: " + errors.toString(StandardCharsets.UTF_8)).isZero();

		ByteArrayOutputStream printed = new ByteArrayOutputStream();
		PrintStream was = System.out;
		try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] {out.toUri().toURL(), Path.of(lungolua).toUri().toURL()}, null)) {
			System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
			loader.loadClass("InboxExample").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
		} finally {
			System.setOut(was);
			try (java.util.stream.Stream<Path> made = Files.walk(out)) {
				made.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete()); // (its classes, then the folder)
			}
		}
		String text = printed.toString(StandardCharsets.UTF_8);
		// (Lua's print goes to the process's own stdout, not System.out: the host's lines are read.)
		for (int i = 1; i <= INBOX_MESSAGES; i++) {
			assertThat(text).as("message " + i + " queued at a freeze").contains("queued \"reading " + i + "\"");
		}
		// The program returns only once it has read every message from its table.
		assertThat(text).as("the program read them all and returned").contains("done:");
	}
}
