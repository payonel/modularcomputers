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

	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of())));
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
		machine.setDevices(Map.of("sata:0", disk));
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		machine.board().setEeprom(new Eeprom(program));
		machine.pressPower();
		for (int i = 0; i < 20 && machine.runner().isPresent(); i++) {
			machine.tick();
		}
	}

	@Test
	void aFileWrittenThroughItsHandleIsARealFileOnTheHost() throws Exception {
		run(DiskTier.TIER_1, board -> {
			seen.add(board.invoke("sata:0", "getMode").first());
			seen.add(board.invoke("sata:0", "mkdir", "/notes").failed());
			Handle file = (Handle) board.invoke("sata:0", "open", "/notes/hello.txt", "w").first();
			seen.add(board.invoke(file, "write", "hello ").failed());
			seen.add(file.isOpen());
			// It's on the host already: no deferred writes.
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
		assertThat(seen).containsExactly("hfs", false, false, true, "hello ", "closed handle", "hello", 6L, "disk\n", null,
			List.of("notes/"), List.of("hello.txt"), seen.get(12));
		@SuppressWarnings("unchecked")
		Map<String, Object> stat = (Map<String, Object>) seen.get(12);
		assertThat(stat).containsEntry("size", 11L).containsEntry("directory", false);
		assertThat(Files.readString(storage().resolve("notes/hello.txt"))).isEqualTo("hello disk\n");
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
		assertThat(seen).containsExactly((long) Disk.ENTRY, false, (long) Disk.ENTRY + 4096, "not enough space", 0L);
	}

	@Test
	void openFilesCloseWhenTheDiskLosesPower() {
		Handle[] kept = new Handle[1];
		run(DiskTier.TIER_1, board -> {
			kept[0] = (Handle) board.invoke("sata:0", "open", "/a", "w").first();
			board.halt();
		});
		assertThat(kept[0].isOpen()).isTrue();
		machine.pressPower(); // off
		assertThat(kept[0].isOpen()).isFalse();
	}

	@Test
	void formattingRawGivesSectorsInOneImageFileAndBackErasesIt() {
		run(DiskTier.TIER_1, board -> {
			Handle file = (Handle) board.invoke("sata:0", "open", "/a", "w").first();
			seen.add(board.invoke("sata:0", "setMode", "raw").failed());
			seen.add(file.isOpen());
			seen.add(board.invoke("sata:0", "open", "/a", "w").error());
			seen.add(board.invoke("sata:0", "writeSector", 3, "boot").failed());
			Result sector = board.invoke("sata:0", "readSector", 3);
			seen.add(((String) sector.first()).length());
			seen.add(((String) sector.first()).substring(0, 4));
			seen.add(board.invoke("sata:0", "readSector", DiskTier.TIER_1.bytes() / Disk.SECTOR).error());
			seen.add(board.invoke("sata:0", "setMode", "hfs").failed());
			seen.add(board.invoke("sata:0", "list", "/").first());
			board.halt();
		});
		assertThat(seen).containsExactly(false, false, "raw disk: no filesystem", false, Disk.SECTOR, "boot",
			"no such sector: " + DiskTier.TIER_1.bytes() / Disk.SECTOR, false, List.of());
		assertThat(storage().resolveSibling("d1.img")).doesNotExist();
	}
}
