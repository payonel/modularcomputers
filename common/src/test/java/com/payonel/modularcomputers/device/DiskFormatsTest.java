package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a Disk Station does to a disk: format it, copy files in, clone another onto it; and a read-only disk refuses every write. */
class DiskFormatsTest {
	@TempDir
	Path world;

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	private Disk disk(String name, DiskTier tier) {
		return new Disk(tier, world.resolve(name));
	}

	private static void write(Disk disk, String path, String text) {
		Disk.FileHandle file = (Disk.FileHandle) disk.open(args(path, "w")).first();
		file.write(args(text));
		file.close(args());
	}

	private static String read(Disk disk, String path) {
		Disk.FileHandle file = (Disk.FileHandle) disk.open(args(path)).first();
		String text = (String) file.read(args(4096L)).first();
		file.close(args());
		return text;
	}

	@Test
	void aReadOnlyDiskReadsAndRefusesEveryWrite() {
		Disk disk = disk("ro", DiskTier.TIER_1);
		write(disk, "/a", "kept");
		disk.readOnly(true);
		assertThat(read(disk, "/a")).isEqualTo("kept");
		assertThat(disk.open(args("/a", "w")).error()).isEqualTo(Disk.READ_ONLY);
		assertThat(disk.open(args("/a", "a")).error()).isEqualTo(Disk.READ_ONLY);
		assertThat(disk.mkdir(args("/d")).error()).isEqualTo(Disk.READ_ONLY);
		assertThat(disk.remove(args("/a")).error()).isEqualTo(Disk.READ_ONLY);
		assertThat(disk.rename(args("/a", "/b")).error()).isEqualTo(Disk.READ_ONLY);
		assertThat(disk.symlink(args("/a", "/l")).error()).isEqualTo(Disk.READ_ONLY);
		assertThat(disk.setMode(args(Disk.RAW)).error()).isEqualTo(Disk.READ_ONLY);
		assertThat(disk.format(Disk.HFS).error()).isEqualTo(Disk.READ_ONLY);
		assertThat(read(disk, "/a")).as("nothing changed").isEqualTo("kept");
		disk.retire();
	}

	@Test
	void formattingClearsItsFilesFromTheHost() {
		Disk disk = disk("f", DiskTier.TIER_1);
		write(disk, "/a", "gone");
		assertThat(disk.format(Disk.HFS).failed()).isFalse();
		assertThat(disk.list(args("/")).first()).isEqualTo(List.of());
		assertThat(world.resolve("f/a")).doesNotExist();
		assertThat(disk.format(Disk.RAW).failed()).isFalse();
		assertThat(disk.getMode(args()).first()).isEqualTo(Disk.RAW);
		disk.retire();
	}

	@Test
	void filesCopiedInAreItsAndAnRawDiskTakesNone() {
		Disk disk = disk("c", DiskTier.TIER_1);
		write(disk, "/mine", "stays");
		assertThat(disk.copyIn(folder -> {
			try {
				Files.createDirectories(folder.resolve("bin"));
				Files.writeString(folder.resolve("bin/tool"), "new");
				return List.of(folder.resolve("bin"), folder.resolve("bin/tool"));
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}).failed()).isFalse();
		assertThat(read(disk, "/bin/tool")).isEqualTo("new");
		assertThat(read(disk, "/mine")).as("everything else left").isEqualTo("stays");
		disk.format(Disk.RAW);
		assertThat(disk.copyIn(folder -> List.of()).failed()).as("no filesystem to copy into").isTrue();
		disk.retire();
	}

	@Test
	void aCloneIsTheSourcesModeAndContentsAndRefusedWhenItDoesntFit() {
		Disk source = disk("src", DiskTier.TIER_1);
		source.mkdir(args("/dir"));
		write(source, "/dir/a", "copied");
		Disk target = disk("dst", DiskTier.TIER_2);
		write(target, "/old", "replaced");
		assertThat(target.cloneFrom(source).failed()).isFalse();
		assertThat(read(target, "/dir/a")).isEqualTo("copied");
		assertThat(world.resolve("dst/old")).as("what it held is gone").doesNotExist();

		source.format(Disk.RAW);
		source.write(args(100L, "bytes"));
		assertThat(target.cloneFrom(source).failed()).isFalse();
		assertThat(target.getMode(args()).first()).isEqualTo(Disk.RAW);
		assertThat(target.read(args(100L, 5L)).first()).isEqualTo("bytes");
		assertThat(target.read(args(105L, 3L)).first()).as("zeros past what was cloned").isEqualTo("\0\0\0");
		assertThat(world.resolve("dst.img")).as("the image ends at the source's last byte").hasSize(105);

		Disk small = disk("small", DiskTier.TIER_1);
		Disk big = disk("big", DiskTier.TIER_2);
		big.format(Disk.RAW);
		write(small, "/keep", "kept");
		assertThat(small.cloneFrom(big).error()).as("a raw tier 2 is bigger than a tier 1").startsWith("doesn't fit");
		assertThat(read(small, "/keep")).as("nothing changed").isEqualTo("kept");
		for (Disk disk : List.of(source, target, small, big)) {
			disk.retire();
		}
	}
}
