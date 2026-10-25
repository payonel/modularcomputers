package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.Program;
import com.payonel.modularcomputers.runtime.Handle;
import com.payonel.modularcomputers.runtime.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A disk serves its files (hfs, how it comes): programs open a file and get a handle to read and
 * write through, and the files are real files on the host, written at once. Formatted raw, it's
 * sectors in one image file.
 */
class DiskTest {
	@TempDir
	Path world;

	private final Machine machine = new Machine(new Motherboard(Hardware.computer(), new Bus()));
	private final List<Object> seen = new ArrayList<>();
	private Disk disk;

	@AfterEach
	void powerDown() {
		if (machine.isPowered()) {
			machine.pressPower();
		}
	}

	private Path storage() {
		return world.resolve("disks").resolve("d1");
	}

	private void run(DiskTier tier, Program program) {
		disk = new Disk(tier, storage());
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		new com.payonel.modularcomputers.machine.TestBus(machine).disk(disk);
		machine.board().setEeprom(new Eeprom(program));
		machine.pressPower();
		for (int i = 0; i < 20 && machine.runner().isPresent(); i++) {
			machine.tick();
		}
	}

	@Test
	void aSymbolicLinkIsAFileOnTheDiskInCifssFormatAndCallsThatMeetOneReparse() throws Exception {
		run(DiskTier.TIER_1, board -> {
			board.invoke("sata:0", "mkdir", "/real");
			seen.add(board.invoke("sata:0", "symlink", "/real", "/to").failed());
			seen.add(board.invoke("sata:0", "readlink", "/to").first());
			seen.add(board.invoke("sata:0", "stat", "/to", true).first());
			seen.add(board.invoke("sata:0", "list", "/", true).first());
			seen.add(board.invoke("sata:0", "open", "/to/f", "w").error()); // on the way: the OS follows it
			seen.add(board.invoke("sata:0", "stat", "/to").error()); // at the end, following
			seen.add(board.invoke("sata:0", "symlink", "x", "/to").error());
			seen.add(board.invoke("sata:0", "readlink", "/real").error());
			seen.add(board.invoke("sata:0", "rename", "/to", "/moved").failed());
			seen.add(board.invoke("sata:0", "remove", "/moved").failed()); // the link, not what it points to
			seen.add(board.invoke("sata:0", "stat", "/real").first() != null);
			board.halt();
		});
		assertThat(seen.get(0)).isEqualTo(false);
		assertThat(seen.get(1)).isEqualTo("/real");
		assertThat(seen.get(2)).isEqualTo(Map.of("size", 5L, "directory", false, "link", "/real",
			"modified", ((Map<?, ?>) seen.get(2)).get("modified")));
		assertThat(seen.get(3)).isEqualTo(List.of(Map.of("name", "real", "type", "directory"), Map.of("name", "to", "type", "link")));
		assertThat(seen.get(4)).isEqualTo(Disk.REPARSE + "/to\0/real");
		assertThat(seen.get(5)).isEqualTo(Disk.REPARSE + "/to\0/real");
		assertThat((String) seen.get(6)).startsWith("already exists");
		assertThat((String) seen.get(7)).startsWith("not a link");
		assertThat(seen.subList(8, 11)).containsExactly(false, false, true);
		assertThat(Files.exists(storage().resolve("moved"))).isFalse();
	}

	@Test
	void aLinksFileIsCifssMinshallFrenchSymlink() throws Exception {
		run(DiskTier.TIER_1, board -> {
			board.invoke("sata:0", "symlink", "a/b", "/l");
			board.halt();
		});
		byte[] bytes = Files.readAllBytes(storage().resolve("l"));
		String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
		assertThat(bytes).hasSize(Disk.LINK_SIZE);
		// XSym, the length, the target's MD5, the target, padding (md5("a/b"))
		assertThat(text).startsWith("XSym\n0003\n" + java.util.HexFormat.of().formatHex(
			java.security.MessageDigest.getInstance("MD5").digest("a/b".getBytes())) + "\na/b\n ");
		assertThat(Files.isSymbolicLink(storage().resolve("l"))).as("never a host link").isFalse();
	}

	@Test
	void aFileWrittenThroughItsHandleIsARealFileOnTheHost() throws Exception {
		run(DiskTier.TIER_1, board -> {
			seen.add(board.invoke("sata:0", "getMode").first());
			seen.add(board.invoke("sata:0", "mkdir", "/notes").failed());
			Handle file = (Handle) board.invoke("sata:0", "open", "/notes/hello.txt", "w").first();
			seen.add(board.invoke(file, "write", "hello ").failed());
			seen.add(file.isOpen());
			// Made at open, empty; the writes take effect at close, all at once (user design, 2026-10-13).
			seen.add(read(storage().resolve("notes/hello.txt")));
			board.invoke(file, "write", "disk\n");
			board.invoke(file, "close");
			seen.add(board.invoke(file, "write", "more").error());
			Handle again = (Handle) board.invoke("sata:0", "open", "/notes/hello.txt").first();
			seen.add(board.invoke(again, "read", 5).first());
			seen.add(board.invoke(again, "seek", "set", 6).first());
			seen.add(board.invoke(again, "read", 100).first());
			seen.add(board.invoke(again, "read", 100).first());
			seen.add(board.invoke("sata:0", "list", "/").first());
			seen.add(board.invoke("sata:0", "list", "/notes").first());
			seen.add(board.invoke("sata:0", "stat", "/notes/hello.txt").first());
			board.halt();
		});
		assertThat(seen).containsExactly("hfs", false, false, true, "", "closed handle", "hello", 6L, "disk\n", null,
			List.of("notes/"), List.of("hello.txt"), seen.get(12));
		@SuppressWarnings("unchecked")
		Map<String, Object> stat = (Map<String, Object>) seen.get(12);
		assertThat(stat).containsEntry("size", 11L).containsEntry("directory", false);
		assertThat(Files.readString(storage().resolve("notes/hello.txt"))).isEqualTo("hello disk\n");
	}

	/** A program's memory with room for {@code cap} bytes, counting what's charged to it. */
	private static final class Account implements com.payonel.modularcomputers.runtime.MemoryAccount {
		final long cap;
		long held;

		Account(long cap) {
			this.cap = cap;
		}

		@Override
		public boolean charge(long bytes) {
			if (held + bytes > cap) {
				return false;
			}
			held += bytes;
			return true;
		}

		@Override
		public void force(long bytes) {
			held += bytes;
		}

		@Override
		public void release(long bytes) {
			held -= bytes;
		}
	}

	@Test
	void aHandlesBufferedWritesAreChargedToTheProgramsMemory() {
		disk = new Disk(DiskTier.TIER_1, storage());
		Account account = new Account(10_000);
		String chunk = "x".repeat(4000);
		Disk.FileHandle file = (Disk.FileHandle) disk.open(new com.payonel.modularcomputers.runtime.Arguments(List.of("/f", "w"))).first();
		java.util.function.Function<Object[], com.payonel.modularcomputers.runtime.Result> write = values ->
			file.write(new com.payonel.modularcomputers.runtime.Arguments(List.of(values), account));
		assertThat(write.apply(new Object[] {chunk}).failed()).isFalse();
		assertThat(account.held).isEqualTo(4000);
		file.seek(new com.payonel.modularcomputers.runtime.Arguments(List.of("set", 0L)));
		write.apply(new Object[] {chunk});
		assertThat(account.held).as("overwriting what's buffered costs nothing more").isEqualTo(4000);
		write.apply(new Object[] {chunk});
		assertThat(account.held).isEqualTo(8000);
		assertThat(write.apply(new Object[] {chunk}).error()).as("no room: ENOMEM, nothing written").isEqualTo(Disk.NO_MEMORY);
		assertThat(account.held).isEqualTo(8000);
		assertThat(file.close(new com.payonel.modularcomputers.runtime.Arguments(List.of())).failed()).isFalse();
		assertThat(account.held).as("committed: given back").isZero();
	}

	@Test
	void aHandleTheProgramLetGoOfGivesBackItsMemoryAndSpace() {
		disk = new Disk(DiskTier.TIER_1, storage());
		Account account = new Account(100_000);
		long used = (long) disk.getUsed(new com.payonel.modularcomputers.runtime.Arguments(List.of())).first();
		Disk.FileHandle file = (Disk.FileHandle) disk.open(new com.payonel.modularcomputers.runtime.Arguments(List.of("/g", "w"))).first();
		file.write(new com.payonel.modularcomputers.runtime.Arguments(List.of("y".repeat(5000)), account));
		file.abandon();
		assertThat(account.held).isZero();
		assertThat(file.isOpen()).isFalse();
		assertThat(disk.getUsed(new com.payonel.modularcomputers.runtime.Arguments(List.of())).first()).as("its reservation too").isEqualTo(used + 512);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	@Test
	void pathsCantClimbOutOfTheDisk() throws Exception {
		run(DiskTier.TIER_1, board -> {
			Handle file = (Handle) board.invoke("sata:0", "open", "/../../escape.txt", "w").first();
			board.invoke(file, "write", "x");
			board.invoke(file, "close");
			board.halt();
		});
		assertThat(world.resolve("escape.txt")).doesNotExist();
		assertThat(storage().resolve("escape.txt")).hasContent("x");
	}

	@Test
	void itRefusesWhatDoesntFitAndCountsEachFile() {
		run(DiskTier.TIER_1, board -> {
			Handle file = (Handle) board.invoke("sata:0", "open", "/big", "w").first();
			seen.add(board.invoke("sata:0", "getUsed").first());
			seen.add(board.invoke(file, "write", "x".repeat(4096)).failed());
			seen.add(board.invoke("sata:0", "getUsed").first());
			long room = DiskTier.TIER_1.bytes() - (Long) board.invoke("sata:0", "getUsed").first();
			// Writes go 4096 at a time; fill it up, then one byte more fails.
			while (room > 0) {
				int chunk = (int) Math.min(room, 3000);
				board.invoke(file, "write", "y".repeat(chunk));
				room -= chunk;
			}
			seen.add(board.invoke(file, "write", "z").error());
			board.invoke(file, "close");
			board.invoke("sata:0", "remove", "/big");
			seen.add(board.invoke("sata:0", "getUsed").first());
			board.halt();
		});
		assertThat(seen).containsExactly((long) Disk.ENTRY, false, (long) Disk.ENTRY + 4096, Disk.NO_SPACE, 0L);
	}

	@Test
	void openFilesCloseWhenTheDiskLosesPower() {
		Handle[] kept = new Handle[1];
		run(DiskTier.TIER_1, board -> {
			kept[0] = (Handle) board.invoke("sata:0", "open", "/a", "w").first();
			board.waitForInterrupt(1000, "nothing");
			board.halt();
		});
		assertThat(kept[0].isOpen()).isTrue();
		machine.pressPower(); // off
		assertThat(kept[0].isOpen()).isFalse();
	}

	@Test
	void aProgramThatHaltsLeavesNoFilesOpen() {
		Handle[] kept = new Handle[1];
		run(DiskTier.TIER_1, board -> {
			kept[0] = (Handle) board.invoke("sata:0", "open", "/a", "w").first();
			board.halt();
		});
		assertThat(kept[0].isOpen()).as("its program's gone: what it held goes too").isFalse();
	}

	@Test
	void formattingRawGivesBytesByOffsetInOneImageFileAndBackErasesIt() {
		long size = DiskTier.TIER_1.bytes();
		run(DiskTier.TIER_1, board -> {
			seen.add(board.invoke("sata:0", "write", 0, "x").error()); // an hfs disk: its filesystem owns it
			Handle file = (Handle) board.invoke("sata:0", "open", "/a", "w").first();
			seen.add(board.invoke("sata:0", "setMode", "raw").failed());
			seen.add(file.isOpen());
			seen.add(board.invoke("sata:0", "open", "/a", "w").error());
			seen.add(board.invoke("sata:0", "write", 1000, "boot").first());
			seen.add(board.invoke("sata:0", "read", 998, 8).first()); // (never written: zeros)
			seen.add(board.invoke("sata:0", "write", size - 2, "tail").first()); // as many as fit
			seen.add(board.invoke("sata:0", "read", size - 2, 10).first());
			seen.add(board.invoke("sata:0", "read", size, 1).first()); // the end
			seen.add(board.invoke("sata:0", "read", size + 1, 1).error());
			seen.add(board.invoke("sata:0", "setMode", "hfs").failed());
			seen.add(board.invoke("sata:0", "list", "/").first());
			board.halt();
		});
		assertThat(seen).containsExactly("hfs disk: format it raw to read and write it by offset", false, false, "raw disk: no filesystem",
			4L, "\0\0boot\0\0", 2L, "ta", null, "no such offset: " + (size + 1) + " (0 to " + size + ")", false, List.of());
		assertThat(storage().resolveSibling("d1.img")).doesNotExist();
	}

	@Test
	void nothingAProgramNamesReachesOutsideTheDisksFolder() throws Exception {
		Files.createDirectories(world.resolve("disks"));
		Files.writeString(world.resolve("secret.txt"), "the host's");
		run(DiskTier.TIER_1, board -> {
			for (String path : List.of("../../secret.txt", "/../../../secret.txt", "a/../../..//secret.txt", "../d1/../../secret.txt")) {
				// Relative or absolute, a path starts at the disk's root, and .. at the root stays there.
				seen.add(board.invoke("sata:0", "open", path).error());
			}
			for (String name : List.of("C:x", "a\\b", "nul", "con.txt", "trailing.", "trailing ")) {
				seen.add(board.invoke("sata:0", "open", name, "w").error());
			}
			Handle file = (Handle) board.invoke("sata:0", "open", "../../escape.txt", "w").first();
			board.invoke(file, "write", "stays in");
			board.invoke(file, "close");
			board.halt();
		});
		assertThat(seen.subList(0, 4)).allMatch(error -> String.valueOf(error).startsWith("no such file"));
		assertThat(seen.subList(4, 10)).allMatch(error -> String.valueOf(error).startsWith("bad name"));
		assertThat(Files.readString(storage().resolve("escape.txt"))).as("written inside the disk").isEqualTo("stays in");
		assertThat(Files.exists(world.resolve("escape.txt"))).isFalse();
	}

	@Test
	void aLinkOnTheHostCantTakeItOutside() throws Exception {
		Files.createDirectories(storage());
		Files.writeString(world.resolve("secret.txt"), "the host's");
		Files.createSymbolicLink(storage().resolve("link"), world);
		run(DiskTier.TIER_1, board -> {
			seen.add(board.invoke("sata:0", "open", "/link/secret.txt").error());
			seen.add(board.invoke("sata:0", "open", "/link/new.txt", "w").error());
			board.halt();
		});
		assertThat(seen).allMatch(error -> String.valueOf(error).startsWith("outside the disk"));
		assertThat(Files.exists(world.resolve("new.txt"))).isFalse();
	}

	@Test
	void aBigFileIsReadThroughASmallWindow() throws Exception {
		Files.createDirectories(storage());
		StringBuilder text = new StringBuilder();
		for (int i = 0; text.length() < 20_000; i++) {
			text.append(i).append(',');
		}
		Files.writeString(storage().resolve("big.txt"), text);
		run(DiskTier.TIER_1, board -> {
			Handle file = (Handle) board.invoke("sata:0", "open", "/big.txt").first();
			StringBuilder got = new StringBuilder();
			Object chunk;
			while ((chunk = board.invoke(file, "read", 4096).first()) != null) {
				got.append(chunk);
			}
			seen.add(got.toString());
			board.invoke(file, "close");
			board.halt();
		});
		assertThat(seen).containsExactly(text.toString());
	}

	@Test
	void writesTakeEffectAtCloseAllAtOnceAndAreReadBackBeforeThat() throws Exception {
		Files.createDirectories(storage());
		Files.writeString(storage().resolve("a.txt"), "hello world");
		run(DiskTier.TIER_1, board -> {
			Handle file = (Handle) board.invoke("sata:0", "open", "/a.txt", "r+").first();
			board.invoke(file, "seek", "set", 6);
			board.invoke(file, "write", "WORLD!");
			board.invoke(file, "seek", "set", 0);
			seen.add(board.invoke(file, "read", 100).first()); // its own writes, over the file
			seen.add(read(storage().resolve("a.txt"))); // the host file: as it was
			seen.add(board.invoke(file, "close").failed());
			seen.add(read(storage().resolve("a.txt")));
			board.halt();
		});
		assertThat(seen).containsExactly("hello WORLD!", "hello world", false, "hello WORLD!");
	}

	@Test
	void writesNotClosedAreLostWithThePowerAndTheirSpaceIsFree() throws Exception {
		Files.createDirectories(storage());
		Files.writeString(storage().resolve("a.txt"), "old");
		run(DiskTier.TIER_1, board -> {
			Handle file = (Handle) board.invoke("sata:0", "open", "/a.txt", "w").first();
			board.invoke(file, "write", "x".repeat(10_000));
			seen.add(board.invoke("sata:0", "getUsed").first());
			board.halt();
		});
		machine.pressPower(); // a power cut
		assertThat(Files.readString(storage().resolve("a.txt"))).isEqualTo("old");
		run(DiskTier.TIER_1, board -> {
			seen.add(board.invoke("sata:0", "getUsed").first());
			board.halt();
		});
		assertThat(seen).containsExactly((long) Disk.ENTRY + 10_000, (long) Disk.ENTRY + 3);
	}
}
