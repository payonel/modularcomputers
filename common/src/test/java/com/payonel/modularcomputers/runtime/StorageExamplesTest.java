package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskStation;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.machine.LuaScripts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * The manual's storage programs (docs/examples: disk-*, eeprom-*, disk-station-*), each run on a
 * computer with the devices it uses, as a player would copy one. Each test checks what the program
 * did to the disks, the EEPROM and the files, never what mcos says.
 */
class StorageExamplesTest extends McosHarness {
	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	private static void write(Path file, String text) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A raw disk at sata:1, with its folder. */
	private Disk rawDisk() {
		Disk raw = new Disk(DiskTier.TIER_1, world.resolve("raw-disk"));
		raw.format(Disk.RAW);
		devices.put("sata:1", raw);
		return raw;
	}

	/** Until the program's commands ended, the shell writing their last exit status to /status. */
	private String status(String commands) {
		Path status = root().resolve("status");
		try {
			Files.deleteIfExists(status);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		type(commands + "; echo $? > /status\r");
		settle(() -> Files.isRegularFile(status) && read(status).endsWith("\n"));
		return read(status).strip();
	}

	// ---- hard disks ---------------------------------------------------------------------------

	@Test
	void theBackupProgramCopiesHomeOntoTheSecondDiskKeepingTheLastThree() {
		Path data = dataDisk();
		boot();
		file("home/a.txt", "alpha");
		file("home/notes/b.txt", "beta");
		file("backup.lua", example("disk-backup"));
		assertThat(status("lua /backup.lua; lua /backup.lua; lua /backup.lua; lua /backup.lua")).isEqualTo("0");
		Path backups = data.resolve("backup");
		assertThat(backups.resolve("1")).as("the oldest, removed").doesNotExist();
		for (String n : List.of("2", "3", "4")) {
			assertThat(backups.resolve(n + "/a.txt")).hasContent("alpha");
			assertThat(backups.resolve(n + "/notes/b.txt")).hasContent("beta");
		}
		assertThat(backups.resolve("5")).doesNotExist();
	}

	@Test
	void theBackupProgramRefusesABackupThatDoesNotFit() {
		Path data = dataDisk();
		write(data.resolve("filler"), "x".repeat(DiskTier.TIER_1.bytes() - 4 * Disk.ENTRY)); // (the backup disk nearly full)
		boot();
		file("home/big.txt", "y".repeat(4 * Disk.ENTRY));
		file("backup.lua", example("disk-backup"));
		assertThat(status("lua /backup.lua")).isEqualTo("1");
		assertThat(data.resolve("backup")).as("nothing written").doesNotExist();
	}

	@Test
	void theRawLogAddsLinesToARawDiskAndShowsThemBackInOrder() {
		Disk raw = rawDisk();
		boot();
		file("log.lua", example("disk-raw-log"));
		file("in", "first line\nsecond\n");
		file("in2", "third\n");
		assertThat(status("lua /log.lua add < /in; lua /log.lua add < /in2; lua /log.lua show > /out")).isEqualTo("0");
		assertThat(read(root().resolve("out"))).isEqualTo("first line\nsecond\nthird\n");
		assertThat(raw.read(args(0L, 40L)).first()).as("the header, then the lines, on the disk's bytes")
			.isEqualTo("rawlog 00000024\nfirst line\nsecond\nthird\n");
		assertThat(status("lua /log.lua clear; lua /log.lua show > /out2")).isEqualTo("0");
		assertThat(read(root().resolve("out2"))).isEmpty();
		raw.retire();
	}

	@Test
	void theRawLogRefusesAnHfsDisk() {
		dataDisk();
		boot();
		file("log.lua", example("disk-raw-log"));
		assertThat(status("lua /log.lua show")).isEqualTo("1");
	}

	@Test
	void theWatcherLogsEachChangeMadeOnTheDisk() {
		Path data = dataDisk();
		write(data.resolve("old.txt"), "x");
		Disk disk = (Disk) devices.get("sata:1");
		boot();
		file("watch.lua", example("disk-watch"));
		type("lua /watch.lua /w.log &\r");
		untilIdle();
		Path log = root().resolve("w.log");
		// (made on the disk by another: as a second computer or a Disk Station would)
		server.add(() -> disk.mkdir(args("/photos")));
		settle(() -> Files.isRegularFile(log) && read(log).contains(" CREATE /mnt/sata/1/photos\n"));
		server.add(() -> disk.rename(args("/old.txt", "/new.txt")));
		settle(() -> read(log).contains(" MOVED_TO /mnt/sata/1/new.txt\n"));
		assertThat(read(log)).contains(" MOVED_FROM /mnt/sata/1/old.txt\n");
		assertThat(read(log).lines()).as("a line a notice").hasSize(3);
	}

	@Test
	void theSurveyListsEachDisksFormatUseAndRoot() {
		Path data = dataDisk();
		write(data.resolve("notes.txt"), "12345");
		write(data.resolve("photos/one.txt"), "1");
		Disk raw = new Disk(DiskTier.TIER_2, world.resolve("raw-disk"));
		raw.format(Disk.RAW);
		devices.put("sata:2", raw);
		boot();
		file("survey.lua", example("disk-survey"));
		assertThat(status("lua /survey.lua > /out")).isEqualTo("0");
		String out = read(root().resolve("out"));
		assertThat(out).contains("storage0 sata:0 hfs ")
			.contains("storage1 sata:1 hfs ")
			.contains("\n  notes.txt 5\n")
			.contains("\n  photos/\n")
			.contains("storage2 sata:2 raw 4096/4096 KiB used");
		raw.retire();
	}

	// ---- the EEPROM ---------------------------------------------------------------------------

	private String eeprom() {
		return machine.board().eeprom().orElseThrow().image();
	}

	@Test
	void theEepromBackupSavesTheImage() {
		boot();
		file("backup.lua", example("eeprom-backup"));
		assertThat(status("lua /backup.lua")).isEqualTo("0");
		assertThat(read(root().resolve("home/eeprom.bak"))).isEqualTo(LuaScripts.read("eeprom/boot")).isEqualTo(eeprom());
	}

	@Test
	void theFlasherRefusesWhatDoesNotCompileAndFlashesWhatDoesKeepingTheOld() {
		boot();
		String old = eeprom();
		file("flash.lua", example("eeprom-flash"));
		file("bad.lua", "this is not ( lua");
		file("new.lua", "-- a new image\nwhile true do board.waitForInterrupt(nil, 'park') end\n");
		assertThat(status("lua /flash.lua /bad.lua")).isEqualTo("1");
		assertThat(eeprom()).as("left as it was").isEqualTo(old);
		assertThat(status("lua /flash.lua /new.lua")).isEqualTo("0");
		assertThat(eeprom()).isEqualTo("-- a new image\nwhile true do board.waitForInterrupt(nil, 'park') end\n");
		assertThat(read(root().resolve("home/eeprom.old"))).isEqualTo(old);
	}

	@Test
	void theBootOnceProgramBootsAnotherDiskNextTimeOnlyThenThisOneAgain() throws IOException {
		Path data = dataDisk();
		Files.createDirectories(data);
		Files.writeString(data.resolve("init.lua"), """
			local disk = ...
			local ok, file = board.invoke(disk, "open", "/booted", "w")
			board.invoke(file, "close")
			board.poweroff()
			"""); // (an OS that says it booted, and turns the computer off)
		boot();
		file("once.lua", example("eeprom-boot-once"));
		type("lua /once.lua storage1\r");
		settle(() -> Files.exists(data.resolve("booted")) && !machine.isPowered());
		var nvram = machine.board().eeprom().orElseThrow().nvram();
		assertThat(nvram.entries()).hasSize(1);
		assertThat(nvram.next()).as("BootNext: once only").isEqualTo(com.payonel.modularcomputers.machine.BootVariables.NONE);
		assertThat(machine.pressPower()).isEqualTo(com.payonel.modularcomputers.machine.Machine.PowerPress.ON);
		untilIdle(); // (mcos again: the fallback)
		type("touch /after\r");
		untilFile(root().resolve("after"), "");
	}

	// ---- the Disk Station ---------------------------------------------------------------------

	/** Its block, stood in for: two slots (changed on the server thread), power that can run out, mcos's image. */
	private static final class Bay implements DiskStation.Bay {
		final Map<String, Disk> disks = new ConcurrentHashMap<>();
		volatile boolean powered = true;

		@Override
		public Disk disk(String which) {
			return disks.get(which);
		}

		@Override
		public boolean pay(int cost) {
			return powered;
		}

		@Override
		public List<Path> copyImage(String image, Path folder, boolean replace) {
			return LuaScripts.copyTree(image, folder, replace);
		}

		/** Puts a disk in a slot (null: takes it out), as a player does; a source is only read. */
		void put(String slot, Disk disk) {
			if (disk == null) {
				disks.remove(slot);
				return;
			}
			disk.readOnly(slot.equals(DiskStation.SOURCE));
			disks.put(slot, disk);
		}
	}

	private final Bay bay = new Bay();

	private void station() {
		devices.put("id:station", new DiskStation(bay)); // (cabled, as a station is)
	}

	@Test
	void theStationProgramInstallsMcosAndRefusesWhatCannotBeDone() {
		Path folder = world.resolve("target");
		Disk target = new Disk(DiskTier.TIER_2, folder);
		bay.put(DiskStation.TARGET, target);
		station();
		boot();
		file("do.lua", example("disk-station-do"));
		assertThat(status("lua /do.lua install")).isEqualTo("0");
		assertThat(folder.resolve("init.lua")).as("mcos installed").isRegularFile();
		assertThat(status("lua /do.lua clone")).as("no source disk").isEqualTo("1");
		bay.powered = false;
		assertThat(status("lua /do.lua wipe")).as("no power: the write fails").isEqualTo("1");
		assertThat(target.getMode(args()).first()).as("not wiped").isEqualTo(Disk.HFS);
		bay.powered = true;
		assertThat(status("lua /do.lua wipe")).isEqualTo("0");
		assertThat(target.getMode(args()).first()).isEqualTo(Disk.RAW);
		assertThat(status("lua /do.lua update")).as("a raw disk has no mcos to update").isEqualTo("1");
		target.retire();
	}

	@Test
	void theCloneProgramCopiesTheSourceOntoTheTarget() {
		Path from = world.resolve("source");
		write(from.resolve("docs/readme.txt"), "hello");
		Disk source = new Disk(DiskTier.TIER_1, from);
		Path to = world.resolve("target");
		Disk target = new Disk(DiskTier.TIER_2, to);
		target.mkdir(args("/old"));
		bay.put(DiskStation.SOURCE, source);
		bay.put(DiskStation.TARGET, target);
		station();
		boot();
		file("clone.lua", example("disk-station-clone"));
		assertThat(status("lua /clone.lua > /out")).isEqualTo("0");
		assertThat(to.resolve("docs/readme.txt")).hasContent("hello");
		assertThat(to.resolve("old")).as("what was there, gone").doesNotExist();
		assertThat(from.resolve("docs/readme.txt")).as("the source, as it was").hasContent("hello");
		source.retire();
		target.retire();
	}

	@Test
	void theCloneProgramReportsARefusalAndChangesNothing() {
		Disk source = new Disk(DiskTier.TIER_2, world.resolve("source"));
		source.format(Disk.RAW); // (a raw disk needs a target as big)
		Path to = world.resolve("target");
		Disk target = new Disk(DiskTier.TIER_1, to);
		target.mkdir(args("/kept"));
		bay.put(DiskStation.SOURCE, source);
		bay.put(DiskStation.TARGET, target);
		station();
		boot();
		file("clone.lua", example("disk-station-clone"));
		assertThat(status("lua /clone.lua > /out")).isEqualTo("1");
		assertThat(to.resolve("kept")).isDirectory();
		assertThat(target.getMode(args()).first()).isEqualTo(Disk.HFS);
		source.retire();
		target.retire();
	}

	@Test
	void theDuplicatorInstallsOrClonesEachDiskPutInTheTarget() {
		station();
		boot();
		file("dup.lua", example("disk-station-duplicator"));
		type("lua /dup.lua &\r");
		untilIdle();
		Path first = world.resolve("first");
		Disk one = new Disk(DiskTier.TIER_2, first);
		server.add(() -> bay.put(DiskStation.TARGET, one));
		settle(() -> Files.isRegularFile(first.resolve("init.lua"))); // no source: mcos installed
		Path log = root().resolve("home/duplicator.log");
		settle(() -> Files.isRegularFile(log) && read(log).contains("install: done"));

		Path from = world.resolve("source");
		write(from.resolve("game/save.txt"), "level 3");
		Disk source = new Disk(DiskTier.TIER_1, from);
		Path second = world.resolve("second");
		Disk two = new Disk(DiskTier.TIER_2, second);
		server.add(() -> {
			bay.put(DiskStation.TARGET, null); // (the first taken out, then the next put in)
			bay.put(DiskStation.SOURCE, source);
		});
		settle(() -> read(log).lines().count() == 1 && bay.disk(DiskStation.TARGET) == null);
		sleepTicks(60); // (it looks every second: it sees the slot empty)
		server.add(() -> bay.put(DiskStation.TARGET, two));
		settle(() -> Files.isRegularFile(second.resolve("game/save.txt")));
		settle(() -> read(log).contains("clone: done"));
		assertThat(second.resolve("init.lua")).as("a copy of the source, not an install").doesNotExist();
		one.retire();
		two.retire();
		source.retire();
	}

	/** Plays the server for a while (a program's sleep to pass). */
	private void sleepTicks(int ticks) {
		int[] left = {ticks};
		settle(() -> left[0]-- <= 0);
	}
}
