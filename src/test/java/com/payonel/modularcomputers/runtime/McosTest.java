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
 * mcos (Lua), booted from a disk through the bootloader, as a player would: commands typed at its
 * shell. Tests bring their own programs and check what they do (files on the disks, the computer
 * halting or running), never what mcos says.
 */
class McosTest {
	private final Terminal screen = new Terminal(Resolution.DEFAULT);
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(java.util.List.of())));
	private final ConcurrentLinkedQueue<Runnable> server = new ConcurrentLinkedQueue<>();
	private final Map<String, Device> devices = new LinkedHashMap<>();

	@TempDir
	Path world;

	McosTest() {
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
	private Path root() {
		return world.resolve("mcos-disk");
	}

	/** Puts a file on the boot disk before it boots. */
	private void file(String path, String text) {
		try {
			Path at = root().resolve(path);
			Files.createDirectories(at.getParent());
			Files.writeString(at, text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A second disk, at sata:1, with its folder. */
	private Path dataDisk() {
		Path folder = world.resolve("data-disk");
		devices.put("sata:1", new Disk(DiskTier.TIER_1, folder));
		return folder;
	}

	private void boot() {
		LuaScripts.copyTree("mcos", root());
		devices.put("sata:0", new Disk(DiskTier.TIER_1, root()));
		machine.setDevices(new LinkedHashMap<>(devices));
		machine.board().setEeprom(new Eeprom(LuaScripts.read("eeprom/boot")));
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
		untilIdle();
	}

	/** Plays the server thread until {@code done}: its asked-for tasks, and a tick a millisecond. */
	private void settle(BooleanSupplier done) {
		long end = System.nanoTime() + 20_000_000_000L;
		while (!done.getAsBoolean()) {
			assertThat(System.nanoTime()).as(() -> "settled in time; the screen:\n" + screen()).isLessThan(end);
			Runnable task;
			while ((task = server.poll()) != null) {
				task.run();
			}
			machine.tick();
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/** What the console shows (to explain a failure: tests never assert on it). */
	private String screen() {
		StringBuilder text = new StringBuilder();
		var console = machine.board().virtualBus().console().screen();
		for (int row = 0; row < Resolution.DEFAULT.rows(); row++) {
			text.append(console.row(row).stripTrailing()).append('\n');
		}
		return text.toString();
	}

	/** Until mcos has read every key and idles, waiting for more (still running). */
	private void untilIdle() {
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		settle(() -> machine.runner().isEmpty() || (!screen.hasKeys() && runner.state() == Runner.State.WAITING));
		assertThat(machine.runner()).as(() -> "still running; the screen:\n" + screen()).isPresent();
	}

	private void untilHalted() {
		settle(() -> machine.runner().isEmpty());
		assertThat(machine.board().virtualBus().console().screen().row(0)).doesNotStartWith("ABRT");
	}

	private void type(String keys) {
		for (int i = 0; i < keys.length(); i++) {
			screen.press(keys.substring(i, i + 1));
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Until a file is there with this text (a file appears when it's opened, its text when it's closed). */
	private void untilFile(Path file, String text) {
		settle(() -> Files.isRegularFile(file) && read(file).equals(text));
	}

	@Test
	void haltHalts() {
		boot();
		type("halt\r");
		untilHalted();
	}

	@Test
	void ctrlCDropsTheLineAndTheShellReadsOn() {
		boot();
		type("hal\u0003"); // what was typed is dropped with the ^C
		untilIdle();
		type("t\r"); // not "halt": the line was flushed
		untilIdle();
		type("halt\r");
		untilHalted();
	}

	@Test
	void theShellEndingIsStartedAgain() {
		boot();
		type("exit\r");
		untilIdle();
		type("\u0004"); // end of file ends it too
		untilIdle();
		type("halx\u007ft\r"); // Backspace erases
		untilHalted();
	}

	@Test
	void poweroffHaltsABoardWithNoSoftOff() {
		boot();
		type("poweroff\r");
		untilHalted();
	}

	@Test
	void aProgramMakesDirectoriesAndWritesReadsAndRenamesFiles() {
		file("t.lua", """
			local fs = require("fs")
			assert(fs.mkdir("/out/deep", true))
			local f = assert(io.open("/out/deep/a.txt", "w"))
			f:write("hello ", 42, "\\n")
			f:close()
			local g = assert(io.open("/out/deep/a.txt"))
			local line = g:read("l")
			g:close()
			assert(os.rename("/out/deep/a.txt", "/out/b.txt"))
			local missing, why, errno = io.open("/out/deep/a.txt")
			local h = assert(io.open("/out/result", "w"))
			h:write(line, "|", tostring(fs.isdir("/out")), "|", #fs.list("/out/deep"), "|", errno)
			h:close()
			""");
		boot();
		type("/t.lua\r");
		untilFile(root().resolve("out/result"), "hello 42|true|0|ENOENT");
		assertThat(read(root().resolve("out/b.txt"))).isEqualTo("hello 42\n");
	}

	@Test
	void theShellPipesAndRedirects() {
		boot();
		type("echo hello  world | cat | cat > /piped; ls / > /listing; cat /nothing 2> /err; echo $? >> /piped\r");
		untilFile(root().resolve("piped"), "hello world\n1\n");
		assertThat(read(root().resolve("listing"))).contains("bin/\n").contains("dev/\n").contains("tmp/\n");
		assertThat(read(root().resolve("err"))).contains("No such file or directory");
	}

	@Test
	void anotherDiskIsMountedUnderMnt() throws Exception {
		Path data = dataDisk();
		Files.createDirectories(data);
		Files.writeString(data.resolve("hello.txt"), "from the other disk");
		boot();
		type("cp /mnt/sata/1/hello.txt /copied; echo x > /mnt/sata/1/new; mv /copied /mnt/sata/1/moved\r");
		untilFile(data.resolve("moved"), "from the other disk");
		settle(() -> !Files.exists(root().resolve("copied"))); // moved across disks: copied, then removed
		assertThat(read(data.resolve("new"))).isEqualTo("x\n");
	}

	@Test
	void aDiskAddedWhileItRunsIsMounted() {
		boot();
		Path data = dataDisk();
		machine.setDevices(new LinkedHashMap<>(devices)); // plugged in: the bus's interrupt
		untilIdle();
		type("echo hot > /mnt/sata/1/plugged\r");
		untilFile(data.resolve("plugged"), "hot\n");
	}

	@Test
	void ctrlCEndsABusyCommandAndTheKernelClosesWhatItHad() {
		file("busy.lua", """
			local f = assert(io.open("/kept", "w"))
			f:write("kept")
			f:flush() -- in the kernel's hands now (a buffer in the process is lost with it)
			io.open("/ready", "w"):close()
			while true do end
			""");
		boot();
		type("/busy.lua\r");
		untilFile(root().resolve("ready"), "");
		type("\u0003");
		untilFile(root().resolve("kept"), "kept"); // closed as the process ended: its writes are on the disk
		type("touch /alive\r"); // the shell lived on
		untilFile(root().resolve("alive"), "");
	}

	@Test
	void aBusyBackgroundProgramDoesntStopTheShell() {
		file("spin.lua", """
			io.open("/spinning", "w"):close()
			while true do end
			""");
		boot();
		type("/spin.lua &\r");
		untilFile(root().resolve("spinning"), "");
		type("touch /meanwhile\r"); // preempted: the shell and touch run while it spins
		untilFile(root().resolve("meanwhile"), "");
		type("\u0003touch /still\r"); // ^C doesn't reach it (a background job ignores it); halt ends it
		untilFile(root().resolve("still"), "");
		type("halt\r");
		untilHalted();
	}

	@Test
	void requireFindsModulesInLibAndTmpIsInMemory() {
		file("lib/greeting.lua", """
			return {text = "hi from a module"}
			""");
		file("t.lua", """
			local greeting = require("greeting")
			local f = assert(io.open("/tmp/note", "w"))
			f:write(greeting.text)
			f:close()
			local text = io.open("/tmp/note"):read("a")
			local out = assert(io.open("/result", "w"))
			out:write(text, "|", tostring(require("greeting") == greeting))
			out:close()
			""");
		boot();
		type("/t.lua; sleep 0.1; touch /slept\r");
		untilFile(root().resolve("result"), "hi from a module|true");
		untilFile(root().resolve("slept"), "");
		assertThat(root().resolve("tmp")).as("/tmp is the kernel's memory, not the disk").doesNotExist();
	}

	/** 512 KiB: mcos takes about 400 KiB with its shell and two more processes (one tier 1 stick, 256 KiB, isn't enough). */
	@Test
	void aPipelineRunsOnOneTier2Stick() {
		machine.board().setChassis(new com.payonel.modularcomputers.machine.Chassis() {
			@Override
			public int tier() {
				return 2;
			}

			@Override
			public Board.PowerInfo power() {
				return com.payonel.modularcomputers.machine.Chassis.BENCH.power();
			}

			@Override
			public java.util.List<Board.MemoryInfo> memory() {
				return java.util.List.of(new Board.MemoryInfo(0, 2, com.payonel.modularcomputers.machine.MemoryTier.TIER_2.bytes()));
			}
		});
		file("count.lua", """
			collectgarbage()
			print(math.floor(collectgarbage("count")) .. " KiB")
			""");
		boot();
		type("echo a | cat | cat > /x; /count.lua | cat > /used\r");
		untilFile(root().resolve("x"), "a\n");
		settle(() -> Files.isRegularFile(root().resolve("used")) && read(root().resolve("used")).endsWith("KiB\n"));
		System.out.println("mcos, a shell and a pipeline of two: " + read(root().resolve("used")).strip() + " in the VM");
	}

	@Test
	void ioPopenReadsACommandsOutput() {
		file("t.lua", """
			local p = assert(io.popen("echo piped through"))
			local text = p:read("a")
			local ok, how, status = p:close()
			local out = assert(io.open("/result", "w"))
			out:write(text, "|", tostring(ok), " ", how, " ", status)
			out:close()
			""");
		boot();
		type("/t.lua\r");
		untilFile(root().resolve("result"), "piped through\n|true exit 0");
	}
}
