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
 * files itself. Writes go to the host file at once (no deferred writes).</li>
 * <li><b>raw</b>, an opaque block device: sectors of {@link #SECTOR} bytes, kept on the host as one
 * image file.</li>
 * </ul>
 * Paths are the disk's own, from its root ({@code /notes/a.txt}); {@code ..} can't climb out. Data
 * is bytes: strings carry one character per byte (ISO-8859-1), as Lua's do. Space counts each
 * file's bytes plus {@link #ENTRY} for each file and directory, so empty files aren't free.
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

	private final DiskTier tier;
	private final BusPower power;
	/** Where its data lives: a folder (hfs), or this plus ".img" (raw). Null on a client's copy: no data there. */
	private final Path storage;
	private final List<FileHandle> handles = new ArrayList<>();
	private String label;
	/** Bytes in use, worked out on first need, then kept up to date. */
	private long used = -1;

	public Disk(DiskTier tier) {
		this(tier, null);
	}

	public Disk(DiskTier tier, Path storage) {
		this.tier = tier;
		this.power = new BusPower(tier.draw());
		this.storage = storage;
	}

	public DiskTier tier() {
		return tier;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
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

	/** Power's gone, or it left the bus: open files close. */
	@Override
	public void powerOff() {
		for (FileHandle handle : List.copyOf(handles)) {
			handle.close();
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

	/** A path on the disk, as a host path inside its folder. */
	private Path resolve(String path) {
		Path root = root();
		Path at = root;
		for (String name : path.split("/")) {
			if (name.isEmpty() || name.equals(".")) {
				continue;
			}
			if (name.equals("..")) {
				at = at.equals(root) ? root : at.getParent();
				continue;
			}
			if (name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
				throw new Refused("bad name: " + name);
			}
			at = at.resolve(name);
		}
		return at;
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
		if (bytes > 0 && used() + bytes > tier.bytes()) {
			throw new Refused("not enough space");
		}
		used = used() + bytes;
	}

	private Result run(java.util.function.Supplier<Result> action) {
		try {
			return action.get();
		} catch (Refused e) {
			return Result.fail(e.getMessage());
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

	@Method(doc = "getUsed(): how many bytes are in use (hfs: files and their entries; raw: all of it)")
	public Result getUsed(Arguments args) {
		return run(() -> Result.ok(mode().equals(RAW) ? tier.bytes() : used()));
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

	@Method(doc = "open(path[, mode]): opens a file and returns its handle; mode is r (the default), r+, w, w+, a or a+, as C's fopen")
	public Result open(Arguments args) {
		String path = args.checkString(0);
		String mode = args.optString(1, "r").replace("b", "");
		return run(() -> {
			Path file = resolve(path);
			boolean read = mode.startsWith("r") || mode.endsWith("+");
			boolean write = !mode.equals("r");
			if (!List.of("r", "r+", "w", "w+", "a", "a+").contains(mode)) {
				throw new Refused("bad mode: " + mode);
			}
			if (Files.isDirectory(file)) {
				throw new Refused("is a directory: " + path);
			}
			if (handles.size() >= MAX_HANDLES) {
				throw new Refused("too many open files");
			}
			boolean exists = Files.isRegularFile(file);
			if (!exists && mode.startsWith("r")) {
				throw new NoSuchFileRuntime(path);
			}
			if (!exists && !Files.isDirectory(file.getParent())) {
				throw new NoSuchFileRuntime(path);
			}
			try {
				if (!exists) {
					grow(ENTRY);
				} else if (mode.startsWith("w")) {
					grow(-Files.size(file));
				}
				RandomAccessFile host = new RandomAccessFile(file.toFile(), write ? "rw" : "r");
				if (mode.startsWith("w")) {
					host.setLength(0);
				}
				FileHandle handle = new FileHandle(this, host, read, write, mode.startsWith("a"));
				handles.add(handle);
				return Result.ok(handle);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	@Method(doc = "list(path): the names in a directory, sorted; directories end in /")
	public Result list(Arguments args) {
		String path = args.optString(0, "/");
		return run(() -> {
			Path dir = resolve(path);
			if (!Files.isDirectory(dir)) {
				throw new NoSuchFileRuntime(path);
			}
			try (Stream<Path> paths = Files.list(dir)) {
				return Result.ok(paths.map(entry -> entry.getFileName() + (Files.isDirectory(entry) ? "/" : "")).sorted().toList());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	@Method(doc = "stat(path): {size, directory, modified} (modified in milliseconds since 1970), or nil if there's nothing there")
	public Result stat(Arguments args) {
		String path = args.checkString(0);
		return run(() -> {
			Path at = resolve(path);
			if (at.equals(storage)) {
				return Result.ok(Map.of("size", 0L, "directory", true, "modified", 0L));
			}
			if (!Files.exists(at)) {
				return Result.ok();
			}
			try {
				Map<String, Object> stat = new LinkedHashMap<>();
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
			Path dir = resolve(path);
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

	@Method(doc = "remove(path): removes a file, or an empty directory")
	public Result remove(Arguments args) {
		String path = args.checkString(0);
		return run(() -> {
			Path at = resolve(path);
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
			Path source = resolve(from);
			Path target = resolve(to);
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
	 * An open file on an hfs disk: a proxy that holds the disk and the real host file. Its methods
	 * are the program's; it closes when the program closes it, or the disk loses power.
	 */
	public static final class FileHandle implements Handle {
		private final Disk disk;
		private final RandomAccessFile file;
		private final boolean read;
		private final boolean write;
		private final boolean append;
		private boolean open = true;

		FileHandle(Disk disk, RandomAccessFile file, boolean read, boolean write, boolean append) {
			this.disk = disk;
			this.file = file;
			this.read = read;
			this.write = write;
			this.append = append;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		void close() {
			if (open) {
				open = false;
				disk.handles.remove(this);
				try {
					file.close();
				} catch (IOException ignored) {
					// closed either way
				}
			}
		}

		@Method(doc = "read(n): up to n bytes (at most " + MAX_READ + "), or nil at the end")
		public Result read(Arguments args) {
			if (!read) {
				return Result.fail("not open for reading");
			}
			int count = (int) Math.clamp(args.checkInteger(0), 0, MAX_READ);
			try {
				byte[] bytes = new byte[count];
				int got = file.read(bytes);
				return got < 0 ? Result.ok() : Result.ok(new String(bytes, 0, got, StandardCharsets.ISO_8859_1));
			} catch (IOException e) {
				return Result.fail("i/o error: " + e.getMessage());
			}
		}

		@Method(doc = "write(data): writes bytes where the file is at (at the end, opened to append)")
		public Result write(Arguments args) {
			if (!write) {
				return Result.fail("not open for writing");
			}
			byte[] data = args.checkString(0).getBytes(StandardCharsets.ISO_8859_1);
			try {
				if (append) {
					file.seek(file.length());
				}
				long grows = Math.max(0, file.getFilePointer() + data.length - file.length());
				disk.grow(grows);
				file.write(data);
				return Result.ok();
			} catch (Refused e) {
				return Result.fail(e.getMessage());
			} catch (IOException e) {
				return Result.fail("i/o error: " + e.getMessage());
			}
		}

		@Method(doc = "seek([whence[, offset]]): moves to offset from \"set\" (the start), \"cur\" (the default) or \"end\"; returns where it is")
		public Result seek(Arguments args) {
			String whence = args.optString(0, "cur").toLowerCase(Locale.ROOT);
			long offset = args.optInteger(1, 0);
			try {
				long base = switch (whence) {
					case "set" -> 0;
					case "cur" -> file.getFilePointer();
					case "end" -> file.length();
					default -> -1;
				};
				if (base < 0) {
					return Result.fail("bad whence: " + whence);
				}
				if (base + offset < 0) {
					return Result.fail("can't seek before the start");
				}
				file.seek(base + offset);
				return Result.ok(file.getFilePointer());
			} catch (IOException e) {
				return Result.fail("i/o error: " + e.getMessage());
			}
		}

		@Method(doc = "close(): closes the file")
		public Result close(Arguments args) {
			close();
			return Result.ok();
		}
	}
}
