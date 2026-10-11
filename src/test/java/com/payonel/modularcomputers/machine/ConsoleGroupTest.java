package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The console's group: every display and keyboard is in it until a program unbinds one. There's
 * one console buffer and no graphics card, so a screen that leaves the bus loses what it showed,
 * and one that joins (new, or plugged back in) is in the group and gets the console drawn on it.
 */
class ConsoleGroupTest {
	private final Display builtIn = new Display(Resolution.DEFAULT);
	private final Display first = new Display(Resolution.DEFAULT);
	private final Display second = new Display(Resolution.DEFAULT);
	private final Keyboard keyboard = new Keyboard();
	private final Disk disk = new Disk();
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of())));
	private final List<Object> seen = new ArrayList<>();

	ConsoleGroupTest() {
		machine.board().setCpu(new Cpu());
		plug(Map.of("id:first", first, "id:second", second));
	}

	/** The built-in display, keyboard and a disk, then these external devices. */
	private void plug(Map<String, Display> external) {
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("edp", builtIn);
		devices.put("kbd", keyboard);
		devices.put("sata:0", disk);
		devices.putAll(new TreeMap<>(external));
		machine.setDevices(devices);
	}

	private ConsoleSplitter console() {
		return machine.board().virtualBus().console();
	}

	/** Greets on the console, then takes every display for itself and labels it, then halts. */
	private void bootLabellingScreens() {
		machine.board().setEeprom(new Eeprom(board -> {
			board.invoke("console", "print", "hello world");
			int screen = 0;
			for (String address : board.list()) {
				if ("display".equals(board.type(address).orElse(null))) {
					seen.add(board.invoke("console", "unbind", address).first());
					board.invoke(address, "fill", 1, 1, 50, 16, " ");
					board.invoke(address, "set", 1, 1, "screen " + screen++);
				}
			}
			seen.add(board.invoke("console", "outputs").first());
			seen.add(board.invoke("console", "inputs").first());
			board.halt();
		}));
		machine.pressPower();
		for (int i = 0; i < 5 && machine.runner().isPresent(); i++) {
			machine.tick();
		}
		assertThat(machine.runner()).as("halted").isEmpty();
	}

	private static Arguments address(String address) {
		return new Arguments(List.<Object>of(address));
	}

	private static String top(Display display) {
		return display.buffer().row(0).strip();
	}

	@Test
	void everyDisplayAndKeyboardIsInTheGroupByDefault() {
		List<Object> group = new ArrayList<>();
		machine.board().setEeprom(new Eeprom(board -> {
			group.add(board.invoke("console", "outputs").first());
			group.add(board.invoke("console", "inputs").first());
			board.halt();
		}));
		machine.pressPower();
		assertThat(group).containsExactly(List.of("edp", "id:first", "id:second"), List.of("kbd"));
	}

	@Test
	void unboundScreensKeepWhatTheProgramWroteOnThem() {
		bootLabellingScreens();
		assertThat(seen).containsExactly(true, true, true, List.of(), List.of("kbd"));
		assertThat(List.of(top(builtIn), top(first), top(second))).containsExactly("screen 0", "screen 1", "screen 2");
		assertThat(top(builtIn)).isNotEqualTo("hello world");
		assertThat(console().screen().row(0).strip()).as("the console's own copy").isEqualTo("hello world");
	}

	@Test
	void consoleOutputSkipsUnboundScreens() {
		bootLabellingScreens();
		console().print("more");
		assertThat(first.buffer().row(1).strip()).isEmpty();
		assertThat(console().screen().row(1).strip()).isEqualTo("more");
	}

	@Test
	void aScreenPluggedBackInIsNewAndGetsTheConsoleBack() {
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
	void aNewScreenJoinsTheGroupAndShowsTheConsole() {
		bootLabellingScreens();
		Display third = new Display(Resolution.DEFAULT);
		plug(Map.of("id:first", first, "id:second", second, "id:third", third));
		assertThat(top(third)).isEqualTo("hello world");
		assertThat(List.of(top(builtIn), top(first), top(second))).containsExactly("screen 0", "screen 1", "screen 2");
	}

	@Test
	void bindingAScreenBackRedrawsTheConsoleOnIt() {
		bootLabellingScreens();
		Result bound = console().bind(address("id:second"));
		assertThat(bound.failed()).isFalse();
		assertThat(top(second)).isEqualTo("hello world");
		assertThat(top(first)).isEqualTo("screen 1");
	}

	@Test
	void onlyDisplaysAndKeyboardsOnTheBusCanBeUnbound() {
		assertThat(console().unbind(address("sata:0")).error()).isEqualTo("not a display or keyboard on the bus: sata:0");
		assertThat(console().unbind(address("id:gone")).failed()).isTrue();
		assertThat(console().unbind(address("kbd")).failed()).isFalse();
		assertThat(console().isBound("kbd")).isFalse();
	}

	@Test
	void powerOffPutsEveryDeviceBackInTheGroup() {
		bootLabellingScreens();
		machine.pressPower();
		assertThat(List.of("edp", "id:first", "id:second")).allMatch(console()::isBound);
	}
}
