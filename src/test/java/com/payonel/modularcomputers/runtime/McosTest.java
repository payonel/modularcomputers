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
	void devListsTheBusAndEachFileSaysBothAddresses() {
		dataDisk();
		boot();
		type("ls /dev > /ld; cat /dev/storage* > /d\r");
		settle(() -> Files.isRegularFile(root().resolve("d")) && read(root().resolve("d")).contains("sata:1"));
		assertThat(read(root().resolve("ld"))).contains("console\n", "null\n", "storage0\n", "storage1\n", "terminal0\n");
		assertThat(read(root().resolve("d"))).contains("path: sata:0", "path: sata:1", "type: storage", "id: ");
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

	/** One tier 3 stick (1 MiB): what mcos needs for now, with its shell and a pipeline (the user: memory is optimized later). */
	@Test
	void aPipelineRunsOnOneTier3Stick() {
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
				return java.util.List.of(new Board.MemoryInfo(0, 3, com.payonel.modularcomputers.machine.MemoryTier.TIER_3.bytes()));
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
			.as("the interpreter's own frames are cut").doesNotContain("/bin/lua");
		assertThat(read(root().resolve("err2"))).isEqualTo("nope\n");
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
