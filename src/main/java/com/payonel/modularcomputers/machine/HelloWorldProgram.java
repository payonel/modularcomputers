package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.VirtualBus;
import com.payonel.modularcomputers.runtime.Board;
import com.payonel.modularcomputers.runtime.Handle;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Stand-in EEPROM contents until there's a Lua runtime, written as a mock program against the
 * motherboard API (the user changes it to try things out; tests never use it). Today it's a tiny
 * OS, after OpenOS and busybox, all in this one class (the user's wish):
 * <ul>
 * <li><b>Files:</b> a VFS with a mount table. The root, {@code /}, is a tmpfs in memory, like an
 * initramfs, with {@code /mnt} and {@code /tmp}. Disks in hfs mode are mounted from their files
 * (the disk serves them; this OS just calls it), and every one on the bus is mounted
 * automatically, like udisks: {@code sata:N} at {@code /mnt/sata/N}, other disks at
 * {@code /mnt/id/<uuid>}. A disk that leaves the bus is unmounted.</li>
 * <li><b>The shell:</b> on the console (every terminal in its group), with line editing
 * (Backspace, Ctrl+C, Ctrl+U, Ctrl+L, Up and Down for history), quotes and backslashes,
 * {@code ;} between commands, and {@code >} and {@code >>} to send a command's output to a
 * file. {@code help} lists the commands.</li>
 * </ul>
 * Everything goes through the board: the OS is a program like any other.
 */
public final class HelloWorldProgram implements Program {
	private static final double WAIT_SECONDS = 3600;
	private static final String CONSOLE = VirtualBus.CONSOLE;
	private static final int CHUNK = 4096;
	private static final String[][] HELP = {
		{"help", "this list"},
		{"ls [-l] [path...]", "list a directory"},
		{"cd [dir]", "change directory (/ by default)"},
		{"pwd", "print the working directory"},
		{"cat file...", "print files"},
		{"echo [-n] text...", "print text (> file to write it, >> to append)"},
		{"touch file...", "make empty files"},
		{"mkdir [-p] dir...", "make directories"},
		{"rm [-r] path...", "remove files (and directories, with -r)"},
		{"cp src dst", "copy a file"},
		{"mv src dst", "move or rename"},
		{"mount [device dir]", "list mounts, or mount a disk on a directory"},
		{"umount dir|device", "unmount"},
		{"df", "disk space"},
		{"mkfs [-t hfs|raw] device", "format a disk (erases it)"},
		{"lsdev", "devices on the bus"},
		{"free", "memory"},
		{"acpi", "power: the case and its batteries"},
		{"uptime", "time since power-on"},
		{"clear", "clear the screen"},
		{"halt", "stop the CPU"},
	};

	private Board board;
	private String cwd = "/";
	/** The mount table: mount point to what's mounted there. */
	private final TreeMap<String, Mount> mounts = new TreeMap<>();
	private final List<String> history = new ArrayList<>();
	private int columns = 50;

	// ---- the file system layer ---------------------------------------------------------------

	/** A failure to report: the shell prints "command: message". */
	private static final class OsError extends RuntimeException {
		OsError(String message) {
			super(message, null, false, false);
		}
	}

	private record Stat(boolean directory, long size) {
	}

	/** An open file. */
	private interface OpenFile {
		/** Up to {@code count} characters, or null at the end. */
		String read(int count);

		void write(String data);

		void close();
	}

	/** A mounted file system; paths are its own, from its root. */
	private interface Fs {
		String type();

		List<String> list(String path);

		Stat stat(String path);

		void mkdir(String path);

		void remove(String path);

		void rename(String from, String to);

		OpenFile open(String path, String mode);

		/** Total and used bytes. */
		long[] space();
	}

	/** What's mounted at a mount point: the device's address ("tmpfs" for memory) and the file system. */
	private record Mount(String source, Fs fs) {
	}

	/** A file system in memory: the root, like an initramfs. */
	private final class TmpFs implements Fs {
		private final class Node {
			final TreeMap<String, Node> children; // null for a file
			final StringBuilder data;

			Node(boolean directory) {
				children = directory ? new TreeMap<>() : null;
				data = directory ? null : new StringBuilder();
			}
		}

		private final Node root = new Node(true);

		@Override
		public String type() {
			return "tmpfs";
		}

		private Node find(String path) {
			Node node = root;
			for (String name : names(path)) {
				node = node.children == null ? null : node.children.get(name);
				if (node == null) {
					return null;
				}
			}
			return node;
		}

		private Node parent(String path) {
			List<String> names = names(path);
			if (names.isEmpty()) {
				throw new OsError("not allowed on /");
			}
			Node parent = find(String.join("/", names.subList(0, names.size() - 1)));
			if (parent == null || parent.children == null) {
				throw new OsError("no such directory: " + path);
			}
			return parent;
		}

		private String leaf(String path) {
			List<String> names = names(path);
			return names.getLast();
		}

		@Override
		public List<String> list(String path) {
			Node node = find(path);
			if (node == null || node.children == null) {
				throw new OsError("not a directory: " + path);
			}
			return node.children.entrySet().stream().map(entry -> entry.getKey() + (entry.getValue().children != null ? "/" : "")).toList();
		}

		@Override
		public Stat stat(String path) {
			Node node = find(path);
			return node == null ? null : new Stat(node.children != null, node.data == null ? 0 : node.data.length());
		}

		@Override
		public void mkdir(String path) {
			Node parent = parent(path);
			if (parent.children.containsKey(leaf(path))) {
				throw new OsError("already exists: " + path);
			}
			parent.children.put(leaf(path), new Node(true));
		}

		@Override
		public void remove(String path) {
			Node parent = parent(path);
			Node node = parent.children.get(leaf(path));
			if (node == null) {
				throw new OsError("no such file: " + path);
			}
			if (node.children != null && !node.children.isEmpty()) {
				throw new OsError("directory not empty: " + path);
			}
			parent.children.remove(leaf(path));
		}

		@Override
		public void rename(String from, String to) {
			Node source = parent(from).children.get(leaf(from));
			if (source == null) {
				throw new OsError("no such file: " + from);
			}
			Node target = parent(to);
			if (target.children.containsKey(leaf(to))) {
				throw new OsError("already exists: " + to);
			}
			parent(from).children.remove(leaf(from));
			target.children.put(leaf(to), source);
		}

		@Override
		public OpenFile open(String path, String mode) {
			Node parent = parent(path);
			Node node = parent.children.get(leaf(path));
			if (node != null && node.children != null) {
				throw new OsError("is a directory: " + path);
			}
			if (node == null) {
				if (mode.startsWith("r")) {
					throw new OsError("no such file: " + path);
				}
				node = new Node(false);
				parent.children.put(leaf(path), node);
			}
			if (mode.startsWith("w")) {
				node.data.setLength(0);
			}
			Node file = node;
			int[] at = {mode.startsWith("a") ? file.data.length() : 0};
			return new OpenFile() {
				@Override
				public String read(int count) {
					if (at[0] >= file.data.length()) {
						return null;
					}
					String chunk = file.data.substring(at[0], Math.min(file.data.length(), at[0] + count));
					at[0] += chunk.length();
					return chunk;
				}

				@Override
				public void write(String data) {
					if (mode.startsWith("a")) {
						at[0] = file.data.length();
					}
					file.data.replace(at[0], Math.min(file.data.length(), at[0] + data.length()), data);
					at[0] += data.length();
				}

				@Override
				public void close() {
				}
			};
		}

		@Override
		public long[] space() {
			long[] used = {0};
			walk(root, used);
			// Like Linux's tmpfs: up to half the memory.
			return new long[] {board.hardware().memoryTotal() / 2, used[0]};
		}

		private void walk(Node node, long[] used) {
			if (node.children == null) {
				used[0] += node.data.length();
			} else {
				node.children.values().forEach(child -> walk(child, used));
			}
		}
	}

	/** A disk in hfs mode: it serves its files; every call goes to it. */
	private final class DiskFs implements Fs {
		private final String address;

		DiskFs(String address) {
			this.address = address;
		}

		@Override
		public String type() {
			return "hfs";
		}

		@Override
		@SuppressWarnings("unchecked")
		public List<String> list(String path) {
			return (List<String>) (List<?>) call(address, "list", path);
		}

		@Override
		@SuppressWarnings("unchecked")
		public Stat stat(String path) {
			Map<String, Object> stat = (Map<String, Object>) call(address, "stat", path);
			return stat == null ? null : new Stat((Boolean) stat.get("directory"), (Long) stat.get("size"));
		}

		@Override
		public void mkdir(String path) {
			call(address, "mkdir", path);
		}

		@Override
		public void remove(String path) {
			call(address, "remove", path);
		}

		@Override
		public void rename(String from, String to) {
			call(address, "rename", from, to);
		}

		@Override
		public OpenFile open(String path, String mode) {
			Handle handle = (Handle) call(address, "open", path, mode);
			return new OpenFile() {
				@Override
				public String read(int count) {
					return (String) call(handle, "read", count);
				}

				@Override
				public void write(String data) {
					call(handle, "write", data);
				}

				@Override
				public void close() {
					call(handle, "close");
				}
			};
		}

		@Override
		public long[] space() {
			return new long[] {(Long) call(address, "getCapacity"), (Long) call(address, "getUsed")};
		}
	}

	/** Calls a device; a failure becomes an {@link OsError}. */
	private Object call(String address, String method, Object... args) {
		Result result = board.invoke(address, method, args);
		if (result.failed()) {
			throw new OsError(result.error());
		}
		return result.first();
	}

	private Object call(Handle handle, String method, Object... args) {
		Result result = board.invoke(handle, method, args);
		if (result.failed()) {
			throw new OsError(result.error());
		}
		return result.first();
	}

	// ---- paths and the mount table -----------------------------------------------------------

	private static List<String> names(String path) {
		List<String> names = new ArrayList<>();
		for (String name : path.split("/")) {
			if (!name.isEmpty() && !name.equals(".")) {
				names.add(name);
			}
		}
		return names;
	}

	/** An absolute, clean path: from the working directory, with . and .. worked out. */
	private String absolute(String path) {
		List<String> names = new ArrayList<>();
		for (String name : ((path.startsWith("/") ? "" : cwd + "/") + path).split("/")) {
			if (name.equals("..")) {
				if (!names.isEmpty()) {
					names.removeLast();
				}
			} else if (!name.isEmpty() && !name.equals(".")) {
				names.add(name);
			}
		}
		return "/" + String.join("/", names);
	}

	/** The mount a path is on (the longest mount point that holds it), and the path on it. */
	private Map.Entry<Mount, String> locate(String path) {
		for (String point : mounts.descendingKeySet()) {
			if (point.equals("/") || path.equals(point) || path.startsWith(point + "/")) {
				String rest = point.equals("/") ? path : path.substring(point.length());
				return Map.entry(mounts.get(point), rest.isEmpty() ? "/" : rest);
			}
		}
		throw new OsError("nothing mounted at /");
	}

	private Fs fs(String path) {
		return locate(path).getKey().fs();
	}

	private String on(String path) {
		return locate(path).getValue();
	}

	private Stat stat(String path) {
		if (mounts.containsKey(path)) {
			return new Stat(true, 0);
		}
		return fs(path).stat(on(path));
	}

	private boolean isDirectory(String path) {
		Stat stat = stat(path);
		return stat != null && stat.directory();
	}

	/** A directory's names, with the mount points right under it. */
	private List<String> list(String path) {
		TreeMap<String, Boolean> names = new TreeMap<>();
		fs(path).list(on(path)).forEach(name -> names.put(name, true));
		for (String point : mounts.keySet()) {
			if (!point.equals("/") && parentOf(point).equals(path)) {
				String name = point.substring(point.lastIndexOf('/') + 1) + "/";
				names.put(name, true);
			}
		}
		return List.copyOf(names.keySet());
	}

	private static String parentOf(String path) {
		int slash = path.lastIndexOf('/');
		return slash <= 0 ? "/" : path.substring(0, slash);
	}

	private void mkdir(String path) {
		if (stat(path) != null) {
			throw new OsError("already exists: " + path);
		}
		fs(path).mkdir(on(path));
	}

	private void mkdirs(String path) {
		if (path.equals("/") || isDirectory(path)) {
			return;
		}
		mkdirs(parentOf(path));
		mkdir(path);
	}

	private void remove(String path, boolean recursive) {
		if (mounts.containsKey(path)) {
			throw new OsError("busy: " + path + " is a mount point");
		}
		Stat stat = stat(path);
		if (stat == null) {
			throw new OsError("no such file: " + path);
		}
		if (stat.directory()) {
			if (!recursive) {
				throw new OsError("is a directory: " + path + " (use -r)");
			}
			for (String name : list(path)) {
				remove(child(path, name), true);
			}
		}
		fs(path).remove(on(path));
	}

	private static String child(String dir, String name) {
		String bare = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
		return dir.equals("/") ? "/" + bare : dir + "/" + bare;
	}

	private OpenFile open(String path, String mode) {
		return fs(path).open(on(path), mode);
	}

	private void copy(String from, String to) {
		OpenFile source = open(from, "r");
		try {
			OpenFile target = open(to, "w");
			try {
				String chunk;
				while ((chunk = source.read(CHUNK)) != null) {
					target.write(chunk);
				}
			} finally {
				target.close();
			}
		} finally {
			source.close();
		}
	}

	/**
	 * Mounts every hfs disk on the bus that isn't mounted, like udisks: {@code sata:N} at
	 * {@code /mnt/sata/N}, others at {@code /mnt/id/<uuid>}; unmounts disks that left. Listing
	 * the bus clears its interrupt.
	 */
	private void automount() {
		List<String> present = new ArrayList<>();
		for (Board.DeviceInfo device : board.list()) {
			present.add(device.address());
			if (!"storage".equals(device.type()) || mounts.values().stream().anyMatch(mount -> mount.source().equals(device.address()))) {
				continue;
			}
			if (!"hfs".equals(board.invoke(device.address(), "getMode").first())) {
				continue; // raw: no file system to mount
			}
			String point = device.address().startsWith("sata:")
				? "/mnt/sata/" + device.address().substring("sata:".length())
				: "/mnt/id/" + device.address().substring("id:".length());
			try {
				mkdirs(point);
				mounts.put(point, new Mount(device.address(), new DiskFs(device.address())));
			} catch (OsError e) {
				print("automount: " + device.address() + ": " + e.getMessage() + "\n");
			}
		}
		mounts.values().removeIf(mount -> !mount.source().equals("tmpfs") && !present.contains(mount.source()));
		if (!isDirectory(cwd)) {
			cwd = "/";
		}
	}

	// ---- the console --------------------------------------------------------------------------

	private void print(String text) {
		board.invoke(CONSOLE, "write", text);
	}

	private String prompt() {
		return cwd + " # ";
	}

	/**
	 * Reads a line from the console, editing it as readline does in raw mode (the console's tty
	 * doesn't edit lines): printable keys go in and are echoed, Backspace rubs out, Up and Down
	 * walk the history, Ctrl+U kills the line, Ctrl+L clears the screen, Ctrl+C gives up on it.
	 * Mounts follow the bus while it waits. Returns null when the line was given up.
	 */
	private String readLine() {
		StringBuilder line = new StringBuilder();
		int back = history.size();
		while (true) {
			List<String> pending = board.waitForInterrupt(WAIT_SECONDS, CONSOLE, Board.BUS);
			if (pending.contains(Board.BUS)) {
				automount();
			}
			Object read;
			while ((read = board.invoke(CONSOLE, "read").first()) != null) {
				String key = read.toString();
				switch (key) {
					case "\r", "\n" -> {
						print("\n");
						return line.toString();
					}
					case "\u007F", "\b" -> {
						if (!line.isEmpty()) {
							line.setLength(line.length() - 1);
							print("\b \b");
						}
					}
					case "\u0003" -> {
						print("^C\n");
						return null;
					}
					case "\u0015" -> {
						line.setLength(0);
						print("\r\u001B[K" + prompt());
					}
					case "\u000C" -> print("\u001B[2J\u001B[H" + prompt() + line);
					case "\u001B[A", "\u001B[B" -> {
						back = Math.clamp(back + (key.equals("\u001B[A") ? -1 : 1), 0, history.size());
						line.setLength(0);
						line.append(back < history.size() ? history.get(back) : "");
						print("\r\u001B[K" + prompt() + line);
					}
					default -> {
						if (key.startsWith("\u001B")) {
							break; // other keys (arrows, Home, ...) aren't edited yet
						}
						StringBuilder typed = new StringBuilder();
						key.chars().filter(c -> c >= 0x20 && c != 0x7F).forEach(typed::appendCodePoint);
						line.append(typed);
						print(typed.toString());
					}
				}
			}
		}
	}

	// ---- the shell ----------------------------------------------------------------------------

	@Override
	public void run(Board board) {
		this.board = board;
		TmpFs root = new TmpFs();
		mounts.put("/", new Mount("tmpfs", root));
		root.mkdir("/mnt");
		root.mkdir("/tmp");
		Result size = board.invoke(CONSOLE, "getSize");
		if (!size.failed() && size.first() instanceof Long width) {
			columns = width.intValue();
		}
		automount();
		print("Modular Computers OS\nType help for commands.\n");
		while (true) {
			print(prompt());
			String line = readLine();
			if (line == null || line.isBlank()) {
				continue;
			}
			if (history.isEmpty() || !history.getLast().equals(line)) {
				history.add(line);
			}
			for (List<String> words : parse(line)) {
				execute(words);
			}
		}
	}

	/**
	 * Splits a line into commands (at {@code ;}) of words: spaces split words, quotes keep them
	 * together, a backslash takes the next character as it is, and {@code >} and {@code >>} are
	 * words of their own.
	 */
	private static List<List<String>> parse(String line) {
		List<List<String>> commands = new ArrayList<>();
		List<String> words = new ArrayList<>();
		StringBuilder word = null;
		char quote = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				} else if (c == '\\' && quote == '"' && i + 1 < line.length()) {
					word.append(line.charAt(++i));
				} else {
					word.append(c);
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
				word = word == null ? new StringBuilder() : word;
			} else if (c == '\\' && i + 1 < line.length()) {
				word = word == null ? new StringBuilder() : word;
				word.append(line.charAt(++i));
			} else if (c == ' ' || c == '\t' || c == ';' || c == '>') {
				if (word != null) {
					words.add(word.toString());
					word = null;
				}
				if (c == '>') {
					boolean twice = i + 1 < line.length() && line.charAt(i + 1) == '>';
					words.add(twice ? ">>" : ">");
					i += twice ? 1 : 0;
				} else if (c == ';' && !words.isEmpty()) {
					commands.add(words);
					words = new ArrayList<>();
				}
			} else {
				word = word == null ? new StringBuilder() : word;
				word.append(c);
			}
		}
		if (word != null) {
			words.add(word.toString());
		}
		if (!words.isEmpty()) {
			commands.add(words);
		}
		return commands;
	}

	/** Runs one command, its output to the console or, redirected, to a file. */
	private void execute(List<String> words) {
		List<String> args = new ArrayList<>();
		String target = null;
		boolean append = false;
		for (int i = 0; i < words.size(); i++) {
			String word = words.get(i);
			if (word.equals(">") || word.equals(">>")) {
				if (i + 1 >= words.size()) {
					print("sh: nothing after " + word + "\n");
					return;
				}
				target = words.get(++i);
				append = word.equals(">>");
			} else {
				args.add(word);
			}
		}
		if (args.isEmpty()) {
			return;
		}
		String name = args.getFirst();
		StringBuilder out = new StringBuilder();
		OpenFile file = null;
		try {
			if (target != null) {
				file = open(absolute(target), append ? "a" : "w");
			}
			OpenFile sink = file;
			// Output is gathered and written in pieces: fewer calls than a write a line.
			Consumer<String> write = text -> {
				out.append(text);
				if (out.length() >= CHUNK) {
					flush(out, sink);
				}
			};
			command(name, args.subList(1, args.size()), write);
			flush(out, sink);
		} catch (OsError e) {
			flush(out, file);
			print(name + ": " + e.getMessage() + "\n");
		} catch (RuntimeException e) {
			flush(out, file);
			print(name + ": crashed: " + e + "\n");
		} finally {
			if (file != null) {
				try {
					file.close();
				} catch (OsError ignored) {
					// it's gone (the disk left)
				}
			}
		}
	}

	private void flush(StringBuilder out, OpenFile file) {
		if (out.isEmpty()) {
			return;
		}
		if (file != null) {
			file.write(out.toString());
		} else {
			print(out.toString());
		}
		out.setLength(0);
	}

	/** Takes the leading {@code -x} flags off {@code args}; returns them as one string of letters. */
	private static String flags(List<String> args) {
		StringBuilder flags = new StringBuilder();
		while (!args.isEmpty() && args.getFirst().startsWith("-") && args.getFirst().length() > 1) {
			flags.append(args.removeFirst().substring(1));
		}
		return flags.toString();
	}

	private static void need(List<String> args, int count, String usage) {
		if (args.size() < count) {
			throw new OsError("usage: " + usage);
		}
	}

	private void command(String name, List<String> given, Consumer<String> out) {
		List<String> args = new ArrayList<>(given);
		switch (name) {
			case "help" -> {
				for (String[] line : HELP) {
					out.accept(String.format("%-25s %s\n", line[0], line[1]));
				}
			}
			case "echo" -> {
				boolean newline = true;
				if (!args.isEmpty() && args.getFirst().equals("-n")) {
					args.removeFirst();
					newline = false;
				}
				out.accept(String.join(" ", args) + (newline ? "\n" : ""));
			}
			case "pwd" -> out.accept(cwd + "\n");
			case "cd" -> {
				String dir = absolute(args.isEmpty() ? "/" : args.getFirst());
				if (!isDirectory(dir)) {
					throw new OsError("not a directory: " + dir);
				}
				cwd = dir;
			}
			case "ls" -> ls(flags(args).contains("l"), args, out);
			case "cat" -> {
				need(args, 1, "cat file...");
				for (String path : args) {
					OpenFile file = open(absolute(path), "r");
					try {
						String chunk;
						while ((chunk = file.read(CHUNK)) != null) {
							out.accept(chunk);
						}
					} finally {
						file.close();
					}
				}
			}
			case "touch" -> {
				need(args, 1, "touch file...");
				for (String path : args) {
					open(absolute(path), "a").close();
				}
			}
			case "mkdir" -> {
				boolean parents = flags(args).contains("p");
				need(args, 1, "mkdir [-p] dir...");
				for (String path : args) {
					if (parents) {
						mkdirs(absolute(path));
					} else {
						mkdir(absolute(path));
					}
				}
			}
			case "rm" -> {
				boolean recursive = flags(args).contains("r");
				need(args, 1, "rm [-r] path...");
				for (String path : args) {
					remove(absolute(path), recursive);
				}
			}
			case "cp" -> {
				need(args, 2, "cp src dst");
				copy(absolute(args.get(0)), target(absolute(args.get(0)), absolute(args.get(1))));
			}
			case "mv" -> {
				need(args, 2, "mv src dst");
				String from = absolute(args.get(0));
				String to = target(from, absolute(args.get(1)));
				if (mounts.containsKey(from)) {
					throw new OsError("busy: " + from + " is a mount point");
				}
				if (locate(from).getKey() == locate(to).getKey()) {
					fs(from).rename(on(from), on(to));
				} else if (isDirectory(from)) {
					throw new OsError("can't move a directory to another disk");
				} else {
					copy(from, to); // another file system: copy, then remove
					remove(from, false);
				}
			}
			case "mount" -> mount(args, out);
			case "umount" -> {
				need(args, 1, "umount dir|device");
				String which = args.getFirst();
				String point = mounts.entrySet().stream()
					.filter(entry -> entry.getValue().source().equals(which) || entry.getKey().equals(absolute(which)))
					.map(Map.Entry::getKey).findFirst().orElseThrow(() -> new OsError("not mounted: " + which));
				if (point.equals("/")) {
					throw new OsError("can't unmount /");
				}
				mounts.remove(point);
				if (!isDirectory(cwd)) {
					cwd = "/";
				}
			}
			case "df" -> {
				out.accept(String.format("%-13s %5s %5s %5s %4s %s\n", "Filesystem", "Size", "Used", "Avail", "Use%", "Mounted on"));
				for (Map.Entry<String, Mount> mount : mounts.entrySet()) {
					long[] space = mount.getValue().fs().space();
					long percent = space[0] == 0 ? 0 : (space[1] * 100 + space[0] - 1) / space[0];
					out.accept(String.format("%-13s %5s %5s %5s %3d%% %s\n", shorten(mount.getValue().source()), bytes(space[0]), bytes(space[1]),
						bytes(Math.max(0, space[0] - space[1])), percent, mount.getKey()));
				}
			}
			case "mkfs" -> {
				String type = "hfs";
				if (!args.isEmpty() && args.getFirst().equals("-t")) {
					args.removeFirst();
					need(args, 2, "mkfs [-t hfs|raw] device");
					type = args.removeFirst();
				}
				need(args, 1, "mkfs [-t hfs|raw] device");
				String device = args.getFirst();
				if (mounts.values().stream().anyMatch(mount -> mount.source().equals(device))) {
					throw new OsError(device + " is mounted: umount it first");
				}
				call(device, "setMode", type);
				out.accept(device + ": formatted " + type + "\n");
			}
			case "lsdev" -> {
				for (Board.DeviceInfo device : board.list()) {
					Map<String, Object> info = board.info(device.address()).orElse(Map.of());
					StringBuilder about = new StringBuilder();
					if (info.get("label") != null) {
						about.append('"').append(info.get("label")).append("\" ");
					}
					if (info.get("tier") != null) {
						about.append("tier ").append(info.get("tier")).append(' ');
					}
					if (info.get("capacity") instanceof Long capacity) {
						about.append(bytes(capacity)).append(' ');
					}
					if (info.get("kind") != null) {
						about.append(info.get("kind")).append(' ');
					}
					if (info.get("mode") != null) {
						about.append(info.get("mode")).append(' ');
					}
					out.accept(String.format("%-10s %-9s %s\n", shorten(device.address()), device.type(), about.toString().strip()));
				}
			}
			case "free" -> {
				Board.HardwareInfo hardware = board.hardware();
				out.accept(String.format("%8s %8s %8s\n", "total", "used", "free"));
				out.accept(String.format("%8s %8s %8s\n", bytes(hardware.memoryTotal()), bytes(hardware.memoryUsed()),
					bytes(hardware.memoryTotal() - hardware.memoryUsed())));
			}
			case "acpi" -> {
				Board.PowerInfo power = board.power();
				out.accept("Case: " + store(power.store()) + ", load " + power.load() + "\n");
				for (Board.BatteryInfo battery : power.batteries()) {
					out.accept("Battery " + battery.bay() + ": tier " + battery.tier() + ", " + store(battery.charge()) + "\n");
				}
				if (power.batteries().isEmpty()) {
					out.accept("No batteries\n");
				}
			}
			case "uptime" -> {
				long seconds = board.uptime() / 20;
				out.accept(String.format("up %02d:%02d:%02d\n", seconds / 3600, seconds / 60 % 60, seconds % 60));
			}
			case "clear" -> out.accept("\u001B[2J\u001B[H");
			case "halt" -> {
				print("halted\n");
				board.halt();
			}
			default -> throw new OsError("command not found (try help)");
		}
	}

	/** Where cp or mv puts a file: into a directory by its own name, or at the path given. */
	private String target(String from, String to) {
		return isDirectory(to) ? child(to, from.substring(from.lastIndexOf('/') + 1)) : to;
	}

	private void ls(boolean long_, List<String> args, Consumer<String> out) {
		List<String> paths = args.isEmpty() ? List.of(".") : args;
		for (String path : paths) {
			String at = absolute(path);
			Stat stat = stat(at);
			if (stat == null) {
				throw new OsError("no such file: " + at);
			}
			if (paths.size() > 1 && stat.directory()) {
				out.accept(at + ":\n");
			}
			List<String> names = stat.directory() ? list(at) : List.of(at.substring(at.lastIndexOf('/') + 1));
			if (long_) {
				for (String name : names) {
					Stat entry = stat.directory() ? stat(child(at, name)) : stat;
					out.accept(String.format("%s %8d %s\n", entry != null && entry.directory() ? "d" : "-", entry == null ? 0 : entry.size(), name));
				}
				continue;
			}
			// Across the screen, wrapping at its width.
			StringBuilder row = new StringBuilder();
			for (String name : names) {
				if (!row.isEmpty() && row.length() + 2 + name.length() > columns) {
					out.accept(row + "\n");
					row.setLength(0);
				}
				row.append(row.isEmpty() ? "" : "  ").append(name);
			}
			if (!row.isEmpty()) {
				out.accept(row + "\n");
			}
		}
	}

	private void mount(List<String> args, Consumer<String> out) {
		if (args.isEmpty()) {
			for (Map.Entry<String, Mount> mount : mounts.entrySet()) {
				out.accept(shorten(mount.getValue().source()) + " on " + mount.getKey() + " type " + mount.getValue().fs().type() + "\n");
			}
			return;
		}
		need(args, 2, "mount [device dir]");
		String device = args.get(0);
		String point = absolute(args.get(1));
		if (board.type(device).map(type -> !type.equals("storage")).orElse(true)) {
			throw new OsError("not a disk: " + device);
		}
		String address = board.info(device).map(info -> (String) info.get("address")).orElse(device);
		if (!"hfs".equals(call(address, "getMode"))) {
			throw new OsError(device + " is raw: no file system (mkfs -t hfs " + device + ")");
		}
		if (mounts.containsKey(point)) {
			throw new OsError("already mounted on " + point);
		}
		if (!isDirectory(point)) {
			throw new OsError("not a directory: " + point);
		}
		mounts.put(point, new Mount(address, new DiskFs(address)));
	}

	/** {@code id:} addresses are long: the first 8 of the uuid, like a short git hash. */
	private static String shorten(String address) {
		return address.startsWith("id:") && address.length() > 11 ? address.substring(0, 11) + ".." : address;
	}

	private static String bytes(long bytes) {
		if (bytes < 1024) {
			return bytes + "B";
		}
		String[] units = {"K", "M", "G", "T"};
		double value = bytes;
		int unit = -1;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}
		return (value < 10 ? String.format("%.1f", value) : String.format("%.0f", value)) + units[unit];
	}

	private static String store(Board.Store store) {
		if (store.infinite()) {
			return "100%, inf";
		}
		return store.percent() + "%, " + store.stored() + " of " + store.capacity();
	}
}
