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
			local names = {}
			for name in pairs(debug) do names[#names + 1] = name end
			table.sort(names)
			board.invoke("console", "write", table.concat(names, ",") .. " " .. tostring(host) .. " "
			  .. tostring(load("return debug")() == debug) .. " " .. tostring(debug.getregistry))
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).as("the sandbox's debug has only what a program needs for its own frozen coroutines")
			.isEqualTo("isfrozen,thaw nil true nil");
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
		local n = 0
		for i = 1, 3000000 do n = n + 1 end -- (the keys are typed meanwhile)
		local keys = ""
		while #keys < 3 do
		  board.waitForInterrupt(nil, "console")
		  local _, bytes = board.invoke("console", "read") -- every byte waiting
		  keys = keys .. (bytes or "")
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
		assertThat(runner.answered()).as("the read came from the inbox: only the write reached the server").isEqualTo(1);
	}

	@Test
	void readingPastTheLastKeyIsFreeWhenNothingIsRaised() {
		ram(3);
		boot("""
			local n = 0
			for i = 1, 3000000 do n = n + 1 end -- (both keys are typed meanwhile)
			board.waitForInterrupt(nil, "console")
			local keys = ""
			while true do
			  local _, key = board.invoke("console", "read") -- the last read finds the queue empty, the console quiet: nil
			  if key == nil then break end
			  keys = keys .. key
			end
			board.invoke("console", "write", keys)
			board.halt()
			""");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		screen.press("a");
		screen.press("b");
		untilHalted();
		assertThat(row(0)).isEqualTo("ab");
		assertThat(runner.answered()).as("only the write reached the server: interrupts are level-triggered").isEqualTo(1);
	}

	@Test
	void aWaitTheInboxCanAnswerIsFree() {
		ram(3);
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("id:redstone", card);
		machine.setDevices(devices);
		boot("""
			local n = 0
			for i = 1, 3000000 do n = n + 1 end -- (both events come meanwhile)
			board.waitForInterrupt(nil, "id:redstone") -- cold: the server thread answers, and fills the inbox
			board.invoke("id:redstone", "pull")
			board.waitForInterrupt(nil, "id:redstone") -- warm: answered on the computer's thread
			board.invoke("id:redstone", "pull")
			board.invoke("console", "write", "done")
			board.halt()
			""");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		card.sense(0, 1);
		card.sense(0, 2);
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

	/** Reads a ring's packets (between head and tail) as the program would, moving head on. */
	private static final String READ_RING = """
		local function take(ring)
		  local got = {}
		  while ring.head < ring.tail do
		    local slot = ring.head % ring.size + 1
		    got[#got + 1] = ring[slot]
		    ring[slot] = false
		    ring.head = ring.head + 1
		  end
		  return got
		end
		""";

	@Test
	void aRingFillsAsTheDeviceSendsWithNoCall() {
		boot(READ_RING + """
			local ring = board.ring("console", 4)
			local typed = ""
			while #typed < 3 do -- busy: no call, no wait; the firmware fills the ring as it resumes
			  typed = typed .. table.concat(take(ring))
			end
			board.invoke("console", "write", typed)
			board.halt()
			""");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		settle(() -> runner.state() == Runner.State.RUNNING && runner.queuedCalls() == 0);
		screen.press("a");
		screen.press("b");
		screen.press("c");
		untilHalted();
		assertThat(row(0)).isEqualTo("abc");
		assertThat(runner.answered()).as("the bytes came with no call: only the write reached the server").isEqualTo(1);
	}

	@Test
	void aRingTakesOnlyWhatItHasRoomForAndTheDeviceKeepsTheRest() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		devices.put("id:redstone", card);
		machine.setDevices(devices);
		boot(READ_RING + """
			local ring = board.ring("id:redstone", 2)
			local n = 0
			for i = 1, 3000000 do n = n + 1 end -- the events come meanwhile
			local first = ring.tail - ring.head -- full: no more than its room
			local got = #take(ring)
			while got < 5 do -- room again: the rest comes
			  for i = 1, 100000 do n = n + 1 end
			  got = got + #take(ring)
			end
			board.invoke("console", "write", first .. " " .. got)
			board.halt()
			""");
		settle(() -> machine.runner().map(runner -> runner.state() == Runner.State.RUNNING).orElse(false));
		for (int i = 1; i <= 5; i++) {
			card.sense(0, i);
		}
		untilHalted();
		assertThat(row(0)).as("2 at first (its room), then all 5").isEqualTo("2 5");
		assertThat(card.pull(new Arguments(java.util.List.of())).first()).as("none left in the card, none lost").isNull();
	}

	@Test
	void whatsOnItsWayToARingIsSavedWithTheComputer() {
		boot(READ_RING + """
			local ring = board.ring("console", 8)
			board.waitForInterrupt(0.5, "nothing") -- parked: what's typed meanwhile waits in the mailbox, not in the ring
			local typed = ""
			while #typed < 4 do
			  local n = 0
			  for i = 1, 100000 do n = n + 1 end
			  typed = typed .. table.concat(take(ring))
			end
			board.invoke("console", "write", typed)
			board.halt()
			""");
		LuaRunner runner = (LuaRunner) machine.runner().orElseThrow();
		long end = System.nanoTime() + 10_000_000_000L;
		while (runner.state() != Runner.State.WAITING) { // (no ticks: the wait's half second doesn't pass)
			assertThat(System.nanoTime()).isLessThan(end);
			Runnable task;
			while ((task = server.poll()) != null) {
				task.run();
			}
			Thread.onSpinWait();
		}
		screen.press("a");
		screen.press("b");
		assertThat(runner.mailboxed()).as("pushed, not delivered: the program is parked").isPositive();
		// Saved as the world is, the program then its devices' blocks, on the server thread: nothing comes in between.
		byte[] saved = machine.persistProgram().orElseThrow(); // the mailbox goes with it
		Map<String, Object> terminalSaved = screen.saved();
		machine.unload();
		Terminal loaded = new Terminal(Resolution.DEFAULT);
		loaded.restore(terminalSaved);
		Machine again = reloaded(loaded);
		again.resumeProgram(saved);
		settle(again, () -> again.runner().map(r -> r.state() == Runner.State.RUNNING).orElse(false));
		loaded.press("c");
		loaded.press("d");
		settle(again, () -> again.runner().isEmpty());
		assertThat(again.board().virtualBus().console().screen().row(0).strip()).as("none lost, in order").isEqualTo("abcd");
		again.pressPower();
	}

	/** Plays the server thread for {@code ticks} ticks, pressing keys at the ticks given. */
	private void typeAt(int ticks, Map<Integer, String> keys) {
		for (int tick = 0; tick < ticks && machine.runner().isPresent(); tick++) {
			Runnable task;
			while ((task = server.poll()) != null) {
				task.run();
			}
			machine.tick();
			if (keys.containsKey(tick)) {
				screen.press(keys.get(tick));
			}
			try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	@Test
	void newInputStopsABusyChildAndItsSchedulerHearsAtOnce() {
		boot(READ_RING + """
			local ring = board.ring("console", 4)
			local child = coroutine.create(function() local n = 0 while true do n = n + 1 end end)
			board.addstop(child)
			coroutine.resume(child) -- busy forever: only a stop brings the scheduler back
			while ring.head == ring.tail do
			  debug.thaw(child) -- (the timer stops it too: on until the key's in the ring)
			end
			local typed = table.concat(take(ring))
			board.invoke("console", "write", tostring(debug.isfrozen(child)) .. " " .. typed)
			board.halt()
			""");
		typeAt(200, Map.of(10, "k"));
		untilHalted();
		assertThat(row(0)).as("the child stopped where it was; the key in the ring").isEqualTo("true k");
	}

	@Test
	void aTrapStopsAtTheNearestStopAndItsThawGoesOn() {
		boot("""
			local child = coroutine.create(function()
			  local inner = coroutine.wrap(function()
			    board.trap() -- (inside a coroutine of the child's own: the whole chain stops)
			    return "back"
			  end)
			  return inner()
			end)
			board.addstop(child)
			local ok, value = coroutine.resume(child)
			local first = tostring(ok) .. " " .. tostring(value) .. " " .. tostring(debug.isfrozen(child))
			local again, result = debug.thaw(child)
			board.trap() -- no stop in the chain: returns at once
			board.invoke("console", "write", first .. " | " .. tostring(again) .. " " .. tostring(result))
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).as("the resume returned as if frozen; the thaw ran on to the end").isEqualTo("true nil true | true back");
	}

	@Test
	void aSpentSliceStopsABusyChild() {
		boot("""
			local child = coroutine.create(function()
			  local n = 0
			  for i = 1, 3000000 do n = n + 1 end -- many slices
			  return n
			end)
			board.addstop(child)
			local stops = 0
			local ok, n = coroutine.resume(child)
			while debug.isfrozen(child) do
			  stops = stops + 1
			  ok, n = debug.thaw(child)
			end
			board.invoke("console", "write", (stops > 1 and "stopped" or "ran through") .. " " .. tostring(n))
			board.halt()
			""");
		untilHalted();
		assertThat(row(0)).as("the timer: its scheduler got control back each slice").isEqualTo("stopped 3000000");
	}

	@Test
	void aStopIsntRepeatedUntilTheProgramHasTakenTheData() {
		boot(READ_RING + """
			local ring = board.ring("console", 4)
			local child = coroutine.create(function()
			  local start = board.uptime()
			  while board.uptime() < start + 40 do end -- busy for 40 ticks
			end)
			board.addstop(child)
			local stops = 0
			local ok = coroutine.resume(child)
			while debug.isfrozen(child) do
			  stops = stops + 1 -- (the data isn't taken: the next key isn't news)
			  debug.thaw(child)
			end
			board.invoke("console", "write", stops .. " " .. (ring.tail - ring.head))
			board.halt()
			""");
		typeAt(400, Map.of(5, "a", 20, "b"));
		untilHalted();
		assertThat(row(0)).as("one stop; both keys came").isEqualTo("1 2");
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
		assertThat(runner.answered()).as("the read went to the device (taking every byte), and the write").isEqualTo(2);
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
			local n = 0
			for i = 1, 3000000 do n = n + 1 end -- (the keys and events come meanwhile)
			board.waitForInterrupt(nil, "console", "id:redstone") -- cold: every key in one read, and 2 events
			board.invoke("console", "write", "waited")
			board.waitForInterrupt(1000, "nothing")
			""");
		for (int i = 1; i <= 5; i++) {
			screen.press("k" + i);
			card.sense(0, i);
		}
		settle(() -> row(0).equals("waited"));
		int events = 0;
		while (card.pull(new Arguments(java.util.List.of())).first() != null) {
			events++;
		}
		assertThat(events).as("the card keeps what its full queue had no room for").isEqualTo(3);
		assertThat(screen.interruptPending()).as("the keys went in one read: a packet of the console's room").isFalse();
	}

	@Test
	void theInboxIsSavedWithTheComputerAndTheDevicesKeepTheRest() {
		ram(2);
		boot("""
			board.waitForInterrupt(nil, "console") -- takes what's typed by then
			board.invoke("console", "write", "busy")
			local n = 0
			for i = 1, 3000000 do n = n + 1 end -- busy, and saved meanwhile
			local keys = ""
			while #keys < 4 do
			  board.waitForInterrupt(nil, "console")
			  local _, bytes = board.invoke("console", "read")
			  keys = keys .. (bytes or "")
			end
			board.invoke("console", "write", keys)
			board.halt()
			""");
		for (String key : new String[] {"a", "b", "c", "d"}) {
			screen.press(key);
		}
		settle(() -> row(0).equals("busy"));
		// Saved as the world is: the program (its inbox), then the terminal's block (what came after).
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
