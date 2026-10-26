package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Board;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The console's group: every terminal is in it until a program unbinds one. There's one console
 * buffer and no graphics card, so a terminal that leaves the bus loses what it showed, and one
 * that joins (new, or plugged back in) is in the group and gets the console drawn on it. Keys
 * typed at a terminal in the group go to the console; at an unbound one, to the terminal alone.
 */
class ConsoleGroupTest {
	private final Terminal builtIn = new Terminal(Resolution.DEFAULT);
	private final Terminal first = new Terminal(Resolution.DEFAULT);
	private final Terminal second = new Terminal(Resolution.DEFAULT);
	private final Disk disk = new Disk(DiskTier.TIER_1);
	private final Machine machine = new Machine(new Motherboard(Hardware.computer(), new Bus()));
	private final List<Object> seen = new ArrayList<>();

	private final TestBus bus;
	/** Where the built-in terminal is: a case's own screen is in an expansion slot. */
	private static final String BUILT_IN = Motherboard.exp(0);

	ConsoleGroupTest() {
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		bus = new TestBus(machine);
		bus.plug(BUILT_IN, builtIn);
		bus.disk(disk);
		plug(Map.of("id:first", first, "id:second", second));
	}

	/** These external terminals, cabled (in name order), with the built-in terminal and a disk. */
	private void plug(Map<String, Terminal> external) {
		bus.cabled(new TreeMap<>(external));
	}

	private ConsoleSplitter console() {
		return machine.board().virtualBus().console();
	}

	/** Runs a program until it parks. */
	private void boot(Program program) {
		machine.board().setEeprom(new Eeprom(program));
		machine.pressPower();
		for (int i = 0; i < 5 && !Parked.parked(machine); i++) {
			machine.tick();
		}
		assertThat(Parked.parked(machine)).as("parked").isTrue();
	}

	/** Greets on the console, then takes every terminal for itself and labels it, then halts. */
	private void bootLabellingScreens() {
		boot(board -> {
			board.invoke("console", "write", "hello world\n");
			int screen = 0;
			for (Board.DeviceInfo device : board.list()) {
				if ("terminal".equals(device.type())) {
					seen.add(board.invoke("console", "unbind", device.address()).first());
					board.invoke(device.address(), "write", "\u001B[2J\u001B[Hscreen " + screen++);
				}
			}
			seen.add(board.invoke("console", "terminals").first());
			com.payonel.modularcomputers.machine.Program.park(board);
		});
	}

	private static Arguments address(String address) {
		return new Arguments(List.<Object>of(address));
	}

	private static String top(Terminal terminal) {
		return terminal.buffer().row(0).strip();
	}

	@Test
	void everyTerminalIsInTheGroupByDefault() {
		boot(board -> {
			seen.add(board.invoke("console", "terminals").first());
			com.payonel.modularcomputers.machine.Program.park(board);
		});
		assertThat(seen).containsExactly(List.of(BUILT_IN, "id:first", "id:second"));
	}

	@Test
	void unboundTerminalsKeepWhatTheProgramWroteOnThem() {
		bootLabellingScreens();
		assertThat(seen).containsExactly(true, true, true, List.of());
		assertThat(List.of(top(builtIn), top(first), top(second))).containsExactly("screen 0", "screen 1", "screen 2");
		assertThat(console().screen().row(0).strip()).as("the console's own copy").isEqualTo("hello world");
	}

	@Test
	void consoleOutputSkipsUnboundTerminals() {
		bootLabellingScreens();
		console().write("more");
		assertThat(first.buffer().row(1).strip()).isEmpty();
		assertThat(console().screen().row(1).strip()).isEqualTo("more");
	}

	@Test
	void aTerminalPluggedBackInIsNewAndGetsTheConsoleBack() {
		bootLabellingScreens();
		plug(Map.of("id:second", second)); // first's cable is cut
		assertThat(top(first)).as("no graphics card: it lost what it showed").isEmpty();
		plug(Map.of("id:first", first, "id:second", second));
		assertThat(top(first)).isEqualTo("hello world");
		assertThat(console().isBound("id:first")).isTrue();
		assertThat(top(second)).as("still the program's").isEqualTo("screen 2");
		assertThat(console().isBound("id:second")).isFalse();
	}

	@Test
	void aNewTerminalJoinsTheGroupAndShowsTheConsole() {
		bootLabellingScreens();
		Terminal third = new Terminal(Resolution.DEFAULT);
		plug(Map.of("id:first", first, "id:second", second, "id:third", third));
		assertThat(top(third)).isEqualTo("hello world");
		assertThat(List.of(top(builtIn), top(first), top(second))).containsExactly("screen 0", "screen 1", "screen 2");
	}

	@Test
	void bindingATerminalBackRedrawsTheConsoleOnIt() {
		bootLabellingScreens();
		Result bound = console().bind(address("id:second"));
		assertThat(bound.failed()).isFalse();
		assertThat(top(second)).isEqualTo("hello world");
		assertThat(top(first)).isEqualTo("screen 1");
	}

	@Test
	void onlyTerminalsOnTheBusCanBeUnbound() {
		assertThat(console().unbind(address("sata:0")).error()).isEqualTo("not a terminal on the bus: sata:0");
		assertThat(console().unbind(address("id:gone")).failed()).isTrue();
		assertThat(console().unbind(address("id:first")).failed()).isFalse();
		assertThat(console().isBound("id:first")).isFalse();
	}

	@Test
	void powerOffPutsEveryTerminalBackInTheGroup() {
		bootLabellingScreens();
		machine.pressPower();
		assertThat(List.of(BUILT_IN, "id:first", "id:second")).allMatch(console()::isBound);
	}

	@Test
	void keysAtTerminalsInTheGroupReachTheConsoleMergedAndRaiseItsInterruptAndTheirOwn() {
		List<Object> keys = new ArrayList<>();
		machine.board().setEeprom(new Eeprom(board -> {
			board.list(); // nothing new on the bus
			keys.add(board.waitForInterrupt(60));
			keys.add(board.invoke("console", "read").first());
			keys.add(board.waitForInterrupt(60));
			keys.add(board.invoke("console", "read").first());
			keys.add(board.invoke("console", "read").first());
			com.payonel.modularcomputers.machine.Program.park(board);
		}));
		first.press("x"); // typed before power-on: dropped (no program runs to take it)
		machine.pressPower();
		first.press("a"); // each wakes the waiting program at once
		second.press("b");
		machine.tick();
		assertThat(keys).containsExactly(List.of("console", "id:first"), "a", List.of("console", "id:second"), "b", null);
	}

	@Test
	void aTerminalInTheGroupCanBeReadDirectlyRacingTheConsole() {
		List<Object> keys = new ArrayList<>();
		machine.board().setEeprom(new Eeprom(board -> {
			board.waitForInterrupt(60, "console");
			keys.add(board.invoke("console", "read").first()); // the console takes the first key
			board.waitForInterrupt(60, "id:first");
			keys.add(board.invoke("id:first", "read").first()); // the terminal's own read the second
			keys.add(board.invoke("console", "read").first()); // so the console finds nothing left
			com.payonel.modularcomputers.machine.Program.park(board);
		}));
		machine.pressPower();
		first.press("a");
		first.press("b");
		machine.tick();
		assertThat(keys).containsExactly("a", "b", null);
	}

	@Test
	void waitingOnTheConsoleIgnoresKeysAtAnUnboundTerminal() {
		List<Object> seen = new ArrayList<>();
		machine.board().setEeprom(new Eeprom(board -> {
			board.invoke("console", "unbind", "id:second");
			seen.add(board.waitForInterrupt(60, "console"));
			seen.add(board.invoke("console", "read").first());
			com.payonel.modularcomputers.machine.Program.park(board);
		}));
		machine.pressPower();
		second.press("ignored"); // its own interrupt is on, but nobody waits on it
		machine.tick();
		machine.tick();
		assertThat(seen).as("still waiting").isEmpty();
		first.press("a");
		machine.tick();
		assertThat(seen).containsExactly(List.of("console"), "a");
	}

	@Test
	void aKeyWakesAWaitingProgramAtOnceAndItsOutputIsFlushed() {
		int[] flushes = {0};
		first.setFlusher(() -> flushes[0]++);
		machine.board().setEeprom(new Eeprom(board -> {
			board.list();
			while (true) {
				board.waitForInterrupt(3600, "console");
				Object key;
				while ((key = board.invoke("console", "read").first()) != null) {
					board.invoke("console", "write", key.toString());
				}
			}
		}));
		machine.pressPower();
		int before = flushes[0];
		first.press("a"); // no tick in between: the interrupt line wakes it
		assertThat(top(first)).isEqualTo("a");
		assertThat(flushes[0]).as("pushed to players right away").isGreaterThan(before);
	}

	@Test
	void wakingOftenBuysNoMoreThanATicksCalls() {
		int[] calls = {0};
		machine.board().setEeprom(new Eeprom(board -> {
			board.list();
			while (true) {
				board.waitForInterrupt(3600, "console");
				for (int i = 0; i < 15; i++) {
					calls[0]++;
					board.invoke("console", "read");
				}
			}
		}));
		machine.pressPower();
		machine.tick();
		first.press("a");
		first.press("b");
		first.press("c");
		assertThat(calls[0]).as("20 calls this tick, however many wakes").isLessThanOrEqualTo(Machine.DEFAULT_CALLS_PER_TICK);
		machine.tick();
		assertThat(calls[0]).as("the next tick carries on").isGreaterThan(Machine.DEFAULT_CALLS_PER_TICK - 2);
	}

	@Test
	void anUnboundTerminalsKeysAreItsOwn() {
		List<Object> keys = new ArrayList<>();
		machine.board().setEeprom(new Eeprom(board -> {
			board.invoke("console", "unbind", "id:second");
			board.list();
			keys.add(board.waitForInterrupt(60));
			keys.add(board.invoke("console", "read").first());
			keys.add(board.invoke("id:second", "read").first());
			com.payonel.modularcomputers.machine.Program.park(board);
		}));
		machine.pressPower();
		machine.tick();
		second.press("z");
		machine.tick();
		assertThat(keys).containsExactly(List.of("id:second"), null, "z");
	}
}
