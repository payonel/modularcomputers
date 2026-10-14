package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Architecture;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A Lua CPU runs the EEPROM's text as Lua, under its firmware (machine.lua), on the computer's own
 * thread; its board calls are carried out by the server thread, which this test plays: it runs the
 * tasks the machine asks for and ticks.
 */
class LuaRunnerTest {
	private final Terminal screen = new Terminal(Resolution.DEFAULT);
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(java.util.List.of())));
	private final ConcurrentLinkedQueue<Runnable> server = new ConcurrentLinkedQueue<>();

	LuaRunnerTest() {
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		machine.setDevices(devices);
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		machine.setServer(server::add);
	}

	@AfterEach
	void powerOff() {
		if (machine.isPowered()) {
			machine.pressPower();
		}
	}

	private void boot(String lua) {
		machine.board().setEeprom(new Eeprom(lua));
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
	}

	/** Plays the server thread until {@code done}: its asked-for tasks, and a tick a millisecond. */
	private void settle(BooleanSupplier done) {
		settle(machine, done);
	}

	private void settle(Machine machine, BooleanSupplier done) {
		long end = System.nanoTime() + 10_000_000_000L;
		while (!done.getAsBoolean()) {
			assertThat(System.nanoTime()).as("settled in time").isLessThan(end);
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

	private void untilHalted() {
		settle(() -> machine.runner().isEmpty());
	}

	private String row(int row) {
		return machine.board().virtualBus().console().screen().row(row).strip();
	}

	@Test
	void theProgramsMemoryIsTheRamInstalled() {
		ram(1); // 256 KiB: all the program's, on top of what the firmware and libraries take
		boot("""
			local total = board.hardware().memoryTotal
			local big = string.rep("x", 200 * 1024) -- most of the stick: the firmware's memory isn't the program's
			local fits = #big == 200 * 1024
			big = nil
			collectgarbage()
			local used = board.hardware().memoryUsed
			local ok, err = pcall(function()
			  local t = {}
			  for i = 1, 1000000 do t[i] = string.rep("x", 1000) .. i end
			end)
			collectgarbage() -- (as stock Lua: growing a stack never collects, so the next call may fail until something does)
			board.invoke("console", "write", tostring(total == 256 * 1024) .. " " .. tostring(fits) .. " "
			  .. tostring(used > 0 and used < 64 * 1024) .. " " .. tostring(ok) .. " " .. tostring(err))
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).as("the RAM is the program's; used is counted above the baseline; past it, Lua's own error")
			.isEqualTo("true true true false not enough memory");
	}

	@Test
	void noRamNoBoot() {
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
				return java.util.List.of();
			}
		});
		boot("board.halt()");
		assertThat(row(0)).isEqualTo("ABRT: " + Cpu.NO_RAM);
	}

	@Test
	void helloWorldOnTheConsoleThenHalt() {
		boot("""
			board.invoke("console", "write", "hello world\\n")
			board.halt()
			error("halt returned")
			""");
		untilHalted();
		assertThat(row(0)).isEqualTo("hello world");
		assertThat(screen.buffer().rows().getFirst().strip()).as("on the terminal too").isEqualTo("hello world");
		assertThat(machine.isPowered()).as("power stays on").isTrue();
	}

	@Test
	void itRunsOnItsOwnThreadAndAPowerOffEndsIt() {
		boot("while true do end");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		settle(runner::alive);
		machine.pressPower();
		settle(() -> !runner.alive());
	}

	@Test
	void invokeAnswersAsPcallDoes() {
		boot("""
			local ok, w, h = board.invoke("id:screen", "getSize")
			local bad, why = board.invoke("id:nothing", "write", "x")
			board.invoke("console", "write", tostring(ok) .. " " .. w .. "x" .. h .. " " .. tostring(bad) .. " " .. why)
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).startsWith("true 50x16 false ");
	}

	@Test
	void theBoardReportsAsTables() {
		boot("""
			local devices = board.list()
			local hw = board.hardware()
			board.invoke("console", "write", #devices .. " " .. devices[1].type .. " " .. hw.cpu.tier .. " " .. math.type(board.uptime()))
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).isEqualTo("1 terminal 3 integer");
	}

	@Test
	void aLoopThatNeverCallsIsFrozenAndGoesOn() {
		boot("""
			local n = 0
			for i = 1, 1000000 do n = n + 1 end
			board.invoke("console", "write", "counted " .. n)
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).isEqualTo("counted 1000000");
	}

	@Test
	void manyCallsGoThroughTheServerThread() {
		boot("""
			for i = 1, 300 do board.invoke("id:screen", "getSize") end
			board.invoke("console", "write", "done")
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).isEqualTo("done");
	}

	@Test
	void waitingForAnInterruptSleepsUntilItsTimeIsUp() {
		boot("""
			local pending = board.waitForInterrupt(1, "id:screen")
			board.invoke("console", "write", "woke " .. #pending)
			board.halt()
			""");
		settle(() -> machine.runner().map(r -> r.state() == Runner.State.WAITING).orElse(false));
		untilHalted();
		assertThat(row(0)).isEqualTo("woke 0");
	}

	@Test
	void anInterruptWakesItWithTheSourcesPending() {
		boot("""
			local pending = board.waitForInterrupt(nil, "console") -- a bound terminal's keys come through the console
			local ok, key = board.invoke("console", "read")
			board.invoke("console", "write", "woke " .. pending[1] .. " " .. key)
			board.halt()
			""");
		settle(() -> machine.runner().map(r -> r.state() == Runner.State.WAITING).orElse(false));
		screen.press("x");
		untilHalted();
		assertThat(row(0)).isEqualTo("woke console x");
	}

	@Test
	void aSyscallFreezesTheWholeChainFromWhereverItIs() {
		boot("""
			local inner = coroutine.wrap(function()
			  local t = {3, 1, 2}
			  table.sort(t, function(a, b) board.waitForInterrupt(0.05) return a < b end) -- no yield could get out of here
			  coroutine.yield("plain yield")
			  return t[1] .. t[2] .. t[3]
			end)
			local first = inner()
			local sorted = inner()
			board.invoke("console", "write", first .. " " .. sorted)
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).isEqualTo("plain yield 123");
	}

	@Test
	void aHaltFromInsideASortStopsIt() {
		boot("""
			pcall(table.sort, {3, 1, 2}, function(a, b) board.halt() return a < b end)
			board.invoke("console", "write", "still here")
			""");
		untilHalted();
		assertThat(row(0)).as("nothing after the halt").isEmpty();
		assertThat(machine.isPowered()).isTrue();
	}

	@Test
	void theProgramCantReachTheFirmware() {
		boot("""
			board.invoke("console", "write", tostring(debug) .. tostring(host) .. tostring(load("return debug")()))
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).isEqualTo("nilnilnil");
	}

	@Test
	void aLoopInAMetamethodIsPreemptedToo() {
		boot("""
			local evil = setmetatable({}, {__index = function() while true do end end})
			board.invoke("console", "write", evil.x)
			""");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		for (int i = 0; i < 20; i++) {
			machine.tick(); // the server thread is never held up by it
		}
		assertThat(runner.state()).isEqualTo(Runner.State.RUNNING);
		machine.pressPower();
		settle(() -> !runner.alive());
	}

	@Test
	void aReflashedImageKeepsItsBytesAcrossAReboot() {
		Eeprom eeprom = new Eeprom("""
			local next = 'board.invoke("console", "write", "length " .. #"' .. string.char(233) .. '") board.halt()'
			board.invoke("eeprom", "set", next)
			board.halt()
			""");
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("eeprom", eeprom);
		machine.setDevices(devices);
		machine.board().setEeprom(eeprom);
		machine.pressPower();
		untilHalted();
		machine.pressPower();
		machine.pressPower();
		untilHalted();
		assertThat(row(0)).as("one byte, still one byte").isEqualTo("length 1");
	}

	/** The same computer after a reload: a new machine with the same CPU and a new screen, on, nothing booted. */
	private Machine reloaded(Terminal screen) {
		Machine again = new Machine(new Motherboard(Hardware.allInOne(), new Bus(java.util.List.of())));
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		again.setDevices(devices);
		again.board().setCpu(new Cpu(CpuTier.TIER_3));
		again.setServer(server::add);
		again.restorePowered(true);
		return again;
	}

	@Test
	void aComputerSavedInAWaitGoesOnWhereItWas() {
		boot("""
			local total = 0
			for i = 1, 6 do
			  total = total + i
			  board.invoke("console", "write", tostring(i))
			  board.waitForInterrupt(0.25, "id:screen")
			end
			board.invoke("console", "write", " total " .. total)
			board.halt()
			""");
		settle(() -> row(0).startsWith("12"));
		byte[] saved = machine.persistProgram().orElseThrow();
		machine.pressPower(); // this one stops; the saved one goes on elsewhere
		Terminal screen2 = new Terminal(Resolution.DEFAULT);
		Machine again = reloaded(screen2);
		again.resumeProgram(saved);
		settle(again, () -> again.runner().isEmpty());
		assertThat(again.board().virtualBus().console().screen().row(0).strip()).as("its locals and its loop, as they were").endsWith("6 total 21");
		again.pressPower();
	}

	/** A device that counts its calls. */
	public static final class Counter implements Device {
		int hits;

		@Override
		public com.payonel.modularcomputers.bus.DeviceType type() {
			return com.payonel.modularcomputers.bus.DeviceType.REDSTONE;
		}

		@Method(doc = "hit(): counts a call")
		public Result hit(Arguments args) {
			hits++;
			return Result.ok((long) hits);
		}
	}

	/** A device that answers with what it's given. */
	public static final class Echo implements Device {
		@Override
		public com.payonel.modularcomputers.bus.DeviceType type() {
			return com.payonel.modularcomputers.bus.DeviceType.REDSTONE;
		}

		@Method(doc = "echo(x): x")
		public Result echo(Arguments args) {
			return Result.ok(args.get(0));
		}
	}

	@Test
	void finalizersMakingBoardCallsNeverTakeAnotherCallsReply() {
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("id:echo", new Echo());
		machine.setDevices(devices);
		boot("""
			local bad = 0
			local finalized = 0
			for i = 1, 300 do
			  -- garbage whose finalizers make board calls of their own, at whatever syscall they land in
			  for j = 1, 5 do
			    setmetatable({}, {__gc = function()
			      local _, got = board.invoke("id:echo", "echo", "gc")
			      if got ~= "gc" then bad = bad + 1 end
			      finalized = finalized + 1
			    end})
			  end
			  local _, got = board.invoke("id:echo", "echo", i)
			  if got ~= i then bad = bad + 1 end
			end
			collectgarbage()
			board.invoke("console", "write", "bad " .. bad .. (finalized > 0 and " finalized" or " none finalized"))
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).as("every reply its own call's, and no call left spinning").isEqualTo("bad 0 finalized");
	}

	@Test
	void aSaveCarriesOutNoCallsSoTheDevicesAndTheProgramAgree() {
		Counter counter = new Counter();
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("id:counter", counter);
		machine.setDevices(devices);
		boot("""
			local _, n = board.invoke("id:counter", "hit")
			board.invoke("console", "write", "hits " .. n)
			board.halt()
			""");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		long end = System.nanoTime() + 5_000_000_000L;
		while (runner.queuedCalls() == 0) { // its call is queued; the server thread (this test) hasn't made it
			assertThat(System.nanoTime()).isLessThan(end);
			Thread.onSpinWait();
		}
		byte[] saved = machine.persistProgram().orElseThrow();
		assertThat(counter.hits).as("the save made no call: the device is as the save found it").isZero();
		machine.unload();
		Machine again = new Machine(new Motherboard(Hardware.allInOne(), new Bus(java.util.List.of())));
		again.setDevices(devices);
		again.board().setCpu(new Cpu(CpuTier.TIER_3));
		again.setServer(server::add);
		again.restorePowered(true);
		again.resumeProgram(saved);
		settle(again, () -> again.runner().isEmpty());
		assertThat(counter.hits).as("the saved program makes its call, once").isEqualTo(1);
		assertThat(again.board().virtualBus().console().screen().row(0).strip()).isEqualTo("hits 1");
		again.pressPower();
	}

	@Test
	void aSnapshotTakenAsASaveStartsIsntUsedOnceTheComputerWasHandedMore() {
		Counter counter = new Counter();
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("id:counter", counter);
		machine.setDevices(devices);
		boot("""
			local x = 0
			for i = 1, 2000000 do x = x + 1 end
			local _, n = board.invoke("id:counter", "hit")
			for i = 1, 3000000 do x = x + 1 end
			board.invoke("console", "write", "hits " .. n)
			board.halt()
			""");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		settle(runner::alive);
		LuaRunner.prepareAll(); // a save starts: every computer takes its snapshot, here before its call
		settle(() -> counter.hits == 1); // no save happened after all, and its call was made
		byte[] saved = machine.persistProgram().orElseThrow(); // the old snapshot would miss the call
		machine.unload();
		Machine again = new Machine(new Motherboard(Hardware.allInOne(), new Bus(java.util.List.of())));
		again.setDevices(devices);
		again.board().setCpu(new Cpu(CpuTier.TIER_3));
		again.setServer(server::add);
		again.restorePowered(true);
		again.resumeProgram(saved);
		settle(again, () -> again.runner().isEmpty());
		assertThat(again.board().virtualBus().console().screen().row(0).strip()).isEqualTo("hits 1");
		assertThat(counter.hits).as("the call was made once").isEqualTo(1);
		again.pressPower();
	}

	@Test
	void aWaitSavedPartwayEndsOnItsDeadlineOnTheSavedClock() {
		boot("board.waitForInterrupt(4, \"nothing\") board.halt()"); // 80 ticks
		settle(() -> machine.uptime() >= 40 && machine.runner().map(r -> r.state() == Runner.State.WAITING).orElse(false));
		byte[] saved = machine.persistProgram().orElseThrow();
		long at = machine.uptime();
		machine.unload(); // frozen: its clock stands still
		Machine again = reloaded(new Terminal(Resolution.DEFAULT));
		again.restoreUptime(at);
		again.resumeProgram(saved);
		settle(again, () -> again.runner().isEmpty());
		assertThat(again.uptime()).as("it ended when it would have, not a whole wait later").isBetween(80L, 86L);
		again.pressPower();
	}

	@Test
	void aComputerSavedInTheMiddleOfALoopGoesOn() {
		boot("""
			local n = 0
			for i = 1, 3000000 do n = n + 1 end
			board.invoke("console", "write", "counted " .. n)
			board.halt()
			""");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		settle(runner::alive);
		byte[] saved = machine.persistProgram().orElseThrow(); // at its next slice
		long baseline = runner.memory().baseline();
		machine.pressPower();
		Machine again = reloaded(new Terminal(Resolution.DEFAULT));
		again.resumeProgram(saved);
		assertThat(baseline).as("measured at boot").isPositive();
		assertThat(((LuaRunner) again.runner().orElseThrow()).memory().baseline()).as("kept with the save, not measured again").isEqualTo(baseline);
		settle(again, () -> again.runner().isEmpty());
		assertThat(again.board().virtualBus().console().screen().row(0).strip()).isEqualTo("counted 3000000");
		again.pressPower();
	}

	@Test
	void anOpenFileIsKeptWithTheComputersSavedState(@org.junit.jupiter.api.io.TempDir java.nio.file.Path world) throws Exception {
		com.payonel.modularcomputers.device.Disk disk = new com.payonel.modularcomputers.device.Disk(
			com.payonel.modularcomputers.device.DiskTier.TIER_1, world.resolve("disk-a"));
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("sata:0", disk);
		machine.setDevices(devices);
		boot("""
			local _, file = board.invoke("sata:0", "open", "/log.txt", "w")
			board.invoke(file, "write", "before ")
			board.waitForInterrupt(0.5, "nothing")
			board.invoke(file, "write", "after")
			board.invoke(file, "close")
			board.halt()
			""");
		settle(() -> machine.runner().map(r -> r.state() == Runner.State.WAITING).orElse(false));
		byte[] saved = machine.persistProgram().orElseThrow();
		machine.pressPower(); // the original stops: its open file's writes are lost with its power
		Machine again = reloaded(new Terminal(Resolution.DEFAULT));
		Map<String, Device> devices2 = new LinkedHashMap<>();
		devices2.put("sata:0", new com.payonel.modularcomputers.device.Disk(com.payonel.modularcomputers.device.DiskTier.TIER_1, world.resolve("disk-a")));
		again.setDevices(devices2);
		again.resumeProgram(saved);
		settle(again, () -> again.runner().isEmpty());
		assertThat(java.nio.file.Files.readString(world.resolve("disk-a/log.txt"))).as("its handle, writes and all, came back").isEqualTo("before after");
		again.pressPower();
	}

	@Test
	void theBootloaderRunsTheFirstDisksInit(@org.junit.jupiter.api.io.TempDir java.nio.file.Path world) throws Exception {
		java.nio.file.Path folder = world.resolve("boot-disk");
		java.nio.file.Files.createDirectories(folder);
		java.nio.file.Files.writeString(folder.resolve("init.lua"), "board.invoke('console', 'write', 'booted from ' .. ...) board.halt()");
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("sata:0", new com.payonel.modularcomputers.device.Disk(com.payonel.modularcomputers.device.DiskTier.TIER_1, world.resolve("empty-disk")));
		devices.put("sata:1", new com.payonel.modularcomputers.device.Disk(com.payonel.modularcomputers.device.DiskTier.TIER_1, folder));
		machine.setDevices(devices);
		boot(com.payonel.modularcomputers.machine.LuaScripts.read("eeprom/boot"));
		untilHalted();
		assertThat(row(0)).isEqualTo("booted from sata:1");
	}

	/** Boots mcos (Lua) from a disk with its files, through the bootloader: tests check what it does, never what it says. */
	private LuaRunner bootMcos(java.nio.file.Path world) throws Exception {
		java.nio.file.Path folder = world.resolve("mcos-disk");
		com.payonel.modularcomputers.machine.LuaScripts.copyTree("mcos", folder);
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("sata:0", new com.payonel.modularcomputers.device.Disk(com.payonel.modularcomputers.device.DiskTier.TIER_1, folder));
		machine.setDevices(devices);
		boot(com.payonel.modularcomputers.machine.LuaScripts.read("eeprom/boot"));
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		untilIdle(runner);
		return runner;
	}

	/** Until the program has read every key and waits for more (still running). */
	private void untilIdle(LuaRunner runner) {
		settle(() -> machine.runner().isEmpty() || (!screen.hasKeys() && runner.state() == Runner.State.WAITING));
		assertThat(machine.runner()).as("still running").isPresent();
	}

	private void type(String keys) {
		for (int i = 0; i < keys.length(); i++) {
			screen.press(keys.substring(i, i + 1));
		}
	}

	@Test
	void mcosHaltsWhenToldTo(@org.junit.jupiter.api.io.TempDir java.nio.file.Path world) throws Exception {
		bootMcos(world);
		type("halt\r");
		untilHalted();
		assertThat(row(0)).doesNotStartWith("ABRT");
	}

	@Test
	void ctrlCEndsTheShellAndTheKernelStartsAnother(@org.junit.jupiter.api.io.TempDir java.nio.file.Path world) throws Exception {
		LuaRunner runner = bootMcos(world);
		type("hal\u0003"); // what was typed is dropped with the ^C
		untilIdle(runner);
		type("t\r"); // not "halt": the line was flushed
		untilIdle(runner);
		type("halt\r"); // a new shell reads it
		untilHalted();
		assertThat(row(0)).doesNotStartWith("ABRT");
	}

	@Test
	void theShellEndingIsStartedAgain(@org.junit.jupiter.api.io.TempDir java.nio.file.Path world) throws Exception {
		LuaRunner runner = bootMcos(world);
		type("poweroff\r");
		untilIdle(runner);
		type("\u0004"); // end of file ends it too
		untilIdle(runner);
		type("halx\u007ft\r"); // Backspace erases
		untilHalted();
		assertThat(row(0)).doesNotStartWith("ABRT");
	}

	@Test
	void theBootloaderWithNoBootableDiskSaysSo() {
		boot(com.payonel.modularcomputers.machine.LuaScripts.read("eeprom/boot"));
		untilHalted();
		assertThat(row(0)).isEqualTo("boot: no bootable disk");
	}

	/** RAM sticks of {@code tier} in the case, for the board's ring. */
	private void ram(int tier) {
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
				return java.util.List.of(new Board.MemoryInfo(0, tier, com.payonel.modularcomputers.machine.MemoryTier.values()[tier - 1].bytes()));
			}
		});
	}

	private static final String TYPED = """
		board.waitForInterrupt(nil, "console")
		local keys = ""
		for i = 1, 3 do
		  local _, key = board.invoke("console", "read")
		  keys = keys .. tostring(key)
		end
		board.invoke("console", "write", keys)
		board.halt()
		""";

	@Test
	void aWaitFillsTheInboxSoReadsNeedNoCall() {
		ram(3); // 4 a device
		boot(TYPED);
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		screen.press("a");
		screen.press("b");
		screen.press("c");
		untilHalted();
		assertThat(row(0)).isEqualTo("abc");
		assertThat(runner.answered()).as("the reads came from the inbox: only the write reached the server").isEqualTo(1);
	}

	@Test
	void aWaitTheInboxCanAnswerIsFree() {
		ram(3);
		boot("""
			board.waitForInterrupt(nil, "console") -- cold: the server thread answers, and fills the inbox
			board.invoke("console", "read")
			board.waitForInterrupt(nil, "console") -- warm: answered on the computer's thread
			board.invoke("console", "read")
			board.invoke("console", "write", "done")
			board.halt()
			""");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		screen.press("a");
		screen.press("b");
		untilHalted();
		assertThat(row(0)).isEqualTo("done");
		assertThat(runner.handedOver()).as("one wait and the write reached the server thread").isEqualTo(2);
	}

	@Test
	void aKeyTypedWhileTheProgramIsBusyIsNewsItsNextWaitSeesAtOnce() {
		ram(3);
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("id:redstone", card);
		machine.setDevices(devices);
		boot("""
			board.list() -- (so the bus has no interrupt raised: the key alone is news)
			board.waitForInterrupt(nil, "console", "id:redstone") -- cold: the card's events into the inbox
			board.invoke("console", "write", "busy")
			local n = 0
			for i = 1, 5000000 do n = n + 1 end -- a key is typed meanwhile
			local ready = board.waitForInterrupt(nil, "console", "id:redstone") -- the inbox has the card's events, but the key is news
			table.sort(ready)
			board.invoke("console", "write", " " .. table.concat(ready, ","))
			board.halt()
			""");
		for (int i = 1; i <= 3; i++) {
			card.sense(0, i);
		}
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		settle(() -> row(0).equals("busy"));
		screen.press("k"); // the line rises: the board's interrupt status shows it at once
		long end = System.nanoTime() + 10_000_000_000L;
		while (runner.state() != Runner.State.WAITING && runner.queuedCalls() == 0) { // no server thread meanwhile: no tick, no turn
			assertThat(System.nanoTime()).isLessThan(end);
			Thread.onSpinWait();
		}
		assertThat(runner.state()).as("its wait went to the server thread, not answered from the inbox without the key").isEqualTo(Runner.State.WAITING);
		untilHalted();
		assertThat(row(0)).isEqualTo("busy console,id:redstone");
	}

	@Test
	void withOnlyTier1RamTheresNoInboxAndEachReadIsACall() {
		ram(1);
		boot(TYPED);
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		screen.press("a");
		screen.press("b");
		screen.press("c");
		untilHalted();
		assertThat(row(0)).isEqualTo("abc");
		assertThat(runner.answered()).as("each read went to the device").isEqualTo(4);
	}

	@Test
	void eachDeviceHasItsOwnRoomInTheInbox() {
		ram(2); // 2 a device
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("id:redstone", card);
		machine.setDevices(devices);
		boot("""
			board.waitForInterrupt(nil, "console", "id:redstone") -- cold: takes 2 keys and 2 events
			board.invoke("console", "read") -- from the inbox
			board.invoke("console", "read") -- the keys' queue is empty now
			board.waitForInterrupt(nil, "console") -- cold for the keys: tops them up; the card's queue is full
			board.invoke("console", "write", "waited")
			board.waitForInterrupt(1000, "nothing")
			""");
		for (int i = 1; i <= 5; i++) {
			screen.press("k" + i);
			card.sense(0, i);
		}
		settle(() -> row(0).equals("waited"));
		int keys = 0;
		while (screen.interruptPending()) {
			screen.read(new Arguments(java.util.List.of()));
			keys++;
		}
		int events = 0;
		while (card.pull(new Arguments(java.util.List.of())).first() != null) {
			events++;
		}
		assertThat(events).as("the card keeps what its full queue had no room for").isEqualTo(3);
		assertThat(keys).as("left in the terminal: 5, less 2, less 2 more").isEqualTo(1);
	}

	@Test
	void theInboxIsSavedWithTheComputerAndTheDevicesKeepTheRest() {
		ram(2);
		boot("""
			board.waitForInterrupt(nil, "console") -- takes 2 keys
			board.invoke("console", "write", "busy")
			local n = 0
			for i = 1, 3000000 do n = n + 1 end -- busy, and saved meanwhile
			local keys = ""
			for i = 1, 4 do
			  local _, key = board.invoke("console", "read")
			  keys = keys .. tostring(key)
			end
			board.invoke("console", "write", keys)
			board.halt()
			""");
		for (String key : new String[] {"a", "b", "c", "d"}) {
			screen.press(key);
		}
		settle(() -> row(0).equals("busy"));
		// Saved as the world is: the program (its inbox, 2 keys), then the terminal's block (2 more keys).
		byte[] saved = machine.persistProgram().orElseThrow();
		Map<String, Object> terminalSaved = screen.saved();
		machine.unload();
		assertThat(screen.interruptPending()).as("no program runs now: the live terminal drops what it held").isFalse();
		Terminal loaded = new Terminal(Resolution.DEFAULT);
		loaded.restore(terminalSaved); // its block loads
		Machine again = reloaded(loaded);
		again.resumeProgram(saved); // goes on: what its devices kept for it stays
		settle(again, () -> again.runner().isEmpty());
		assertThat(again.board().virtualBus().console().screen().row(0).strip()).as("none lost, in order").isEqualTo("abcd");
		again.pressPower();
	}

	@Test
	void anErrorIsTheAbortScreen() {
		boot("local x = nil\nx.y = 1\n");
		untilHalted();
		assertThat(row(0)).startsWith(Cpu.ABORT + Cpu.CRASHED + "eeprom:2:");
	}

	@Test
	void fallingOffTheEndIsAPanic() {
		boot("local x = 1\n");
		untilHalted();
		assertThat(row(0)).isEqualTo(Cpu.ABORT + Cpu.RETURNED);
	}

	@Test
	void textThatIsntLuaShowsLuasMessage() {
		boot("com.payonel.modularcomputers.machine.Mcos\n");
		untilHalted();
		assertThat(row(0)).startsWith(Cpu.ABORT + Cpu.EEPROM + "eeprom:");
	}

	@Test
	void noFilesOrProcessesOfTheServer() {
		boot("""
			board.invoke("console", "write", tostring(io) .. tostring(os) .. tostring(require) .. tostring(dofile) .. tostring(print))
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).isEqualTo("nilnilnilnilnil");
	}

	@Test
	void theJavaCpuCantRunLua() {
		machine.board().setCpu(new Cpu(CpuTier.TIER_3, Architecture.JAVA));
		boot("board.halt()\n");
		assertThat(row(0)).isEqualTo(Cpu.ABORT + Cpu.EXEC_FORMAT);
	}
}
