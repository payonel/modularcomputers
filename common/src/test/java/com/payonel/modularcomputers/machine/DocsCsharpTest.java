package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The manual's C# (docs/examples/InboxExample.cs and its project, the host's inbox page): built as
 * the page says, against LungoLua's C# port alone (lungolua.dll, from its package), with the .NET 10
 * SDK, and run, so the page shows a program that works. Nothing of the mod is in it.
 */
class DocsCsharpTest {
	/** How many messages the inbox example sends (its MESSAGES). */
	private static final int INBOX_MESSAGES = 5;
	/** The .NET SDK the example's project targets (net10.0). */
	private static final String DOTNET_SDK_MAJOR = "10.";
	/** How long `dotnet build` may take: a cold SDK's first build on a loaded host, with room. */
	private static final long BUILD_SECONDS = 300;
	/** How long the example may run: its producer sends a message a tenth of a second, with room. */
	private static final long RUN_SECONDS = 120;

	private static Path examples() {
		Path dir = Path.of("").toAbsolutePath();
		while (!Files.isDirectory(dir.resolve("docs/examples"))) {
			dir = dir.getParent();
		}
		return dir.resolve("docs/examples");
	}

	/** Runs a command in a folder, answering what it printed; it must exit 0 in time. */
	private static String run(Path dir, long seconds, String... command) throws Exception {
		Path log = Files.createTempFile("dotnet", ".log");
		try {
			ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true)
				.redirectOutput(log.toFile());
			// No first-run banner or telemetry, and the SDK's caches in the test's own folder.
			builder.environment().put("DOTNET_NOLOGO", "1");
			builder.environment().put("DOTNET_CLI_TELEMETRY_OPTOUT", "1");
			builder.environment().put("DOTNET_SKIP_FIRST_TIME_EXPERIENCE", "1");
			builder.environment().put("DOTNET_CLI_HOME", dir.toString());
			Process process = builder.start();
			boolean ended = process.waitFor(seconds, TimeUnit.SECONDS);
			if (!ended) {
				process.destroyForcibly();
			}
			String text = Files.readString(log, StandardCharsets.UTF_8);
			assertThat(ended).as(String.join(" ", command) + " ends in time: " + text).isTrue();
			assertThat(process.exitValue()).as(String.join(" ", command) + " succeeds: " + text).isZero();
			return text;
		} finally {
			Files.deleteIfExists(log);
		}
	}

	@Test
	void theInboxExampleBuildsWithLungoLuasCsharpPortAloneAndEveryMessageReachesTheProgram() throws Exception {
		String dotnet = System.getProperty("modularcomputers.dotnet", "dotnet");
		Path dll = Path.of(System.getProperty("modularcomputers.lungoluaCsharp"));
		Path dir = Files.createTempDirectory("inbox-example-cs");
		try {
			String sdks = run(dir, BUILD_SECONDS, dotnet, "--list-sdks");
			assertThat(sdks.lines().anyMatch(line -> line.startsWith(DOTNET_SDK_MAJOR)))
				.as("the .NET 10 SDK is there (" + dotnet + "; set the `dotnet` Gradle property to its path): " + sdks).isTrue();
			// The page's two files and lungolua.dll beside them, as the page says.
			for (String name : List.of("InboxExample.cs", "InboxExample.csproj")) {
				Files.copy(examples().resolve(name), dir.resolve(name));
			}
			Files.copy(dll, dir.resolve("lungolua.dll"), StandardCopyOption.REPLACE_EXISTING);
			run(dir, BUILD_SECONDS, dotnet, "build", "-o", "out");
			String text = run(dir, RUN_SECONDS, dotnet, dir.resolve("out/InboxExample.dll").toString());
			for (int i = 1; i <= INBOX_MESSAGES; i++) {
				assertThat(text).as("message " + i + " queued at a freeze").contains("queued \"reading " + i + "\"");
				assertThat(text).as("message " + i + " read by the program").contains("read \"reading " + i + "\"");
			}
			// The program returns only once it has read every message from its table.
			assertThat(text).as("the program read them all and returned").contains("done:");
		} finally {
			try (Stream<Path> made = Files.walk(dir)) {
				made.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete()); // (its files, then the folder)
			}
		}
	}
}
