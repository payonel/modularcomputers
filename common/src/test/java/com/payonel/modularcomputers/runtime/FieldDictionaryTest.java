package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The field dictionary (.agents/FIELDS.md, the user's, 2026-11-10) checked against the code: a scan
 * of the devices' sources, not a run of them. Every event a device names is one of its methods or
 * one of its own-news words the dictionary lists; no own-news word is a method's name; and every
 * field a device puts in an answer or an event is a word the dictionary defines. A word a scan can't
 * see (carried in a variable) has to be one of the carriers listed here, each with where its word
 * comes from (rule 2: a fixed field's word is a literal or a constant, or came from one).
 */
class FieldDictionaryTest {
	/** Where the mod's sources are, from the repository's root. */
	private static final String SOURCES = "common/src/main/java/com/payonel/modularcomputers";
	/** The packages whose maps are answers and events. */
	private static final List<String> PACKAGES = List.of("device", "entity", "machine", "internet", "block/CardWorld.java");
	/** The builder: its own literals are what it builds from its callers' words (checked at the callers). */
	private static final String BUILDER = "EventQueue.java";
	/** Maps that are no answer: what a device saves of itself, a header table, a block's properties by the game's names. */
	private static final Set<String> NOT_ANSWERS = Set.of("saved", "tag", "out", "output", "headers", "properties");
	/** The EventQueue builders that take an event's name first. */
	private static final String BUILDERS = "ended|stopped|blocked|drifted|fault|refused|interrupted|lost|unbreakable|missingInputs|noSpace|done|failed|stoppedEnd";
	/**
	 * Words carried in a variable, by file and variable, each with where it comes from: the scan sees
	 * no literal there, so each is named and justified here, and a new one fails till it is.
	 */
	private static final Map<String, String> CARRIERS = Map.of(
		"RoboticPiston.java method", "the running command's name: the method the board recorded as it started (push, extend, retract)",
		"InternetCard.java entry.remove(\"event\")", "the end EventQueue built from REQUEST, moved to the entry's front");

	private static Path root() {
		Path dir = Path.of("").toAbsolutePath();
		while (!Files.isDirectory(dir.resolve(".agents"))) {
			dir = dir.getParent();
		}
		return dir;
	}

	private static String dictionary() throws IOException {
		return Files.readString(root().resolve(".agents/FIELDS.md"));
	}

	/** A section's text: from its heading to the next. */
	private static String section(String text, String heading) {
		int start = text.indexOf("## " + heading);
		int end = text.indexOf("\n## ", start + 1);
		return text.substring(start, end < 0 ? text.length() : end);
	}

	/** The words in backticks in a table row's cell, its parenthesised notes left out. */
	private static Set<String> words(String cell) {
		Set<String> words = new HashSet<>();
		Matcher word = Pattern.compile("`([A-Za-z_]+)`").matcher(cell.replaceAll("\\([^)]*\\)", ""));
		while (word.find()) {
			words.add(word.group(1));
		}
		return words;
	}

	/** Every word the dictionary defines as a field: its Fields table's, and each why's fields. */
	private static Set<String> fields() throws IOException {
		String text = dictionary();
		Set<String> fields = new HashSet<>();
		for (String row : section(text, "Fields").split("\n")) {
			if (row.startsWith("| `")) {
				fields.addAll(words(row.split("\\|")[1]));
			}
		}
		for (String row : section(text, "Whys").split("\n")) {
			if (row.startsWith("| `")) {
				fields.addAll(words(row.split("\\|")[2]));
			}
		}
		return fields;
	}

	/** Each class's own-news words, from the dictionary's table. */
	private static Map<String, Set<String>> ownNews() throws IOException {
		Map<String, Set<String>> news = new HashMap<>();
		for (String row : section(dictionary(), "Events").split("\n")) {
			String[] cells = row.split("\\|");
			if (cells.length > 3 && cells[2].contains("`")) {
				for (String type : words(cells[2])) {
					news.computeIfAbsent(type, k -> new HashSet<>()).addAll(words(cells[3]));
				}
			}
		}
		return news;
	}

	private static List<Path> sources() throws IOException {
		List<Path> files = new ArrayList<>();
		Path base = root().resolve(SOURCES);
		for (String place : PACKAGES) {
			Path at = base.resolve(place);
			if (Files.isRegularFile(at)) {
				files.add(at);
				continue;
			}
			try (Stream<Path> walk = Files.list(at)) {
				walk.filter(file -> file.toString().endsWith(".java")).forEach(files::add);
			}
		}
		return files;
	}

	/** A source's text without the bodies of its saved() methods (what it saves of itself is no answer). */
	private static String withoutSaves(String source) {
		StringBuilder kept = new StringBuilder();
		Matcher saved = Pattern.compile("Map<String, Object> saved\\(\\)\\s*\\{").matcher(source);
		int from = 0;
		while (saved.find(from)) {
			kept.append(source, from, saved.start());
			int depth = 1;
			int at = saved.end();
			while (depth > 0 && at < source.length()) {
				char c = source.charAt(at++);
				depth += c == '{' ? 1 : c == '}' ? -1 : 0;
			}
			from = at;
		}
		return kept.append(source.substring(from)).toString();
	}

	/** Every String constant in the sources, by its name (a word written once, used by name). */
	private static Map<String, Set<String>> constants() throws IOException {
		Map<String, Set<String>> constants = new HashMap<>();
		try (Stream<Path> walk = Files.walk(root().resolve(SOURCES))) {
			for (Path file : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
				Matcher constant = Pattern.compile("static final String ([A-Z_][A-Z_0-9]*) = \"([a-z_]+)\"").matcher(Files.readString(file));
				while (constant.find()) {
					constants.computeIfAbsent(constant.group(1), k -> new HashSet<>()).add(constant.group(2));
				}
			}
		}
		return constants;
	}

	/** A word written at an event's site: a literal, a constant by name, or null (carried in a variable). */
	private static Set<String> resolved(String token, Map<String, Set<String>> constants) {
		String at = token.strip();
		if (at.startsWith("\"")) {
			return Set.of(at.substring(1, at.indexOf('"', 1)));
		}
		String name = at.substring(at.lastIndexOf('.') + 1);
		return name.matches("[A-Z_][A-Z_0-9]*") ? constants.get(name) : null;
	}

	/** A device's methods, by name (its {@code @Method}s: what a program calls). */
	private static Set<String> methods(String source) {
		Set<String> methods = new HashSet<>();
		Matcher method = Pattern.compile("public Result (\\w+)\\(Arguments").matcher(source);
		while (method.find()) {
			methods.add(method.group(1));
		}
		return methods;
	}

	/** Every word a source names an event by, and what it carries in variables (as "file variable"). */
	private static void events(Path file, Map<String, Set<String>> constants, Set<String> named, Set<String> carried) throws IOException {
		String source = Files.readString(file);
		List<String> sites = new ArrayList<>();
		Matcher put = Pattern.compile("\\.put\\(\"event\",\\s*([^;]+?)\\);").matcher(source);
		while (put.find()) {
			sites.add(put.group(1));
		}
		Matcher builder = Pattern.compile("\\b(?:events|log|traffic|EventQueue)\\.(?:" + BUILDERS + ")\\(\\s*([^,)]+)").matcher(source);
		while (builder.find()) {
			sites.add(builder.group(1));
		}
		for (String site : sites) {
			Set<String> words = resolved(site, constants);
			if (words != null) {
				named.addAll(words);
				continue;
			}
			// A parameter passed on: the words are its helper's callers' (event("docked", ...)).
			String variable = site.strip();
			Matcher helper = Pattern.compile("(\\w+)\\(String " + Pattern.quote(variable) + "\\b").matcher(source);
			boolean found = false;
			while (helper.find()) {
				Matcher call = Pattern.compile("\\b" + helper.group(1) + "\\(\\s*(\"[a-z_]+\"|[A-Z_][A-Z_0-9.]*)\\s*[,)]").matcher(source);
				while (call.find()) {
					Set<String> words2 = resolved(call.group(1), constants);
					if (words2 != null) {
						named.addAll(words2);
						found = true;
					}
				}
			}
			if (!found) {
				carried.add(file.getFileName() + " " + variable);
			}
		}
	}

	@Test
	void everyEventIsTheMethodThatCausedItOrItsDevicesOwnNews() throws IOException {
		Map<String, Set<String>> constants = constants();
		Map<String, Set<String>> news = ownNews();
		Set<String> allNews = new HashSet<>();
		news.values().forEach(allNews::addAll);
		List<String> wrong = new ArrayList<>();
		Set<String> carried = new TreeSet<>();
		for (Path file : sources()) {
			if (file.getFileName().toString().equals(BUILDER)) {
				continue;
			}
			Set<String> named = new TreeSet<>();
			events(file, constants, named, carried);
			String type = file.getFileName().toString().replace(".java", "");
			Set<String> methods = methods(Files.readString(file));
			// (a device's: its methods or its own news; elsewhere, CardWorld's for the sensors: some device's own news)
			Set<String> allowed = methods.isEmpty() ? allNews : news.getOrDefault(type, Set.of());
			for (String word : named) {
				if (!methods.contains(word) && !allowed.contains(word)) {
					wrong.add(type + ": event \"" + word + "\" is no method of it and none of its own news in FIELDS.md");
				}
			}
		}
		assertThat(wrong).as("rule 5: an event is the method that caused it, or its device's own news").isEmpty();
		assertThat(carried).as("every word carried in a variable is a known carrier, with where it comes from (and every carrier listed still is one)")
			.isEqualTo(new TreeSet<>(CARRIERS.keySet()));
	}

	@Test
	void noOwnNewsWordIsAMethodsName() throws IOException {
		Path base = root().resolve(SOURCES);
		List<String> wrong = new ArrayList<>();
		for (var entry : ownNews().entrySet()) {
			Path file;
			try (Stream<Path> walk = Files.walk(base)) {
				file = walk.filter(f -> f.getFileName().toString().equals(entry.getKey() + ".java")).findFirst().orElse(null);
			}
			assertThat(file).as("FIELDS.md names a class that exists: " + entry.getKey()).isNotNull();
			Set<String> methods = methods(Files.readString(file));
			for (String word : entry.getValue()) {
				if (methods.contains(word)) {
					wrong.add(entry.getKey() + ": own news \"" + word + "\" is a method's name");
				}
			}
		}
		assertThat(wrong).as("rule 5: an event naming a method means that method caused it").isEmpty();
	}

	@Test
	void everyFieldADevicePutsIsADictionaryWord() throws IOException {
		Set<String> fields = fields();
		Set<String> unknown = new TreeSet<>();
		for (Path file : sources()) {
			String source = withoutSaves(Files.readString(file));
			Matcher put = Pattern.compile("(\\w+)\\.put\\(\"([A-Za-z_]+)\"").matcher(source);
			while (put.find()) {
				if (!NOT_ANSWERS.contains(put.group(1)) && !fields.contains(put.group(2))) {
					unknown.add(put.group(2) + " (" + file.getFileName() + ")");
				}
			}
			Matcher map = Pattern.compile("Map\\.of\\(\"([A-Za-z_]+)\"").matcher(source);
			while (map.find()) {
				if (!fields.contains(map.group(1))) {
					unknown.add(map.group(1) + " (" + file.getFileName() + ")");
				}
			}
		}
		assertThat(unknown).as("rule 1: every field is a word FIELDS.md defines (add it there, with its meaning)").isEmpty();
	}
}
