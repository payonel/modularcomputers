package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.LuaScripts;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.TestBus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A computer booting mcos from a disk, as a player would, for tests to type commands at: its
 * bus (a case's), its server thread (played by {@link #settle}) and its disks' folders. Tests
 * bring their own programs and check what they do (files on the disks, the computer halting or
 * running), never what mcos says.
 */
abstract class McosHarness {
	final Terminal screen = new Terminal(Resolution.DEFAULT);
	final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus()));
	final ConcurrentLinkedQueue<Runnable> server = new ConcurrentLinkedQueue<>();
	final Map<String, Device> devices = new LinkedHashMap<>();

	@TempDir
	Path world;

	McosHarness() {
		devices.put("id:screen", screen);
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		machine.setServer(server::add);
	}

	@AfterEach
	void powerOff() {
		if (machine.isPowered()) {
			machine.pressPower();
		}
	}

	/** The boot disk's folder (mcos's files, and the test's). */
	Path root() {
		return world.resolve("mcos-disk");
	}

	/** Puts a file on the boot disk before it boots. */
	void file(String path, String text) {
		try {
			Path at = root().resolve(path);
			Files.createDirectories(at.getParent());
			Files.writeString(at, text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A second disk, at sata:1, with its folder. */
	Path dataDisk() {
		Path folder = world.resolve("data-disk");
		devices.put("sata:1", new Disk(DiskTier.TIER_1, folder));
		return folder;
	}

	void boot() {
		boot(() -> {
		});
	}

	/** Boots, with {@code configure} changing mcos's own files on the disk first (its /etc). */
	void boot(Runnable configure) {
		LuaScripts.copyTree("mcos", root());
		configure.run();
		devices.put("sata:0", new Disk(DiskTier.TIER_1, root()));
		Eeprom eeprom = new Eeprom(LuaScripts.read("eeprom/boot"));
		devices.put("eeprom", eeprom); // (on the bus at its slot, as a board puts it)
		TestBus.of(machine, devices);
		machine.board().setEeprom(eeprom);
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
		untilIdle();
	}

	/** Plays the server thread until {@code done}: its asked-for tasks, and a tick a millisecond. */
	void settle(BooleanSupplier done) {
		long end = System.nanoTime() + 20_000_000_000L;
		while (!done.getAsBoolean()) {
			assertThat(System.nanoTime()).as(() -> "settled in time; the screen:\n" + screen()).isLessThan(end);
			Runnable task;
			while ((task = server.poll()) != null) {
				task.run();
			}
			machine.tick();
			for (Runnable tick : ticks) {
				tick.run(); // (another computer's, and what a test does each tick)
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/** What else happens each tick: other computers' ticks, a test's own doings. */
	final java.util.List<Runnable> ticks = new java.util.ArrayList<>();

	/** What the console shows (to explain a failure: tests never assert on it). */
	String screen() {
		StringBuilder text = new StringBuilder();
		var console = machine.board().virtualBus().console().screen();
		for (int row = 0; row < Resolution.DEFAULT.rows(); row++) {
			text.append(console.row(row).stripTrailing()).append('\n');
		}
		return text.toString();
	}

	/** Until mcos has read every key and idles, waiting for more (still running). */
	void untilIdle() {
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		settle(() -> machine.runner().isEmpty() || (!screen.hasKeys() && runner.state() == Runner.State.WAITING));
		assertThat(machine.runner()).as(() -> "still running; the screen:\n" + screen()).isPresent();
	}

	void untilHalted() {
		settle(() -> machine.runner().isEmpty());
		assertThat(machine.board().virtualBus().console().screen().row(0)).doesNotStartWith("ABRT");
	}

	void type(String keys) {
		for (int i = 0; i < keys.length(); i++) {
			screen.press(keys.substring(i, i + 1));
		}
	}

	static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Until a file is there with this text (a file appears when it's opened, its text when it's closed). */
	void untilFile(Path file, String text) {
		settle(() -> Files.isRegularFile(file) && read(file).equals(text));
	}

	/** A program of the manual (docs/examples), as a player reads it. */
	static String example(String name) {
		try {
			Path dir = Path.of("").toAbsolutePath();
			while (!Files.isDirectory(dir.resolve("docs/examples"))) {
				dir = dir.getParent();
			}
			return Files.readString(dir.resolve("docs/examples/" + name + ".lua"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
