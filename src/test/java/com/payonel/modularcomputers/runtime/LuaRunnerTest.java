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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A Lua CPU runs the EEPROM's text as Lua, through the motherboard API as the global {@code board}. */
class LuaRunnerTest {
	private final Terminal screen = new Terminal(Resolution.DEFAULT);
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of())));

	LuaRunnerTest() {
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", screen);
		machine.setDevices(devices);
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
	}

	private void boot(String lua) {
		machine.board().setEeprom(new Eeprom(lua));
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
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
		assertThat(row(0)).isEqualTo("hello world");
		assertThat(screen.buffer().rows().getFirst().strip()).as("on the terminal too").isEqualTo("hello world");
		assertThat(machine.runner()).as("halted").isEmpty();
		assertThat(machine.isPowered()).as("power stays on").isTrue();
	}

	@Test
	void invokeAnswersAsPcallDoes() {
		boot("""
			local ok, w, h = board.invoke("id:screen", "getSize")
			local bad, why = board.invoke("id:nothing", "write", "x")
			board.invoke("console", "write", tostring(ok) .. " " .. w .. "x" .. h .. " " .. tostring(bad) .. " " .. why)
			board.halt()
			""");
		assertThat(row(0)).startsWith("true 50x16 false ");
	}

	@Test
	void theBoardReportsAsTables() {
		boot("""
			local devices = board.list()
			local hw = board.hardware()
			board.invoke("console", "write", #devices .. " " .. devices[1].type .. " " .. hw.cpu.tier .. " " .. board.uptime())
			board.halt()
			""");
		assertThat(row(0)).isEqualTo("1 terminal 3 0");
	}

	@Test
	void aLoopThatNeverCallsIsFrozenEachTickAndGoesOn() {
		boot("""
			local n = 0
			for i = 1, 1000000 do n = n + 1 end
			board.invoke("console", "write", "counted " .. n)
			board.halt()
			""");
		assertThat(machine.runner()).as("preempted: still running").isPresent();
		for (int i = 0; i < 100 && machine.runner().isPresent(); i++) {
			machine.tick();
		}
		assertThat(row(0)).isEqualTo("counted 1000000");
	}

	@Test
	void pastTheTicksCallsItYieldsUntilTheNext() {
		boot("""
			local n = 0
			for i = 1, 50 do n = n + 1; board.invoke("id:screen", "getSize") end
			board.invoke("console", "write", "done " .. n)
			board.halt()
			""");
		assertThat(machine.runner()).as("20 calls a tick").isPresent();
		machine.tick();
		assertThat(row(0)).as("50 calls and a write: the third turn's").isEmpty();
		machine.tick();
		assertThat(row(0)).isEqualTo("done 50");
		assertThat(machine.runner()).isEmpty();
	}

	@Test
	void waitingForAnInterruptYieldsToTheHost() {
		boot("""
			local pending = board.waitForInterrupt(1, "id:screen")
			board.invoke("console", "write", "woke " .. #pending)
			board.halt()
			""");
		assertThat(machine.runner().orElseThrow().state()).isEqualTo(Runner.State.WAITING);
		for (int i = 0; i < 21; i++) {
			machine.tick();
		}
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
		assertThat(machine.runner().orElseThrow().state()).isEqualTo(Runner.State.WAITING);
		machine.tick();
		assertThat(machine.runner().orElseThrow().state()).as("nothing yet").isEqualTo(Runner.State.WAITING);
		screen.press("x");
		machine.tick();
		assertThat(row(0)).isEqualTo("woke console x");
	}

	@Test
	void aSyscallFreezesTheWholeChainFromWhereverItIs() {
		boot("""
			local inner = coroutine.wrap(function()
			  local t = {3, 1, 2}
			  table.sort(t, function(a, b) board.waitForInterrupt(0.1) return a < b end) -- no yield could get out of here
			  coroutine.yield("plain yield")
			  return t[1] .. t[2] .. t[3]
			end)
			local first = inner()
			local sorted = inner()
			local n = 0
			for i = 1, 1000000 do n = n + 1 end -- frozen by its budget along the way
			board.invoke("console", "write", first .. " " .. sorted .. " " .. n)
			board.halt()
			""");
		for (int i = 0; i < 200 && machine.runner().isPresent(); i++) {
			machine.tick();
		}
		assertThat(row(0)).isEqualTo("plain yield 123 1000000");
		assertThat(machine.runner()).as("halted").isEmpty();
	}

	@Test
	void aHaltFromInsideASortStopsIt() {
		boot("""
			pcall(table.sort, {3, 1, 2}, function(a, b) board.halt() return a < b end)
			board.invoke("console", "write", "still here")
			""");
		assertThat(machine.runner()).as("halted").isEmpty();
		assertThat(row(0)).as("nothing after the halt").isEmpty();
		assertThat(machine.isPowered()).isTrue();
	}

	@Test
	void theProgramCantReachTheFirmwareOrTheHost() {
		boot("""
			board.invoke("console", "write", tostring(debug) .. tostring(host) .. tostring(rawget(_G, "host")) .. tostring(load("return host")()))
			board.halt()
			""");
		assertThat(row(0)).isEqualTo("nilnilnilnil");
	}

	@Test
	void aLoopInAMetamethodIsPreemptedToo() {
		boot("""
			local evil = setmetatable({}, {__index = function() while true do end end})
			board.invoke("console", "write", evil.x)
			""");
		for (int i = 0; i < 5; i++) {
			machine.tick(); // each tick returns: its slice is spent, and it's frozen
		}
		assertThat(machine.runner().orElseThrow().state()).isEqualTo(Runner.State.RUNNING);
	}

	@Test
	void anErrorIsTheAbortScreen() {
		boot("local x = nil\nx.y = 1\n");
		assertThat(row(0)).startsWith(Cpu.ABORT + Cpu.CRASHED + "eeprom:2:");
	}

	@Test
	void fallingOffTheEndIsAPanic() {
		boot("local x = 1\n");
		assertThat(row(0)).isEqualTo(Cpu.ABORT + Cpu.RETURNED);
	}

	@Test
	void textThatIsntLuaShowsLuasMessage() {
		boot("com.payonel.modularcomputers.machine.Mcos\n");
		assertThat(row(0)).startsWith(Cpu.ABORT + Cpu.EEPROM + "eeprom:");
	}

	@Test
	void noFilesOrProcessesOfTheServer() {
		boot("""
			board.invoke("console", "write", tostring(io) .. tostring(os) .. tostring(require) .. tostring(dofile) .. tostring(print))
			board.halt()
			""");
		assertThat(row(0)).isEqualTo("nilnilnilnilnil");
	}

	@Test
	void theJavaCpuCantRunLua() {
		machine.board().setCpu(new Cpu(CpuTier.TIER_3, Architecture.JAVA));
		boot("board.halt()\n");
		assertThat(row(0)).isEqualTo(Cpu.ABORT + Cpu.EXEC_FORMAT);
	}
}
