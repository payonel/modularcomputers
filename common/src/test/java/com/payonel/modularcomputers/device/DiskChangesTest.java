package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A disk reports every change on it, in its own terms (user design, 2026-10-21). */
class DiskChangesTest {
	@TempDir
	Path world;

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	private static List<Object> changes(Disk disk) {
		List<Object> all = new ArrayList<>();
		Object change;
		while ((change = disk.changes(args()).first()) != null) {
			all.add(change);
		}
		return all;
	}

	@SuppressWarnings("unchecked")
	private static Object modified(Disk disk, String path) {
		return ((Map<String, Object>) disk.stat(args(path)).first()).get("modified");
	}

	@Test
	void aFilesTimeIsTheWorldsWhenItChangedNotTheHosts() {
		long[] ticks = {1200};
		Disk disk = new Disk(DiskTier.TIER_1, world.resolve("timed"));
		disk.clockFrom(() -> ticks[0]);
		disk.mkdir(args("/dir"));
		Disk.FileHandle file = (Disk.FileHandle) disk.open(args("/dir/a.txt", "w")).first();
		ticks[0] = 1300;
		file.write(args("hello"));
		file.close(args());
		assertThat(modified(disk, "/dir/a.txt")).as("written at tick 1300: 50 ms a tick").isEqualTo(1300L * 50);
		assertThat(modified(disk, "/dir")).as("its directory, as an entry in it changed").isEqualTo(1200L * 50);
		ticks[0] = 1400;
		disk.remove(args("/dir/a.txt"));
		assertThat(modified(disk, "/dir")).as("an entry gone").isEqualTo(1400L * 50);
		Disk.stampAll(world.resolve("timed"), 1500);
		assertThat(modified(disk, "/dir")).as("an image copied in: made then").isEqualTo(1500L * 50);
		disk.retire();
	}

	@Test
	void anHfsDiskReportsWhatChangesOnItByItsOwnPaths() {
		Disk disk = new Disk(DiskTier.TIER_1, world.resolve("disk"));
		disk.accepting(true); // on a running board's bus
		disk.mkdir(args("/dir"));
		Disk.FileHandle file = (Disk.FileHandle) disk.open(args("dir//a.txt", "w")).first();
		file.write(args("hello"));
		file.close(args());
		disk.rename(args("/dir/a.txt", "/dir/b.txt"));
		disk.remove(args("/dir/b.txt"));
		assertThat(changes(disk)).containsExactly(
			Map.of("event", "create", "path", "/dir"),
			Map.of("event", "create", "path", "/dir/a.txt"),
			Map.of("event", "modify", "path", "/dir/a.txt"),
			Map.of("event", "rename", "path", "/dir/b.txt", "from", "/dir/a.txt"),
			Map.of("event", "remove", "path", "/dir/b.txt"));
		disk.retire();
	}

	@Test
	void itReportsNothingWhileNoProgramRunsAndARawDiskReportsBytes() {
		Disk disk = new Disk(DiskTier.TIER_1, world.resolve("raw"));
		disk.mkdir(args("/dir")); // not accepting: no report
		assertThat(disk.interruptPending()).isFalse();
		disk.accepting(true);
		disk.setMode(args(Disk.RAW));
		disk.write(args(1030L, "abc"));
		assertThat(changes(disk)).containsExactly(Map.of("event", "format", "path", "/", "mode", "raw"),
			Map.of("event", "write", "offset", 1030L, "length", 3L));
		disk.retire();
	}
}
