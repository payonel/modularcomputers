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
		Eeprom eeprom = new Eeprom(LuaScripts.read("eeprom/boot"));
		devices.put("eeprom", eeprom); // (on the bus at its slot, as a board puts it)
		machine.setDevices(new LinkedHashMap<>(devices));
		machine.board().setEeprom(eeprom);
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
	void backspaceErasesBackAcrossAWrappedLine() {
		boot();
		type("x".repeat(60)); // past the screen's 50 columns: the line wraps
		untilIdle();
		type("\u007f".repeat(15)); // back across the wrap
		untilIdle();
		long shown = screen().chars().filter(c -> c == 'x').count();
		assertThat(shown).as("what's left of the line, and nothing erased still showing").isEqualTo(45);
		type("\u007f".repeat(45) + "y\r");
		untilIdle();
		assertThat(screen().chars().filter(c -> c == 'x').count()).as("all of it erased").isZero();
	}

	@Test
	void theShellEditsItsLineAndKeepsAHistory() {
		boot();
		type("echo bc > /h1\r");
		untilFile(root().resolve("h1"), "bc\n");
		type("\u001B[A"); // up: the last line again
		type("\u001B[D\u001B[D\u001B[D\u001B[D\u001B[D\u001B[D\u001B[D\u001B[D"); // left, onto "c > /h1"... back to "bc"
		type("a"); // inserted mid-line: echo abc > /h1 ... with the file name left to fix
		type("\u0005\u007f2\r"); // ^E, erase the 1, a 2: echo abc > /h2
		untilFile(root().resolve("h2"), "abc\n");
		type("stty -echo; stty > /flags; stty sane\r");
		settle(() -> Files.isRegularFile(root().resolve("flags")) && read(root().resolve("flags")).contains("-echo"));
		assertThat(read(root().resolve("flags"))).as("while the shell isn't reading, the tty is as programs set it").contains("icanon");
	}

	@Test
	void whatsTypedAheadGoesToTheCommand() {
		boot();
		type("cat > /typed\rhello\r\u0004"); // at once: the shell's line, then cat's input and its end
		untilFile(root().resolve("typed"), "hello\n");
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
	void poweroffTurnsTheComputerOff() {
		boot();
		type("poweroff\r");
		settle(() -> !machine.isPowered());
	}

	@Test
	void rebootStartsItAgain() {
		boot();
		Runner first = machine.runner().orElseThrow();
		type("touch /before; reboot\r");
		settle(() -> machine.runner().isPresent() && machine.runner().get() != first);
		assertThat(machine.isPowered()).isTrue();
		untilIdle(); // booted again, at a new shell
		assertThat(root().resolve("before")).as("written before it went down").isRegularFile();
	}

	@Test
	void devHoldsOnlyDataTheEepromIsReadAndWrittenThroughItAndLsdevAsksTheKernel() {
		dataDisk();
		boot();
		String image = machine.board().eeprom().orElseThrow().image();
		type("ls /dev > /ld; cat /dev/eeprom > /e; lsdev -i > /devs\r");
		untilFile(root().resolve("e"), image);
		settle(() -> Files.isRegularFile(root().resolve("devs")) && read(root().resolve("devs")).contains("terminal0"));
		assertThat(read(root().resolve("ld"))).as("only data: no storage or terminal files")
			.contains("console\n", "null\n", "eeprom\n").doesNotContain("storage", "terminal");
		assertThat(read(root().resolve("devs"))).contains("storage0", "storage1", "sata:1", "eeprom ", "terminal0", "id:");
		// written: one set at close, the whole of it (cp writes it in pieces)
		file("new.lua", "-- " + "x".repeat(9000) + "\nboard.halt()\n");
		type("cp /new.lua /dev/eeprom; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(machine.board().eeprom().orElseThrow().image()).isEqualTo(read(root().resolve("new.lua")));
	}

	@Test
	void aRedstoneCardsFileIsTextALineAFace() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put("id:redstone", card);
		boot();
		file("set.txt", "north 11\n");
		file("bad.txt", "not a face\n");
		type("cp /set.txt /dev/redstone0; cat /dev/redstone0 > /r; cp /bad.txt /dev/redstone0 2> /err; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(card.output(0)).as("north, as written").isEqualTo(11);
		assertThat(read(root().resolve("r"))).as("a line a face: face level powered")
			.isEqualTo("north 11 11\nsouth 0 0\neast 0 0\nwest 0 0\ntop 0 0\nbottom 0 0\n");
		assertThat(read(root().resolve("err"))).as("a line that isn't \"face powered\": the write fails").isNotEmpty();
		assertThat(card.output(0)).isEqualTo(11);
	}

	@Test
	void aSensorsFileGivesAnEventALineAndAReadWaitsForOne() {
		com.payonel.modularcomputers.device.MotionSensorCard motion = new com.payonel.modularcomputers.device.MotionSensorCard();
		devices.put("id:motion", motion);
		boot();
		type("head -n 2 /dev/motion0 > /m; touch /done\r");
		untilIdle(); // head waits: nothing has moved
		assertThat(root().resolve("done")).doesNotExist();
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		server.add(() -> motion.sense(Map.of("mob", "minecraft:zombie", "x", 0.0, "y", 0.0, "z", 2.0)));
		untilFile(root().resolve("done"), "");
		String[] lines = read(root().resolve("m")).split("\n");
		assertThat(lines).hasSize(2);
		assertThat(lines).containsExactly("minecraft:pig 1.5 0.0 -3.0", "minecraft:zombie 0.0 0.0 2.0");
	}

	@Test
	void pollWaitsForARedstoneChangeOrAPipesDataOrItsTime() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put("id:redstone", card);
		boot();
		file("p.lua", """
			local sys = require("sys")
			local function put(name, text)
			  local out = io.open(name, "w")
			  out:write(text)
			  out:close()
			end
			local r, w = sys.pipe()
			local IN = {IN = true}
			local empty = sys.poll({[r] = IN}, 0.1)
			sys.write(w, "x")
			local data = sys.poll({[r] = IN}, 0.1)
			sys.read(r)
			sys.close(w)
			local hup = sys.poll({[r] = IN}, 0.1)
			put("/pipe", tostring(next(empty) == nil) .. " " .. tostring(data[r].IN) .. " " .. tostring(hup[r].HUP))
			local f = io.open("/dev/redstone0")
			local fd = sys.fileno(f)
			local function west()
			  f:seek("set", 0)
			  return f:read("a"):match("west (%d+)")
			end
			local before = west()
			local always = sys.poll({[fd] = IN}, 0) -- an attribute: always readable
			local quiet = sys.poll({[fd] = {PRI = true}}, 0.1) -- nothing changed
			put("/started", "")
			local changed = sys.poll({[fd] = {PRI = true}})
			put("/polled", before .. " " .. west() .. " " .. tostring(always[fd].IN) .. " " .. tostring(next(quiet) == nil) .. " " .. tostring(changed[fd].PRI))
			""");
		type("lua /p.lua; touch /done\r");
		settle(() -> Files.isRegularFile(root().resolve("started")));
		server.add(() -> card.sense(3, 9)); // west: 9 comes in
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("pipe"))).as("nothing, then the pipe's data, then its writer gone").isEqualTo("true true true");
		assertThat(read(root().resolve("polled"))).as("readable at once; PRI waited for the change, which a read from the start shows")
			.isEqualTo("0 9 true true true");
	}

	@Test
	void aLineWrittenToARedstoneFileTakesEffectAtOnceSoAPulseWorks() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put("id:redstone", card);
		boot();
		file("pulse.lua", """
			local sys = require("sys")
			local f = io.open("/dev/redstone0", "w")
			f:write("north 15\\n")
			f:flush()
			local started = io.open("/started", "w")
			started:close()
			sys.sleep(0.5)
			f:write("north 0\\n")
			f:close()
			""");
		type("lua /pulse.lua; touch /done\r");
		settle(() -> Files.isRegularFile(root().resolve("started")));
		assertThat(card.output(0)).as("the first line, set with the file still open").isEqualTo(15);
		untilFile(root().resolve("done"), "");
		assertThat(card.output(0)).isZero();
	}

	@Test
	void pollWaitsOnASensorAndTheKeyboardAtOnce() {
		com.payonel.modularcomputers.device.MotionSensorCard motion = new com.payonel.modularcomputers.device.MotionSensorCard();
		devices.put("id:motion", motion);
		boot();
		file("m.lua", """
			local sys = require("sys")
			local f = io.open("/dev/motion0")
			local fd = sys.fileno(f)
			local started = io.open("/started", "w")
			started:close()
			local ready = sys.poll({[0] = {IN = true}, [fd] = {IN = true}})
			local out = io.open("/m", "w")
			out:write(tostring(ready[fd] ~= nil and ready[0] == nil), " ", f:read("l"))
			out:close()
			""");
		type("lua /m.lua; touch /done\r");
		settle(() -> Files.isRegularFile(root().resolve("started")));
		server.add(() -> motion.sense(Map.of("mob", "minecraft:pig", "x", 1.5, "y", 0.0, "z", -3.0)));
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("m"))).isEqualTo("true minecraft:pig 1.5 0.0 -3.0");
	}

	@Test
	void tailFollowsARedstoneFileShowingItAgainWhenItChanges() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put("id:redstone", card);
		boot();
		// (into /tmp: a file on a disk shows its writes at close, and tail -f keeps it open)
		type("tail -f -n 100 /dev/redstone0 > /tmp/out 2> /tmp/err &\r");
		untilIdle();
		server.add(() -> card.sense(3, 9)); // west: 9 comes in
		type("sleep 1; cp /tmp/out /o; cp /tmp/err /e\r");
		settle(() -> Files.isRegularFile(root().resolve("e")) && read(root().resolve("e")).contains("truncated"));
		String out = read(root().resolve("o"));
		assertThat(out.split("west", -1)).as("the state, then again, changed").hasSize(3);
		assertThat(out.substring(out.lastIndexOf("west"))).startsWith("west 9 0");
		assertThat(read(root().resolve("e"))).isEqualTo("tail: /dev/redstone0: file truncated\n");
		type("kill $!\r");
		untilIdle();
	}

	@Test
	void inotifyTellsOfChangesOnADiskAndInTmp() {
		boot();
		file("n.lua", """
			local sys = require("sys")
			local fd = sys.inotify()
			sys.watch(fd, "/data.txt")
			sys.watch(fd, "/tmp")
			local function write(name, text)
			  local f = io.open(name, "w")
			  f:write(text)
			  f:close()
			end
			write("/data.txt", "one")
			write("/other.txt", "unwatched")
			write("/tmp/t", "x")
			os.rename("/tmp/t", "/tmp/u")
			local seen = {}
			while sys.poll({[fd] = {IN = true}}, 0.5)[fd] do
			  seen[#seen + 1] = sys.read(fd)
			end
			write("/seen", table.concat(seen))
			""");
		type("lua /n.lua; touch /done\r");
		untilFile(root().resolve("done"), "");
		String seen = read(root().resolve("seen"));
		assertThat(seen.lines()).contains("CREATE /tmp/t", "MODIFY /tmp/t", "MOVED_FROM /tmp/t", "MOVED_TO /tmp/u", "MODIFY /data.txt")
			.noneMatch(line -> line.contains("other.txt"));
	}

	@Test
	void theKernelsViewOfTheBusCatchesUpWithAHotPlug() {
		boot();
		type("lsdev > /before\r");
		settle(() -> Files.isRegularFile(root().resolve("before")) && read(root().resolve("before")).contains("storage0"));
		assertThat(read(root().resolve("before"))).doesNotContain("storage1");
		devices.put("sata:1", new Disk(DiskTier.TIER_1, world.resolve("late")));
		machine.setDevices(new LinkedHashMap<>(devices));
		untilIdle();
		type("lsdev > /after\r");
		settle(() -> Files.isRegularFile(root().resolve("after")) && read(root().resolve("after")).contains("storage1"));
	}

	@Test
	void lsIntoAFileIsOneNameALineAndFreeSaysTheMemory() {
		boot();
		type("ls / > /listing; free > /mem\r");
		settle(() -> Files.isRegularFile(root().resolve("mem")) && read(root().resolve("mem")).contains("Mem:"));
		assertThat(read(root().resolve("listing"))).contains("bin\n", "dev\n", "lib\n");
		assertThat(read(root().resolve("mem")).lines().toList()).hasSize(2);
	}

	@Test
	void theEditorWritesWhatsTyped() {
		boot();
		type("edit /notes.txt\r");
		untilIdle();
		type("first\rsecond\rthird"); // three lines
		type("\u001B[A\u000B"); // up, ^K: "second" cut
		type("\u001B[B\u0015"); // down (past the end: the last line), ^U: pasted above it
		type("\u000F\r"); // ^O, Enter: written under its name
		type("\u0018"); // ^X
		untilFile(root().resolve("notes.txt"), "first\nsecond\nthird\n");
		untilIdle();
		type("touch /back\r"); // the shell has the terminal again
		untilFile(root().resolve("back"), "");
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
		assertThat(read(root().resolve("listing"))).contains("bin\n").contains("dev\n").contains("tmp\n");
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

	/** One tier 1 stick (1 MiB): mcos with its shell and a pipeline. */
	@Test
	void aPipelineRunsOnOneTier1Stick() {
		machine.board().setChassis(new com.payonel.modularcomputers.machine.Chassis() {
			@Override
			public int tier() {
				return 3;
			}

			@Override
			public Board.PowerInfo power() {
				return com.payonel.modularcomputers.machine.Chassis.BENCH.power();
			}

			@Override
			public java.util.List<Board.MemoryInfo> memory() {
				return java.util.List.of(new Board.MemoryInfo(0, 1, com.payonel.modularcomputers.machine.MemoryTier.TIER_1.bytes()));
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
	void theShellExpandsAndSubstitutesAndKeepsStatus() {
		file("t.sh", """
			X=hello
			echo "$X world" > /o1
			echo $(echo inner $(echo nested)) `echo back` > /o2
			Y="a   b"
			echo $Y > /o3
			echo "$Y" >> /o3
			echo ${UNSET:-dflt} ${#X} ${X:+set} "${UNSET}" > /o4
			false; echo $? > /o5
			true && echo and >> /o5
			false || echo or >> /o5
			false && echo no >> /o5
			false && echo no || echo yes >> /o5
			! false && echo negated >> /o5
			nosuchcmd 2> /dev/null; echo $? >> /o5
			/bin 2> /dev/null; echo $? >> /o5
			cat /missing 2> /dev/null; echo $? >> /o5
			cd /tmp; pwd > /o6; cd / ; pwd >> /o6
			A=1 sh -c 'echo $A' > /o7
			echo "[$A]" >> /o7
			sh -c 'echo $0 $# $1 "$2"' name one "two words" > /o8
			echo one | cat | wc 2> /dev/null; echo $? > /o9
			""");
		boot();
		type("sh /t.sh; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("o1"))).isEqualTo("hello world\n");
		assertThat(read(root().resolve("o2"))).as(() -> screen()).isEqualTo("inner nested back\n");
		assertThat(read(root().resolve("o3"))).as("unquoted: split into fields; quoted: as it is").isEqualTo("a b\na   b\n");
		assertThat(read(root().resolve("o4"))).isEqualTo("dflt 5 set \n");
		assertThat(read(root().resolve("o5"))).as(() -> screen()).isEqualTo("1\nand\nor\nyes\nnegated\n127\n126\n1\n");
		assertThat(read(root().resolve("o6"))).isEqualTo("/tmp\n/\n");
		assertThat(read(root().resolve("o7"))).as("A=1 cmd: that command's only").isEqualTo("1\n[]\n");
		assertThat(read(root().resolve("o8"))).isEqualTo("name 2 one two words\n");
		assertThat(read(root().resolve("o9"))).as("a pipeline's status is its last command's").isEqualTo("127\n");
	}

	@Test
	void redirectionsAreMadeBeforeTheCommandIsLookedFor() {
		boot();
		type("nosuch > /made 2> /err; echo $? > /st\r");
		untilFile(root().resolve("st"), "127\n");
		assertThat(root().resolve("made")).as("made (empty), though the command never ran").isRegularFile();
		assertThat(read(root().resolve("made"))).isEmpty();
		assertThat(read(root().resolve("err"))).as("not found: said on the command's own stderr").isNotEmpty();
	}

	@Test
	void theShellGlobs() throws Exception {
		for (String name : new String[] {"a.txt", "b.txt", "c.lua", ".hidden"}) {
			file("g/" + name, "");
		}
		boot();
		type("cd /g; echo * > /r1; echo *.txt ?.lua > /r2; echo [!a]* [[:alpha:]].lua > /r3;"
			+ " echo \"*.txt\" '*' \\*.txt none* .h* > /r4; echo /g/*.lua > /r5\r");
		untilFile(root().resolve("r5"), "/g/c.lua\n");
		assertThat(read(root().resolve("r1"))).as("no dot files").isEqualTo("a.txt b.txt c.lua\n");
		assertThat(read(root().resolve("r2"))).isEqualTo("a.txt b.txt c.lua\n");
		assertThat(read(root().resolve("r3"))).isEqualTo("b.txt c.lua c.lua\n");
		assertThat(read(root().resolve("r4"))).as("quoted: no glob; no match: the word").isEqualTo("*.txt * *.txt none* .hidden\n");
	}

	@Test
	void grepHeadAndTail() {
		file("f", "alpha\nbeta\nGamma\ndelta\nepsilon\nbbc\n");
		boot();
		type("grep -n a /f > /r1; grep -c b /f > /r2; grep -v a /f > /r3; cat /f | grep -i GAMMA > /r4;"
			+ " grep -E '^b+c$' /f > /r5; grep zzz /f; echo $? > /r6; head -n 2 /f > /r7; tail -n 2 /f > /r8;"
			+ " tail -n +5 /f > /r9; grep -l eta /f /f > /r10\r");
		untilFile(root().resolve("r10"), "/f\n/f\n");
		assertThat(read(root().resolve("r1"))).isEqualTo("1:alpha\n2:beta\n3:Gamma\n4:delta\n");
		assertThat(read(root().resolve("r2"))).isEqualTo("2\n");
		assertThat(read(root().resolve("r3"))).isEqualTo("epsilon\nbbc\n");
		assertThat(read(root().resolve("r4"))).isEqualTo("Gamma\n");
		assertThat(read(root().resolve("r5"))).isEqualTo("bbc\n");
		assertThat(read(root().resolve("r6"))).as("no line selected").isEqualTo("1\n");
		assertThat(read(root().resolve("r7"))).isEqualTo("alpha\nbeta\n");
		assertThat(read(root().resolve("r8"))).isEqualTo("epsilon\nbbc\n");
		assertThat(read(root().resolve("r9"))).isEqualTo("epsilon\nbbc\n");
	}

	@Test
	void aProgramsErrorIsReportedWithItsStackTraceback() {
		file("crash.lua", """
			local function inner()
			  error("boom")
			end
			inner()
			""");
		file("refuse.lua", """
			return false, "nope"
			""");
		boot();
		type("/crash.lua 2> /err; echo $? > /st; /refuse.lua 2> /err2; echo $? >> /st\r");
		untilFile(root().resolve("st"), "1\n1\n");
		assertThat(read(root().resolve("err")))
			.contains("/crash.lua:2: boom", "stack traceback:", "/crash.lua:2: in", "/crash.lua:4: in")
			.as("the loader's own frames are cut").doesNotContain("/lib/ld.lua");
		assertThat(read(root().resolve("err2"))).isEqualTo("nope\n");
	}

	@Test
	void symbolicLinksAreKeptByTheDiskAndFollowedByTheKernel() {
		file("real/a.txt", "through\n");
		file("t.lua", """
			local sys = require("sys")
			local out = assert(io.open("/r", "w"))
			out:write(tostring(sys.readlink("/rel")), "|", sys.lstat("/rel").type, "|", sys.stat("/rel").type, "|",
			  tostring(select(3, sys.stat("/loop"))), "|", sys.realpath("/rel/a.txt"), "|", tostring(select(3, sys.link("/real/a.txt", "/hard"))))
			out:close()
			""");
		boot();
		type("ln -s real /rel; ln -s /tmp /tmplink; ln -s loop /loop; echo new > /rel/b.txt\r");
		untilIdle();
		type("echo over /tmplink > /tmplink/c; cat /tmp/c > /r2; cat /rel/a.txt > /r3; ln -s gone /dangle\r");
		untilIdle();
		type("echo made > /dangle; ls -l / > /r4; /t.lua; cd /rel; pwd > /r5; cd /\r");
		untilIdle();
		type("mkdir /d; ln -s /real /d/in; rm -r /d; rm /rel; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("real/b.txt"))).as("written through a link").isEqualTo("new\n");
		assertThat(read(root().resolve("r2"))).as("a link to another filesystem").isEqualTo("over /tmplink\n");
		assertThat(read(root().resolve("r3"))).isEqualTo("through\n");
		assertThat(read(root().resolve("gone"))).as("a link to nothing makes its target").isEqualTo("made\n");
		assertThat(read(root().resolve("r4"))).contains("dangle -> gone", "tmplink -> /tmp");
		assertThat(read(root().resolve("r"))).isEqualTo("real|link|directory|ELOOP|/real/a.txt|EPERM");
		assertThat(read(root().resolve("r5"))).as("cd through a link: the real path").isEqualTo("/real\n");
		assertThat(root().resolve("real/a.txt")).as("rm -r never follows a link").exists();
		assertThat(root().resolve("rel")).as("rm takes the link, not what it points to").doesNotExist();
		assertThat(root().resolve("real")).exists();
	}

	@Test
	void findDuTreeYesTimeUptimeMktempAndLsdev() {
		file("w/a.txt", "x".repeat(1500));
		file("w/s/b.lua", "y");
		file("w/s/.h", "");
		boot();
		type("find /w -name '*.lua' > /r1; find /w -type d > /r2; find /w -mindepth 1 -maxdepth 1 -not -name s > /r3\r");
		untilIdle();
		type("du /w > /r4; du -s -b /w > /r5; tree /w > /r6; yes hi | head -n 3 > /r7\r");
		untilIdle();
		type("time true 2> /r8; uptime > /r9; mktemp > /r10; mktemp -d -p /w dXXX > /r11; lsdev > /r12; touch /done\r");
		untilFile(root().resolve("done"), "");
		assertThat(read(root().resolve("r1"))).isEqualTo("/w/s/b.lua\n");
		assertThat(read(root().resolve("r2"))).isEqualTo("/w\n/w/s\n");
		assertThat(read(root().resolve("r3"))).isEqualTo("/w/a.txt\n");
		assertThat(read(root().resolve("r4"))).as("1 KiB blocks").isEqualTo("1\t/w/s\n3\t/w\n");
		assertThat(read(root().resolve("r5"))).isEqualTo("1501\t/w\n");
		assertThat(read(root().resolve("r6"))).isEqualTo("/w\n├── a.txt\n└── s\n    └── b.lua\n\n1 directory, 2 files\n");
		assertThat(read(root().resolve("r7"))).isEqualTo("hi\nhi\nhi\n");
		assertThat(read(root().resolve("r8"))).matches("\nreal\t0m\\d\\.\\d{3}s\n");
		assertThat(read(root().resolve("r9"))).matches(" up \\d+ min\n");
		assertThat(read(root().resolve("r10"))).matches("/tmp/tmp\\.[A-Za-z0-9]{10}\n");
		String dir = read(root().resolve("r11")).strip();
		assertThat(dir).matches("/w/d[A-Za-z0-9]{3}");
		assertThat(root().resolve(dir.substring(1))).isDirectory();
		assertThat(read(root().resolve("r12"))).startsWith("NAME").contains("storage0", "sata:0");
	}

	@Test
	void commandRunsPastAliasesAndSaysHowNamesWouldRun() {
		file("w/f", "");
		boot();
		type("alias ls='echo aliased'\r"); // (an alias counts from the next line read, as POSIX has it)
		untilIdle();
		type("ls > /r1; command ls /w > /r2; command -v ls cd sh nosuch > /r3; echo $? >> /r3\r");
		untilFile(root().resolve("r3"), "alias ls='echo aliased'\ncd\n/bin/sh\n1\n");
		assertThat(read(root().resolve("r1"))).isEqualTo("aliased\n");
		assertThat(read(root().resolve("r2"))).isEqualTo("f\n");
	}

	@Test
	void tabCompletesCommandsAndFileNamesInTheShellAndLuaNamesInTheRepl() {
		file("w/long name.txt", "spaced\n");
		file("w/other", "");
		boot();
		type("ec\t hi > /r1; cat /w/lo\t > /r2\r");
		untilFile(root().resolve("r2"), "spaced\n");
		assertThat(read(root().resolve("r1"))).as("the command completed").isEqualTo("hi\n");
		type("lua\r");
		untilIdle();
		type("t = {3, 1, k = \"v\"}\r");
		untilIdle();
		type("io.op\t'/r3', 'w'):write(require('serialization').serialize(t)):close()\r");
		untilIdle();
		type("\4");
		untilIdle();
		assertThat(read(root().resolve("r3"))).as("io.op completed to io.open(; tables serialize").isEqualTo("{3, 1, k = \"v\"}");
	}

	@Test
	void lessPagesAndQuitsAndIntoAFileIsCat() {
		file("f", "one\ntwo\n");
		boot();
		type("less /f > /r1; cat /f | less > /r2\r");
		untilFile(root().resolve("r2"), "one\ntwo\n");
		assertThat(read(root().resolve("r1"))).isEqualTo("one\ntwo\n");
		type("less /f\r");
		untilIdle();
		type("G/two\rq");
		untilIdle();
		type("touch /done\r");
		untilFile(root().resolve("done"), "");
	}

	@Test
	void manFormatsAManPage() {
		file("usr/share/man/man1/t.1", """
			.TH T 1
			.SH NAME
			t \\- a test
			.SH OPTIONS
			.TP
			.B \\-x
			does x
			.PP
			Some words that are filled into one line.
			.nf
			kept   as is
			.fi
			""");
		boot();
		type("man t > /r; man nosuch 2> /dev/null; echo $? > /st\r");
		untilFile(root().resolve("st"), "16\n");
		assertThat(read(root().resolve("r"))).contains("NAME\n       t - a test\n", "OPTIONS\n       -x     does x\n",
			"       Some words that are filled into one line.\n", "       kept   as is\n", "T(1)");
	}

	@Test
	void anAssignmentsStatusIsItsSubstitutions() {
		boot();
		type("false; x=$(false); echo $? > /r; true; y=1; echo $? >> /r\r");
		untilFile(root().resolve("r"), "1\n0\n");
	}

	@Test
	void backgroundListsAliasesBuiltinAssignmentsReadAndDollarStarAsPosixHasThem() {
		file("in1", "a:b:c\n");
		file("in2", "  one   two  three  \n");
		file("in3", "a\\ b c\n");
		boot();
		// a whole && || list in the background; y runs (x failed)
		type("true && echo a > /b1 &\r");
		untilIdle();
		type("false || echo y > /b2 &\r");
		untilFile(root().resolve("b2"), "y\n");
		untilFile(root().resolve("b1"), "a\n");
		// an alias's operators; a quoted name isn't one; a blank at its end makes the next word a name
		type("alias c='echo hi | cat > /a1'; alias e='echo '; alias w=world\r");
		untilIdle();
		type("c; 'c' 2> /dev/null; echo $? > /a2; e w > /a3\r");
		untilFile(root().resolve("a3"), "world\n");
		assertThat(read(root().resolve("a1"))).isEqualTo("hi\n");
		assertThat(read(root().resolve("a2"))).isEqualTo("127\n");
		// assignments before a builtin: only while it runs, but for a special builtin
		type("X=old; X=new cd /; echo $X > /c1; X=b export Z=1; echo $X > /c2\r");
		untilFile(root().resolve("c2"), "b\n");
		assertThat(read(root().resolve("c1"))).isEqualTo("old\n");
		// read splits on IFS (IFS=: just for it), trims blanks, the last takes the rest; \ quotes
		type("IFS=: read x y < /in1; echo \"$x|$y\" > /d1; read p q < /in2; echo \"[$p][$q]\" > /d2\r");
		untilFile(root().resolve("d2"), "[one][two  three]\n");
		assertThat(read(root().resolve("d1"))).isEqualTo("a|b:c\n");
		type("read u v < /in3; echo \"[$u][$v]\" > /d3; read -r u v < /in3; echo \"[$u][$v]\" > /d4\r");
		untilFile(root().resolve("d4"), "[a\\][b c]\n");
		assertThat(read(root().resolve("d3"))).isEqualTo("[a b][c]\n");
		// "$*" joins with IFS's first character
		type("sh -c 'IFS=:; echo \"$*\" > /f1' x a b c\r");
		untilFile(root().resolve("f1"), "a:b:c\n");
	}

	@Test
	void anUnsetParameterWithColonQuestionEndsAScriptButNotAnInteractiveShell() {
		boot();
		type("sh -c 'echo ${NOPE:?gone}; touch /e1' 2> /dev/null; echo $? > /e2\r");
		untilFile(root().resolve("e2"), "1\n");
		assertThat(root().resolve("e1")).doesNotExist();
		type("echo ${NOPE:?x} 2> /dev/null; touch /e3\r");
		untilFile(root().resolve("e3"), "");
	}

	@Test
	void grepCaseClassesWithIAndTailFollows() {
		file("log", "first\n");
		boot();
		type("echo Hello | grep -i '[[:upper:]]ello' > /g1; echo a1 | grep '[[:digit:]]' >> /g1; tail -x 2> /dev/null; echo $? > /h1\r");
		untilFile(root().resolve("h1"), "1\n");
		assertThat(read(root().resolve("g1"))).isEqualTo("Hello\na1\n");
		// (into /tmp: a file on a disk shows its writes at close, and tail -f keeps it open)
		type("tail -f /log > /tmp/out &\r");
		untilIdle();
		type("echo more >> /log; sleep 2; cp /tmp/out /h2\r");
		untilFile(root().resolve("h2"), "first\nmore\n");
		type("kill $!\r");
		untilIdle();
	}

	@Test
	void fixesFoundByTheFootprintAudit() {
		file("t.lua", """
			local sys, fs = require("sys"), require("fs")
			local out = {}
			local function say(...) out[#out + 1] = table.concat({...}, " ") end
			-- a file under a directory that isn't there: ENOENT, and nothing made (tmpfs)
			local _, _, e1 = sys.open("/tmp/nodir/file", "w")
			say("nodir", tostring(e1), tostring(sys.stat("/tmp/nodir") ~= nil))
			-- the open's mode is enforced for every file, devices too
			local r = sys.open("/dev/null", "r")
			local w = sys.open("/dev/null", "w")
			say("modes", tostring(select(3, sys.write(r, "x"))), tostring(select(3, sys.read(w, 1))))
			-- a glob's \\d is the letter d
			say("glob", tostring(string.find("d", fs.pattern([[\\d*]])) ~= nil), tostring(string.find("1", fs.pattern([[\\d*]])) ~= nil))
			-- os.remove takes a link, never what it points to
			sys.symlink("/tmp", "/lk")
			say("remove", tostring(os.remove("/lk")), tostring(sys.stat("/tmp") ~= nil))
			-- io.lines with several formats gives them all
			io.open("/two", "w"):write("a\\nb\\n"):close()
			for x, y in io.lines("/two", "l", "l") do say("lines", x, y) end
			-- an orphan that had ended is reaped (no zombie left)
			local zombies = 0
			for _, p in ipairs(sys.processes()) do
			  if p.state == "zombie" then zombies = zombies + 1 end
			end
			say("zombies", zombies)
			io.open("/r", "w"):write(table.concat(out, "\\n")):close()
			""");
		file("orphan.lua", """
			require("sys").spawn("/bin/true", {})
			require("sys").sleep(0.3) -- (the child ends: a zombie, never waited for)
			""");
		file("fd1.lua", """
			io.stderr:write(tostring(select(3, require("sys").write(1, "x"))))
			""");
		file("crash.lua", "local function f() error('boom') end f()\n");
		boot();
		type("/orphan.lua; /t.lua 2> /terr; /fd1.lua >&- 2> /r2; pwd >&- 2> /r3; echo $? >> /r3\r");
		settle(() -> Files.isRegularFile(root().resolve("r3")) && read(root().resolve("r3")).endsWith("1\n"));
		type("lua /crash.lua 2> /r4; echo \"a\\\r");
		untilIdle();
		type("b\" > /r5\r");
		untilFile(root().resolve("r5"), "ab\n");
		assertThat(root().resolve("r")).as(() -> read(root().resolve("terr"))).exists();
		assertThat(read(root().resolve("r"))).isEqualTo(String.join("\n", "nodir ENOENT false", "modes EBADF EBADF",
			"glob true false", "remove true true", "lines a b", "zombies 0"));
		assertThat(read(root().resolve("r2"))).as("n>&- closes it in the child").isEqualTo("EBADF");
		assertThat(read(root().resolve("r3"))).as("a builtin's closed stdout").contains("write error: Bad file descriptor").endsWith("1\n");
		assertThat(read(root().resolve("r4"))).as("lua's own frames are cut").contains("/crash.lua:1").doesNotContain("/bin/lua:");
	}

	@Test
	void fieldSplittingIsPosixs() {
		file("args.lua", """
			local out = assert(io.open("/r", "a"))
			out:write(select("#", ...), ":", table.concat({...}, "|"), "\\n")
			out:close()
			""");
		boot();
		type("a='a::b'; b=':a'; c='a:'; d='  x  y  '; IFS=:; /args.lua $a; /args.lua $b; /args.lua $c\r");
		untilIdle();
		type("unset IFS; /args.lua $d; /args.lua x${d}y; touch /done\r");
		untilFile(root().resolve("done"), "");
		// two IFS characters that aren't white space make an empty field; one at the start does too, one
		// at the end doesn't; white space runs separate, and at an expansion's ends end the word's field
		assertThat(read(root().resolve("r"))).isEqualTo("3:a||b\n2:|a\n1:a\n2:x|y\n4:x|x|y|y\n");
	}

	@Test
	void everyCommandHasAManPageThatRenders() {
		file("t.lua", """
			local fs = require("fs")
			local out = assert(io.open("/r", "w"))
			for _, entry in ipairs(assert(fs.list("/bin"))) do
			  local pipe = assert(io.popen("man " .. entry.name))
			  local text = pipe:read("a")
			  local ok = pipe:close()
			  if not ok or not string.find(text, "NAME\\n       " .. entry.name, 1, true) then
			    out:write(entry.name, " ")
			  end
			end
			out:write("done")
			out:close()
			""");
		boot();
		type("/t.lua\r");
		untilFile(root().resolve("r"), "done");
	}

	@Test
	void binLuaIsLuasStandaloneInterpreter() {
		file("s.lua", """
			local out = assert(io.open("/r1", "w"))
			out:write(arg[0], " ", arg[-1], " ", table.concat({...}, ","), " ", tostring(greeting))
			out:close()
			return 7
			""");
		file("lib/greet.lua", """
			return "hi"
			""");
		file("in.lua", """
			io.open("/r3", "w"):write("from stdin"):close()
			""");
		boot();
		type("lua -l greeting=greet /s.lua a b; echo $? > /st;"
			+ " lua -e 'io.open(\"/r2\", \"w\"):write(1 + 1):close()'; lua - < /in.lua;"
			+ " lua -e 'error(\"x\")' 2> /dev/null; echo $? >> /st\r");
		untilFile(root().resolve("st"), "0\n1\n");
		assertThat(read(root().resolve("r1"))).as("arg, ... and -l; what it returns is ignored")
			.isEqualTo("/s.lua greeting=greet a,b hi");
		assertThat(read(root().resolve("r2"))).isEqualTo("2");
		assertThat(read(root().resolve("r3"))).isEqualTo("from stdin");
	}

	@Test
	void aLineThatGoesOnIsReadOnAndThePromptShowsTheDirectory() {
		boot();
		type("echo 'a\rb' |\r cat > /q\r");
		untilFile(root().resolve("q"), "a\nb\n");
		type("export PS1='[\\w]# '; cd /tmp\r");
		untilIdle();
		assertThat(screen()).contains("[/tmp]#");
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
