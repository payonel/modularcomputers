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
		machine.pressPower();
		Machine again = reloaded(new Terminal(Resolution.DEFAULT));
		again.resumeProgram(saved);
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
