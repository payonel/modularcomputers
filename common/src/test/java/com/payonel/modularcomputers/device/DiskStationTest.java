package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Disk Station's actions, as methods and as set's lines, on a stand-in for its block. */
class DiskStationTest {
	@TempDir
	Path world;

	/** Its block, stood in for: two slots, and a power supply that can run out. */
	private final class Bay implements DiskStation.Bay {
		final Map<String, Disk> disks = new HashMap<>();
		boolean powered = true;

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
			try {
				Path file = folder.resolve("init.lua");
				if (Files.exists(file) && !replace) {
					throw new IllegalStateException("there already");
				}
				Files.writeString(file, image + (replace ? " updated" : ""));
				return List.of(file);
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
	}

	private final Bay bay = new Bay();
	private final DiskStation station = new DiskStation(bay);

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	private Disk disk(String name) {
		Disk disk = new Disk(DiskTier.TIER_1, world.resolve(name));
		disk.mkdir(args("/home"));
		return disk;
	}

	@Test
	void itInstallsUpdatesFormatsAndWipesItsDisk() {
		Disk disk = disk("t");
		bay.disks.put(DiskStation.TARGET, disk);
		assertThat(station.install(args()).failed()).isFalse();
		assertThat(world.resolve("t/home")).as("formatted first").doesNotExist();
		assertThat(world.resolve("t/init.lua")).hasContent("mcos");
		disk.mkdir(args("/mine"));
		assertThat(station.update(args()).failed()).isFalse();
		assertThat(world.resolve("t/init.lua")).hasContent("mcos updated");
		assertThat(world.resolve("t/mine")).as("an update leaves the rest").exists();
		assertThat(station.wipe(args()).failed()).isFalse();
		assertThat(disk.getMode(args()).first()).isEqualTo(Disk.RAW);
		assertThat(station.format(args()).failed()).isFalse();
		assertThat(disk.getMode(args()).first()).isEqualTo(Disk.HFS);
		disk.retire();
	}

	@Test
	void anActionNeedsADiskAndPowerAndACloneASource() {
		assertThat(station.format(args()).failure().table()).isEqualTo(java.util.Map.of("why", "fault", "condition", "missing", "part", "destination", "message", "storage disk target required"));
		Disk disk = disk("p");
		bay.disks.put(DiskStation.TARGET, disk);
		assertThat(station.clone(args()).failure().table()).isEqualTo(java.util.Map.of("why", "fault", "condition", "missing", "part", "source", "message", "source disk required"));
		bay.powered = false;
		Result unpowered = station.format(args());
		assertThat(unpowered.failure().table()).isEqualTo(java.util.Map.of("why", "fault", "condition", "missing", "part", "power", "message", "power required"));
		assertThat(world.resolve("p/home")).as("nothing done").exists();
		disk.retire();
	}

	@Test
	void itClonesItsSourceOntoItsDisk() {
		Disk source = disk("s");
		Disk target = new Disk(DiskTier.TIER_1, world.resolve("c"));
		source.readOnly(true); // (as the station has it)
		bay.disks.put(DiskStation.SOURCE, source);
		bay.disks.put(DiskStation.TARGET, target);
		assertThat(station.clone(args()).failed()).isFalse();
		assertThat(world.resolve("c/home")).isDirectory();
		source.retire();
		target.retire();
	}

	@Test
	void anActionIsItsMethodAndGetSaysWhatsInIt() {
		bay.disks.put(DiskStation.TARGET, disk("g"));
		assertThat(station.wipe(args()).failed()).isFalse();
		@SuppressWarnings("unchecked")
		Map<String, Object> state = (Map<String, Object>) station.get(args()).first();
		assertThat(state).doesNotContainKey(DiskStation.SOURCE);
		@SuppressWarnings("unchecked")
		Map<String, Object> target = (Map<String, Object>) state.get(DiskStation.TARGET);
		assertThat(target).containsEntry("mode", Disk.RAW).containsEntry("tier", 1L);
		bay.disks.get(DiskStation.TARGET).retire();
	}

	/** A disk with no label says so with "" (the field dictionary's: every field always there), one with one its label. */
	@Test
	void aDiskWithNoLabelHasAnEmptyOne() {
		bay.disks.put(DiskStation.TARGET, disk("h"));
		Disk named = disk("i");
		named.labelIn(new com.payonel.modularcomputers.bus.Label(() -> "backups", null));
		bay.disks.put(DiskStation.SOURCE, named);
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Object>> state = (Map<String, Map<String, Object>>) station.get(args()).first();
		assertThat(state.get(DiskStation.TARGET)).containsEntry("label", "");
		assertThat(state.get(DiskStation.SOURCE)).containsEntry("label", "backups");
		bay.disks.values().forEach(Disk::retire);
	}

	/** An action by a name it doesn't have is the host's bug (only its menu asks, and only for its buttons): nothing done. */
	@Test
	void anActionItDoesntHaveIsTheHostsBug() {
		bay.disks.put(DiskStation.TARGET, disk("j"));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> station.act("frobnicate")).isInstanceOf(IllegalArgumentException.class);
		assertThat(Files.exists(world.resolve("j").resolve("home"))).as("nothing done").isTrue();
		bay.disks.get(DiskStation.TARGET).retire();
	}
}
