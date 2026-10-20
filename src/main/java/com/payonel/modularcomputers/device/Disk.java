package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Handle;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A storage device of a tier: its size and its power draw. Bus-powered. Its data lives on the
 * host, in the world save by the disk's id ({@code storage}), in one of two modes, set like
 * formatting (user design, 2026-10-12):
 * <ul>
 * <li><b>hfs</b> (host filesystem), how a disk comes: the disk serves files, and they're real
 * files in a folder on the host, easy to look at. Programs {@code open} a file and get a
 * {@link FileHandle}, then read and write through it, like Plan 9's 9P, where a device serves
 * files itself. A handle never keeps a host file open (user design, 2026-10-13): it reads through
 * a window of the file, buffers its writes, and commits them atomically at {@code close}.</li>
 * <li><b>raw</b>, an opaque block device: sectors of {@link #SECTOR} bytes, kept on the host as one
 * image file.</li>
 * </ul>
 * Paths are the disk's own, from its root ({@code /notes/a.txt}): {@code /} is the disk's folder in
 * the world save ({@code modularcomputers/disks/<id>}), and nothing a program names can reach
 * outside it ({@link #resolve}). Data
 * is bytes: strings carry one character per byte (ISO-8859-1), as Lua's do. Space counts each
 * file's bytes plus {@link #ENTRY} for each file and directory, so empty files aren't free.
 * <p>
 * <b>Symbolic links</b> (user, 2026-10-19: the filesystem on the device keeps them, as a real one
 * does) are kept as CIFS's Minshall+French symlinks are on a server that has none: an ordinary
 * file of exactly {@link #LINK_SIZE} bytes, {@code XSym}, the target's length and MD5, then the
 * target ({@link #symlink}). The host never gets a real link (one could point out of the folder). The
 * disk never follows a link itself: a path that meets one (on its way, or at its end, for a call
 * that follows it) fails with {@link #REPARSE}, the link's path and its target, and the OS's VFS
 * goes on from there (as a Windows filesystem answers STATUS_REPARSE): a target can be anywhere in
 * the OS's tree, on another disk too.
 */
public final class Disk implements Device {
	public static final String HFS = "hfs";
	public static final String RAW = "raw";
	public static final int SECTOR = 512;
	/** What each file or directory costs on top of its bytes, like an inode. */
	public static final int ENTRY = 512;
	/** Open files at once, all programs together. */
	public static final int MAX_HANDLES = 16;
	/** The most a read gives at once. */
	public static final int MAX_READ = 4096;
	/** How much of a file a handle holds to read from: what it costs the program's memory, whatever the file's size. */
	public static final int WINDOW = 4096;
	/** What a full disk says, as a real file descriptor's write does (ENOSPC). */
	public static final String NO_SPACE = "No space left on device";
	/** A write the program's memory has no room for (ENOMEM): a handle's buffered writes are charged to it. */
	public static final String NO_MEMORY = "Cannot allocate memory";
	/** A symbolic link's file: its size, exactly (as CIFS's mfsymlinks). */
	public static final int LINK_SIZE = 1067;
	/** A link's file starts with this. */
	private static final String LINK_MAGIC = "XSym\n";
	/** What a link's header takes before its target: the magic, the length (4 digits and a new line), the MD5 (32 hex digits and a new line). */
	private static final int LINK_HEADER = LINK_MAGIC.length() + 5 + 33;
	/** The longest target a link holds (the rest of its file, less the new line after it). */
	public static final int LINK_MAX = LINK_SIZE - LINK_HEADER - 1;
	/** How a call that met a link fails: this, the link's path on the disk, a NUL and its target ({@code "symbolic link\0/a/b\0../c"}). */
	public static final String REPARSE = "symbolic link\0";
	/** Live disks by id (their folder's name): a handle finds its disk by it, also after a restore. */
	private static final Map<String, Disk> LIVE = new java.util.concurrent.ConcurrentHashMap<>();

	private final DiskTier tier;
	private final BusPower power;
	/** Where its data lives: a folder (hfs), or this plus ".img" (raw). Null on a client's copy: no data there. */
	private final Path storage;
	private final List<FileHandle> handles = new ArrayList<>();
	/** Space open handles' buffered writes will take when they close (reserved at write, as delayed allocation does). */
	private long reserved;
	/** Where its label is (its item's anvil name), read when asked: no copy of it here. */
	private java.util.function.Supplier<String> label = () -> null;
	/** Bytes in use, worked out on first need, then kept up to date. */
	private long used = -1;

	public Disk(DiskTier tier) {
		this(tier, null);
	}

	public Disk(DiskTier tier, Path storage) {
		this.tier = tier;
		this.power = new BusPower(tier.draw());
		this.storage = storage == null ? null : storage.toAbsolutePath().normalize(); // (the inside-the-folder checks compare normalized paths)
		if (storage != null) {
			LIVE.put(id(), this);
		}
	}

	/** It's out of its computer (taken out, or the computer unloaded): handles can't find it now, and it may be wiped. */
	public void retire() {
		if (storage != null) {
			LIVE.remove(id(), this);
		}
	}

	/** Its id: its folder's name in the world save. */
	public String id() {
		return storage.getFileName().toString();
	}

	public DiskTier tier() {
		return tier;
	}

	public void labelFrom(java.util.function.Supplier<String> label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label.get();
	}

	private Path image() {
		return storage.resolveSibling(storage.getFileName() + ".img");
	}

	/** hfs, unless it was formatted raw. */
	public String mode() {
		return storage != null && Files.isRegularFile(image()) ? RAW : HFS;
	}

	@Override
	public Map<String, Object> info() {
		return Map.of("tier", (long) tier.number(), "capacity", (long) tier.bytes(), "mode", mode());
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public DeviceType type() {
		return DeviceType.STORAGE;
	}

	/** Power's gone, or it left the bus: open files close, and what they hadn't committed is lost (as at a power cut). */
	@Override
	public void powerOff() {
		for (FileHandle handle : List.copyOf(handles)) {
			handle.drop();
		}
	}

	/** Its board stopped taking data (no program runs): the program's open files close, uncommitted. */
	@Override
	public void accepting(boolean accepting) {
		if (!accepting) {
			powerOff();
		}
	}

	// ---- the host side -----------------------------------------------------------------------

	/** Thrown for a soft failure, with the message the program gets. */
	private static final class Refused extends RuntimeException {
		Refused(String message) {
			super(message, null, false, false);
		}
	}

	private Path root() {
		if (storage == null) {
			throw new Refused("no storage");
		}
		if (!mode().equals(HFS)) {
			throw new Refused("raw disk: no filesystem");
		}
		try {
			Files.createDirectories(storage); // a new disk's root, on first use
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return storage;
	}

	/** Names a host could take as something else: Windows' devices. */
	private static final java.util.Set<String> RESERVED = java.util.Set.of("con", "prn", "aux", "nul",
		"com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
		"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

	/**
	 * A path on the disk, as a host path inside its folder. Every path is the disk's own: relative
	 * or absolute, it starts at the disk's root, {@code ..} at the root stays there, and a name a
	 * host could read as more than a name (a separator, a drive, a device, a NUL) is refused. The
	 * result is checked to be inside the folder, through any link on the host too.
	 */
	Path resolve(String path) {
		return resolve(path, true);
	}

	/**
	 * As {@link #resolve(String)}; a link met on the way, or at the end when {@code follow}, fails the
	 * call with {@link #REPARSE} (the OS follows it).
	 */
	Path resolve(String path, boolean follow) {
		Path root = root();
		Path at = root;
		String[] names = path.split("/");
		int last = names.length - 1;
		while (last >= 0 && (names[last].isEmpty() || names[last].equals("."))) {
			last--;
		}
		StringBuilder walked = new StringBuilder();
		for (int i = 0; i < names.length; i++) {
			String name = names[i];
			if (name.isEmpty() || name.equals(".")) {
				continue;
			}
			if (name.equals("..")) {
				at = at.equals(root) ? root : at.getParent();
				continue;
			}
			String bare = name.toLowerCase(Locale.ROOT);
			int dot = bare.indexOf('.');
			if (name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0 || name.indexOf(':') >= 0 || name.endsWith(" ") || name.endsWith(".")
				|| RESERVED.contains(dot < 0 ? bare : bare.substring(0, dot))) {
				throw new Refused("bad name: " + name);
			}
			at = at.resolve(name);
			walked.append('/').append(name);
			if (i < last || follow) {
				String target = linkTarget(at);
				if (target != null) {
					throw new Reparse(walked.toString(), target);
				}
			}
		}
		if (!at.normalize().startsWith(root)) {
			throw new Refused("outside the disk: " + path);
		}
		try {
			// Through links: the nearest part that exists must really be inside the folder.
			Path real = root.toRealPath();
			Path nearest = at;
			while (!Files.exists(nearest, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !nearest.equals(root)) {
				nearest = nearest.getParent();
			}
			if (!nearest.toRealPath().startsWith(real)) {
				throw new Refused("outside the disk: " + path);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return at;
	}

	/** A link's target, if {@code at} is a link's file (see the class's doc); null for anything else. */
	static String linkTarget(Path at) {
		try {
			if (!Files.isRegularFile(at, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.size(at) != LINK_SIZE) {
				return null;
			}
			String text = new String(Files.readAllBytes(at), StandardCharsets.ISO_8859_1);
			if (!text.startsWith(LINK_MAGIC) || text.charAt(LINK_MAGIC.length() + 4) != '\n') {
				return null;
			}
			int length = Integer.parseInt(text.substring(LINK_MAGIC.length(), LINK_MAGIC.length() + 4));
			if (length < 1 || length > LINK_MAX) {
				return null;
			}
			String target = text.substring(LINK_HEADER, LINK_HEADER + length);
			return text.substring(LINK_MAGIC.length() + 5, LINK_HEADER - 1).equals(md5(target)) ? target : null;
		} catch (IOException | NumberFormatException e) {
			return null;
		}
	}

	/** A link's file for a target (see the class's doc). */
	static byte[] linkFile(String target) {
		StringBuilder text = new StringBuilder(LINK_SIZE);
		text.append(LINK_MAGIC).append(String.format(Locale.ROOT, "%04d\n", target.length())).append(md5(target)).append('\n');
		text.append(target).append('\n');
		while (text.length() < LINK_SIZE) {
			text.append(' ');
		}
		return text.toString().getBytes(StandardCharsets.ISO_8859_1);
	}

	private static String md5(String text) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.ISO_8859_1));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A call met a link: the OS goes on from it. */
	private static final class Reparse extends RuntimeException {
		final String at;
		final String target;

		Reparse(String at, String target) {
			super(at, null, false, false);
			this.at = at;
			this.target = target;
		}
	}

	/** Where a handle builds what it commits: beside the disk's folder, never in it (not visible, not counted). */
	private Path scratch() throws IOException {
		Path folder = storage.resolveSibling(storage.getFileName() + ".tmp");
		Files.createDirectories(folder);
		return folder;
	}

	private long used() {
		if (used < 0) {
			used = 0;
			if (storage != null && Files.isDirectory(storage)) {
				try (Stream<Path> paths = Files.walk(storage)) {
					paths.filter(path -> !path.equals(storage)).forEach(path -> used += ENTRY + size(path));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		}
		return used;
	}

	private static long size(Path path) {
		try {
			return Files.isRegularFile(path) ? Files.size(path) : 0;
		} catch (IOException e) {
			return 0;
		}
	}

	/** Takes {@code bytes} more space (fewer, if negative), or refuses if there isn't room. */
	void grow(long bytes) {
		if (bytes > 0 && used() + reserved + bytes > tier.bytes()) {
			throw new Refused(NO_SPACE);
		}
		used = used() + bytes;
	}

	/** Reserves {@code bytes} more for a handle's buffered writes (fewer, if negative), or refuses if there isn't room. */
	void reserve(long bytes) {
		if (bytes > 0 && used() + reserved + bytes > tier.bytes()) {
			throw new Refused(NO_SPACE);
		}
		reserved += bytes;
	}

	private Result run(java.util.function.Supplier<Result> action) {
		try {
			return action.get();
		} catch (Refused e) {
			return Result.fail(e.getMessage());
		} catch (Reparse e) {
			return Result.fail(REPARSE + e.at + "\0" + e.target);
		} catch (NoSuchFileRuntime e) {
			return Result.fail("no such file: " + e.getMessage());
		} catch (UncheckedIOException e) {
			return Result.fail(e.getCause() instanceof NoSuchFileException missing ? "no such file" : "i/o error: " + e.getCause().getMessage());
		}
	}

	private static final class NoSuchFileRuntime extends RuntimeException {
		NoSuchFileRuntime(String path) {
			super(path, null, false, false);
		}
	}

	// ---- device methods: both modes ----------------------------------------------------------

	@Method(doc = "getCapacity(): how many bytes it holds")
	public Result getCapacity(Arguments args) {
		return Result.ok(tier.bytes());
	}

	@Method(doc = "getUsed(): how many bytes are in use (hfs: files and their entries, and what open files have written; raw: all of it)")
	public Result getUsed(Arguments args) {
		return run(() -> Result.ok(mode().equals(RAW) ? tier.bytes() : used() + reserved)); // what open files will take counts, as df counts delayed allocation
	}

	@Method(doc = "getMode(): \"hfs\" (it serves files) or \"raw\" (sectors)")
	public Result getMode(Arguments args) {
		return Result.ok(mode());
	}

	@Method(doc = "setMode(mode): formats it as \"hfs\" or \"raw\"; everything on it is lost and open files close")
	public Result setMode(Arguments args) {
		String mode = args.checkString(0);
		if (!mode.equals(HFS) && !mode.equals(RAW)) {
			return Result.fail("unknown mode: " + mode);
		}
		return run(() -> {
			if (storage == null) {
				throw new Refused("no storage");
			}
			powerOff();
			try {
				wipe(storage);
				Files.deleteIfExists(image());
				if (mode.equals(RAW)) {
					Files.createDirectories(storage.getParent());
					try (RandomAccessFile file = new RandomAccessFile(image().toFile(), "rw")) {
						file.setLength(tier.bytes()); // sparse on the host until written
					}
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			used = -1;
			return Result.ok();
		});
	}

	/** A disk's data, gone from the host (its folder, its image, its scratch): it was wiped. Not while it's in a computer. */
	public static void erase(Path storage) {
		Path folder = storage.toAbsolutePath().normalize();
		if (LIVE.containsKey(folder.getFileName().toString())) {
			return; // in use: a copy of this disk is in a computer (a creative copy, say)
		}
		try {
			wipe(folder);
			wipe(folder.resolveSibling(folder.getFileName() + ".tmp"));
			Files.deleteIfExists(folder.resolveSibling(folder.getFileName() + ".img"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void wipe(Path folder) throws IOException {
		if (!Files.exists(folder)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(folder)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}

	// ---- device methods: hfs -----------------------------------------------------------------

	@Method(doc = "open(path[, mode]): opens a file and returns its handle; mode is r (the default), r+, w, w+, a or a+, as C's fopen; writes take effect at close")
	public Result open(Arguments args) {
		String path = args.checkString(0);
		String mode = args.optString(1, "r").replace("b", "");
		return run(() -> {
			if (!List.of("r", "r+", "w", "w+", "a", "a+").contains(mode)) {
				throw new Refused("bad mode: " + mode);
			}
			Path file = resolve(path);
			if (Files.isDirectory(file)) {
				throw new Refused("is a directory: " + path);
			}
			if (handles.size() >= MAX_HANDLES) {
				throw new Refused("too many open files");
			}
			boolean exists = Files.isRegularFile(file);
			if (!exists && (mode.startsWith("r") || !Files.isDirectory(file.getParent()))) {
				throw new NoSuchFileRuntime(path);
			}
			try {
				if (!exists) {
					grow(ENTRY); // a new file is there at once, empty, as fopen makes it
					Files.createFile(file);
				}
				String relative = storage.relativize(file).toString().replace('\\', '/');
				FileHandle handle = new FileHandle(id(), relative, mode, Files.size(file));
				handles.add(handle);
				return Result.ok(handle);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	@Method(doc = "list(path[, typed]): the names in a directory, sorted; directories end in /; typed: {name, type} each, type file, directory or link")
	public Result list(Arguments args) {
		String path = args.optString(0, "/");
		boolean typed = args.optBoolean(1, false);
		return run(() -> {
			Path dir = resolve(path);
			if (!Files.isDirectory(dir)) {
				throw new NoSuchFileRuntime(path);
			}
			try (Stream<Path> paths = Files.list(dir)) {
				if (typed) {
					return Result.ok(paths.sorted().map(entry -> (Object) Map.of("name", entry.getFileName().toString(),
						"type", Files.isDirectory(entry) ? "directory" : linkTarget(entry) != null ? "link" : "file")).toList());
				}
				return Result.ok(paths.map(entry -> entry.getFileName() + (Files.isDirectory(entry) ? "/" : "")).sorted().toList());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	@Method(doc = "stat(path[, nofollow]): {size, directory, modified} (modified in milliseconds since 1970), or nil if there's nothing there; nofollow: a link itself, with link = its target (and size its length)")
	public Result stat(Arguments args) {
		String path = args.checkString(0);
		boolean nofollow = args.optBoolean(1, false);
		return run(() -> {
			Path at = resolve(path, !nofollow);
			if (at.equals(storage)) {
				return Result.ok(Map.of("size", 0L, "directory", true, "modified", 0L));
			}
			if (!Files.exists(at)) {
				return Result.ok();
			}
			try {
				Map<String, Object> stat = new LinkedHashMap<>();
				String target = nofollow ? linkTarget(at) : null;
				if (target != null) {
					stat.put("size", (long) target.length());
					stat.put("directory", false);
					stat.put("link", target);
					stat.put("modified", Files.getLastModifiedTime(at).toMillis());
					return Result.ok(stat);
				}
				stat.put("size", size(at));
				stat.put("directory", Files.isDirectory(at));
				stat.put("modified", Files.getLastModifiedTime(at).toMillis());
				return Result.ok(stat);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	@Method(doc = "mkdir(path): makes a directory; its parent must exist")
	public Result mkdir(Arguments args) {
		String path = args.checkString(0);
		return run(() -> {
			Path dir = resolve(path, false);
			if (Files.exists(dir)) {
				throw new Refused("already exists: " + path);
			}
			try {
				if (!Files.isDirectory(dir.getParent())) {
					throw new NoSuchFileRuntime(path);
				}
				grow(ENTRY);
				Files.createDirectory(dir);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return Result.ok();
		});
	}

	@Method(doc = "remove(path): removes a file, a link (not what it points to), or an empty directory")
	public Result remove(Arguments args) {
		String path = args.checkString(0);
		return run(() -> {
			Path at = resolve(path, false);
			if (at.equals(storage)) {
				throw new Refused("can't remove the root");
			}
			if (!Files.exists(at)) {
				throw new NoSuchFileRuntime(path);
			}
			try {
				long freed = ENTRY + size(at);
				Files.delete(at);
				grow(-freed);
			} catch (DirectoryNotEmptyException e) {
				throw new Refused("directory not empty: " + path);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return Result.ok();
		});
	}

	@Method(doc = "rename(from, to): moves a file or directory; to must not exist")
	public Result rename(Arguments args) {
		String from = args.checkString(0);
		String to = args.checkString(1);
		return run(() -> {
			Path source = resolve(from, false);
			Path target = resolve(to, false);
			if (!Files.exists(source) || source.equals(storage)) {
				throw new NoSuchFileRuntime(from);
			}
			if (Files.exists(target)) {
				throw new Refused("already exists: " + to);
			}
			if (target.startsWith(source)) {
				throw new Refused("can't move a directory into itself");
			}
			try {
				Files.move(source, target);
			} catch (NoSuchFileException e) {
				throw new NoSuchFileRuntime(to);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return Result.ok();
		});
	}

	@Method(doc = "symlink(target, path): makes a symbolic link at path to target (any text: the OS follows it); path must not exist")
	public Result symlink(Arguments args) {
		String target = args.checkString(0);
		String path = args.checkString(1);
		return run(() -> {
			if (target.isEmpty() || target.indexOf('\0') >= 0) {
				throw new Refused("bad target");
			}
			if (target.length() > LINK_MAX) {
				throw new Refused("name too long: " + target.length() + " bytes, at most " + LINK_MAX);
			}
			Path at = resolve(path, false);
			if (Files.exists(at)) {
				throw new Refused("already exists: " + path);
			}
			if (!Files.isDirectory(at.getParent())) {
				throw new NoSuchFileRuntime(path);
			}
			try {
				grow(ENTRY + LINK_SIZE);
				Files.write(at, linkFile(target));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return Result.ok();
		});
	}

	@Method(doc = "readlink(path): a symbolic link's target")
	public Result readlink(Arguments args) {
		String path = args.checkString(0);
		return run(() -> {
			Path at = resolve(path, false);
			if (!Files.exists(at)) {
				throw new NoSuchFileRuntime(path);
			}
			String target = linkTarget(at);
			if (target == null) {
				throw new Refused("not a link: " + path);
			}
			return Result.ok(target);
		});
	}

	// ---- device methods: raw -----------------------------------------------------------------

	private RandomAccessFile rawImage(String access) throws IOException {
		if (storage == null) {
			throw new Refused("no storage");
		}
		if (!mode().equals(RAW)) {
			throw new Refused("hfs disk: format it raw for sectors");
		}
		return new RandomAccessFile(image().toFile(), access);
	}

	private int sector(Arguments args) {
		long sector = args.checkInteger(0);
		if (sector < 0 || sector >= tier.bytes() / SECTOR) {
			throw new Refused("no such sector: " + sector);
		}
		return (int) sector;
	}

	@Method(doc = "getSectorSize(): bytes in a sector (raw)")
	public Result getSectorSize(Arguments args) {
		return Result.ok(SECTOR);
	}

	@Method(doc = "readSector(n): sector n's bytes, from 0 (raw)")
	public Result readSector(Arguments args) {
		return run(() -> {
			int sector = sector(args);
			try (RandomAccessFile image = rawImage("r")) {
				byte[] bytes = new byte[SECTOR];
				image.seek((long) sector * SECTOR);
				image.readFully(bytes);
				return Result.ok(new String(bytes, StandardCharsets.ISO_8859_1));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	@Method(doc = "writeSector(n, data): writes sector n, padded with zeros to its size (raw)")
	public Result writeSector(Arguments args) {
		return run(() -> {
			int sector = sector(args);
			byte[] data = args.checkString(1).getBytes(StandardCharsets.ISO_8859_1);
			if (data.length > SECTOR) {
				throw new Refused("more than a sector: " + data.length + " bytes");
			}
			try (RandomAccessFile image = rawImage("rw")) {
				image.seek((long) sector * SECTOR);
				image.write(java.util.Arrays.copyOf(data, SECTOR));
				return Result.ok();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	// ---- open files --------------------------------------------------------------------------

	/**
	 * An open file on an hfs disk (user design, 2026-10-13). It never keeps a host file open while
	 * the program runs: it holds the file's path on the disk, where it's at, a window of the file to
	 * read from ({@link #WINDOW} bytes, refilled as reads move on, so a file's size never costs the
	 * program's memory), and the writes it hasn't committed, as extents over the file. Its own reads
	 * see its writes. Writes reserve the space they'll take, or fail as a full disk does
	 * ({@link #NO_SPACE}). {@code close} commits them atomically: the new file is built beside the
	 * disk's folder and renamed over the old one, so the file is all old or all new; only when that
	 * worked does the handle drop its buffer (a failure leaves it, to retry). Power lost before
	 * {@code close} loses them, as in real life. It's a Lua userdata that Styx keeps when the computer
	 * is saved ({@link #persisted}, {@link #restore}).
	 */
	public static final class FileHandle implements Handle {
		/** Styx's name for the kind. */
		public static final String KIND = "modularcomputers.file";
		private final String diskId;
		private final String path;
		private final String mode;
		/** The file's size when it was opened (the base the writes go over). */
		private final long base;
		/** The size it will have: the base, truncated by "w", grown by writes. */
		private long length;
		private long position;
		/** Buffered writes, by where they start; never overlapping. */
		private final java.util.TreeMap<Long, byte[]> writes = new java.util.TreeMap<>();
		private long windowStart = -1;
		private byte[] window = new byte[0];
		private boolean open = true;
		/** The program's memory its buffered writes are charged to, and how much (their bytes). */
		private com.payonel.modularcomputers.runtime.MemoryAccount account = com.payonel.modularcomputers.runtime.MemoryAccount.UNLIMITED;
		private long charged;

		FileHandle(String diskId, String path, String mode, long base) {
			this.diskId = diskId;
			this.path = path;
			this.mode = mode;
			this.base = base;
			this.length = mode.startsWith("w") ? 0 : base;
			this.position = mode.startsWith("a") ? length : 0;
		}

		private boolean readable() {
			return mode.startsWith("r") || mode.endsWith("+");
		}

		private boolean writable() {
			return !mode.equals("r");
		}

		/** Its disk, if it's still there (a disk taken out leaves its handles closed). */
		private Disk disk() {
			Disk disk = LIVE.get(diskId);
			if (disk == null || !disk.handles.contains(this) && !adopt(disk)) {
				throw new Refused("closed handle");
			}
			return disk;
		}

		/** After a restore: the handle joins its disk again. */
		private boolean adopt(Disk disk) {
			if (!open || disk.handles.size() >= MAX_HANDLES) {
				return false;
			}
			disk.handles.add(this);
			disk.reserved += reservation();
			return true;
		}

		/** What its buffered writes will add to the disk. */
		private long reservation() {
			return writes.isEmpty() && length == base ? 0 : Math.max(0, length - base);
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		/** The program let go of it without closing it (Lua collected it): as a power cut, its writes are gone. */
		@Override
		public void abandon() {
			drop();
		}

		/** Closed without committing (power lost, the disk left): its writes are gone. */
		void drop() {
			Disk disk = LIVE.get(diskId);
			if (open && disk != null && disk.handles.remove(this)) {
				disk.reserved -= reservation();
			}
			open = false;
			clearWrites();
		}

		/** Its buffered writes are gone: their memory goes back to the program. */
		private void clearWrites() {
			writes.clear();
			account.release(charged);
			charged = 0;
		}

		/** After a restore: its buffered writes are charged to the restored program's memory, as they were before. */
		public void chargeTo(com.payonel.modularcomputers.runtime.MemoryAccount restored) {
			account = restored;
			charged = writes.values().stream().mapToLong(write -> write.length).sum();
			restored.force(charged);
		}

		/** The bytes at {@code at}, from the file (through the window) and over them its writes. */
		private byte[] bytesAt(Disk disk, long at, int count) throws IOException {
			count = (int) Math.max(0, Math.min(count, length - at));
			byte[] out = new byte[count];
			int done = 0;
			while (done < count) {
				long here = at + done;
				if (here >= (mode.startsWith("w") ? 0 : base)) {
					break; // past the old file: only writes (or zeros)
				}
				if (here < windowStart || here >= windowStart + window.length) {
					fill(disk, here);
				}
				int take = (int) Math.min(count - done, windowStart + window.length - here);
				if (take <= 0) {
					break;
				}
				System.arraycopy(window, (int) (here - windowStart), out, done, take);
				done += take;
			}
			for (var write : writes.subMap(writes.floorKey(at) != null ? writes.floorKey(at) : at, at + count).entrySet()) {
				long from = Math.max(at, write.getKey());
				long to = Math.min(at + count, write.getKey() + write.getValue().length);
				if (from < to) {
					System.arraycopy(write.getValue(), (int) (from - write.getKey()), out, (int) (from - at), (int) (to - from));
				}
			}
			return out;
		}

		/** Reads a window of the file around {@code at}: the host file is open only for this. */
		private void fill(Disk disk, long at) throws IOException {
			windowStart = at - at % WINDOW;
			try (RandomAccessFile file = new RandomAccessFile(disk.resolve(path, false).toFile(), "r")) {
				file.seek(windowStart);
				byte[] bytes = new byte[(int) Math.max(0, Math.min(WINDOW, file.length() - windowStart))];
				file.readFully(bytes);
				window = bytes;
			}
		}

		@Method(doc = "read(n): up to n bytes (at most " + MAX_READ + "), or nil at the end")
		public Result read(Arguments args) {
			if (!readable()) {
				return Result.fail("not open for reading");
			}
			int count = (int) Math.clamp(args.checkInteger(0), 0, MAX_READ);
			try {
				Disk disk = disk();
				if (position >= length) {
					return Result.ok();
				}
				byte[] bytes = bytesAt(disk, position, count);
				position += bytes.length;
				return Result.ok(new String(bytes, StandardCharsets.ISO_8859_1));
			} catch (Refused e) {
				return Result.fail(e.getMessage());
			} catch (IOException e) {
				return Result.fail("i/o error: " + e.getMessage());
			}
		}

		@Method(doc = "write(data): writes bytes where the file is at (at the end, opened to append); they take effect at close")
		public Result write(Arguments args) {
			if (!writable()) {
				return Result.fail("not open for writing");
			}
			byte[] data = args.checkString(0).getBytes(StandardCharsets.ISO_8859_1);
			try {
				Disk disk = disk();
				long at = mode.startsWith("a") ? length : position;
				long newLength = Math.max(length, at + data.length);
				long before = reservation();
				long grows = Math.max(0, newLength - base) - before;
				// The buffer is the program's memory, held outside its VM: charged up front, the
				// excess back once it's merged (an overwrite of what's buffered costs nothing more).
				var payer = args.account();
				if (charged > 0 && payer != account) {
					account.release(charged); // another program's now: what it holds moves with it
					payer.force(charged);
				}
				account = payer;
				if (!payer.charge(data.length)) {
					return Result.fail(NO_MEMORY); // nothing written, the program's buffer as it was
				}
				try {
					disk.reserve(grows); // refuses with NO_SPACE: nothing written, the program's buffer as it was
				} catch (Refused e) {
					payer.release(data.length);
					throw e;
				}
				long added = put(at, data);
				payer.release(data.length - added);
				charged += added;
				length = newLength;
				position = at + data.length;
				return Result.ok();
			} catch (Refused e) {
				return Result.fail(e.getMessage());
			}
		}

		/** Lays {@code data} over the buffered writes at {@code at}, merging what it touches; returns how much the buffer grew. */
		private long put(long at, byte[] data) {
			long start = at;
			long end = at + data.length;
			Long from = writes.floorKey(at);
			if (from != null && from + writes.get(from).length >= at) {
				start = from;
			}
			for (var write : writes.subMap(start, true, end, true).entrySet()) {
				end = Math.max(end, write.getKey() + write.getValue().length);
			}
			byte[] merged = new byte[(int) (end - start)];
			long had = 0;
			for (var write : List.copyOf(writes.subMap(start, true, end, true).entrySet())) {
				System.arraycopy(write.getValue(), 0, merged, (int) (write.getKey() - start), write.getValue().length);
				writes.remove(write.getKey());
				had += write.getValue().length;
			}
			System.arraycopy(data, 0, merged, (int) (at - start), data.length);
			writes.put(start, merged);
			return merged.length - had;
		}

		@Method(doc = "seek([whence[, offset]]): moves to offset from \"set\" (the start), \"cur\" (the default) or \"end\"; returns where it is")
		public Result seek(Arguments args) {
			String whence = args.optString(0, "cur").toLowerCase(Locale.ROOT);
			long offset = args.optInteger(1, 0);
			long from = switch (whence) {
				case "set" -> 0;
				case "cur" -> position;
				case "end" -> length;
				default -> -1;
			};
			if (from < 0) {
				return Result.fail("bad whence: " + whence);
			}
			if (from + offset < 0) {
				return Result.fail("can't seek before the start");
			}
			try {
				disk();
			} catch (Refused e) {
				return Result.fail(e.getMessage());
			}
			position = from + offset;
			return Result.ok(position);
		}

		@Method(doc = "close(): commits what was written, all at once (the file is all old or all new), and closes; on failure it stays open, its writes kept")
		public Result close(Arguments args) {
			if (!open) {
				return Result.ok();
			}
			try {
				Disk disk = disk();
				if (writable() && (!writes.isEmpty() || length != base)) {
					commit(disk); // (its reservation becomes the file's space)
				} else {
					disk.reserved -= reservation();
				}
				disk.handles.remove(this);
				open = false;
				clearWrites();
				return Result.ok();
			} catch (Refused e) {
				return Result.fail(e.getMessage());
			} catch (IOException e) {
				return Result.fail("i/o error: " + e.getMessage());
			}
		}

		/** Builds the new file beside the disk's folder and renames it over the old one, in one step. */
		private void commit(Disk disk) throws IOException {
			Path target = disk.resolve(path, false);
			long current = Files.isRegularFile(target) ? Files.size(target) : 0;
			// The space it takes now (the file may have changed meanwhile); its own reservation is given back as it goes.
			long reservation = reservation();
			disk.reserved -= reservation;
			try {
				disk.grow(length - current);
			} catch (Refused e) {
				disk.reserved += reservation; // nothing changed: the program can retry
				throw e;
			}
			Path scratch = Files.createTempFile(disk.scratch(), "commit", ".tmp");
			try {
				try (RandomAccessFile out = new RandomAccessFile(scratch.toFile(), "rw")) {
					if (!mode.startsWith("w") && Files.isRegularFile(target)) {
						try (var in = Files.newInputStream(target)) {
							byte[] chunk = new byte[64 * 1024];
							long left = Math.min(base, length);
							int got;
							while (left > 0 && (got = in.read(chunk, 0, (int) Math.min(chunk.length, left))) > 0) {
								out.write(chunk, 0, got);
								left -= got;
							}
						}
					}
					out.setLength(length);
					for (var write : writes.entrySet()) {
						out.seek(write.getKey());
						out.write(write.getValue());
					}
				}
				Files.move(scratch, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				Files.deleteIfExists(scratch);
				disk.grow(current - length); // undo: nothing was committed
				disk.reserved += reservation;
				throw e;
			}
			windowStart = -1;
		}

		/** What Styx keeps of it: everything above, but the window (read again as needed). */
		public byte[] persisted() {
			try (var bytes = new java.io.ByteArrayOutputStream(); var out = new java.io.DataOutputStream(bytes)) {
				out.writeInt(1);
				out.writeUTF(diskId);
				out.writeUTF(path);
				out.writeUTF(mode);
				out.writeLong(base);
				out.writeLong(length);
				out.writeLong(position);
				out.writeBoolean(open);
				out.writeInt(writes.size());
				for (var write : writes.entrySet()) {
					out.writeLong(write.getKey());
					out.writeInt(write.getValue().length);
					out.write(write.getValue());
				}
				out.flush();
				return bytes.toByteArray();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		/** A handle again, from {@link #persisted}: it joins its disk at its next use. */
		public static FileHandle restore(byte[] saved) {
			try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(saved))) {
				in.readInt();
				String diskId = in.readUTF();
				String path = in.readUTF();
				String mode = in.readUTF();
				FileHandle handle = new FileHandle(diskId, path, mode, in.readLong());
				handle.length = in.readLong();
				handle.position = in.readLong();
				handle.open = in.readBoolean();
				int count = in.readInt();
				for (int i = 0; i < count; i++) {
					long at = in.readLong();
					byte[] data = new byte[in.readInt()];
					in.readFully(data);
					handle.writes.put(at, data);
				}
				return handle;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
