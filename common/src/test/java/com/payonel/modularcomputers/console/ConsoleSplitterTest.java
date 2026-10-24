package com.payonel.modularcomputers.console;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The console as a tty: it sends text to every terminal in its group, after its output processing. */
class ConsoleSplitterTest {
	/** A console over a bus of these devices, cabled (a bus, alone: no case's rules to keep). */
	private static ConsoleSplitter console(Device... devices) {
		java.util.Map<String, Device> cabled = new java.util.LinkedHashMap<>();
		for (int i = 0; i < devices.length; i++) {
			cabled.put("id:device-" + i, devices[i]);
		}
		Bus bus = new Bus();
		bus.setDevices(cabled, java.util.Map.of());
		return new ConsoleSplitter(bus);
	}

	@Test
	void withNoTerminalItHasASizeAndKeepsItsOwnCopy() {
		ConsoleSplitter console = console(new Disk(DiskTier.TIER_1));
		assertThat(console.size()).isEqualTo(ConsoleSplitter.HEADLESS);
		console.write("nobody sees this\n");
		assertThat(console.screen().row(0).strip()).isEqualTo("nobody sees this");
	}

	@Test
	void writesTheSameTextToEveryTerminal() {
		Terminal one = new Terminal(new Resolution(6, 2));
		Terminal two = new Terminal(new Resolution(6, 3));
		ConsoleSplitter console = console(new Disk(DiskTier.TIER_1), one, two);
		console.write("hi\r\nthere");
		assertThat(one.buffer().rows()).containsExactly("hi    ", "there ");
		assertThat(two.buffer().rows()).as("one size for the group: what every member can show").containsExactly("hi    ", "there ");
		assertThat(console.size()).isEqualTo(new Resolution(6, 2));
	}

	@Test
	void writesAreLiteralAsUefisOutputStringIs() {
		Terminal terminal = new Terminal(new Resolution(6, 3));
		ConsoleSplitter console = console(terminal);
		console.write("ab\ncd\r\nef");
		assertThat(terminal.buffer().rows()).containsExactly("ab    ", "  cd  ", "ef    ");
	}

	@Test
	void escapeSequencesPassThroughToTheTerminals() {
		Terminal terminal = new Terminal(new Resolution(6, 2));
		ConsoleSplitter console = console(terminal);
		console.write("\u001B[2;3H\u001B[31mx");
		assertThat(terminal.buffer().get(2, 1)).isEqualTo('x');
		assertThat(terminal.buffer().foreground(2, 1)).isEqualTo(0xCD0000);
	}

	@Test
	void aJoiningTerminalGetsTheConsoleRedrawnOnIt() {
		ConsoleSplitter console = console(new Disk(DiskTier.TIER_1));
		console.write("\u001B[32mgreen\r\nline two");
		Terminal late = new Terminal(Resolution.DEFAULT);
		late.write("old junk");
		console.redraw(late);
		assertThat(late.buffer().rows()).isEqualTo(console.screen().rows());
		assertThat(late.buffer().foregrounds()).isEqualTo(console.screen().foregrounds());
		console.write("!");
		assertThat(late.buffer().row(1).strip()).isEqualTo("line two");
	}

	@Test
	void resetBlanksItsCopy() {
		ConsoleSplitter console = console(new Terminal(Resolution.DEFAULT));
		console.write("a");
		console.reset();
		assertThat(console.screen().isBlank()).isTrue();
		assertThat(console.decoder().column()).isZero();
	}
	@Test
	void itsGroupIsOneSizeTheLargestEveryMemberCanShowAndAProgramMayResizeIt() {
		Terminal small = new Terminal(new Resolution(8, 4));
		Terminal big = new Terminal(new Resolution(12, 6));
		java.util.Map<String, com.payonel.modularcomputers.bus.Device> devices = new java.util.LinkedHashMap<>();
		devices.put("id:small", small);
		devices.put("id:big", big);
		Bus bus = new Bus();
		bus.setDevices(devices, java.util.Map.of());
		ConsoleSplitter console = new ConsoleSplitter(bus);
		assertThat(console.size()).isEqualTo(new Resolution(8, 4));
		assertThat(big.buffer().resolution()).as("set to the console's size").isEqualTo(new Resolution(8, 4));
		console.write("\u001B[8;2;5t");
		assertThat(List.of(console.size(), small.buffer().resolution(), big.buffer().resolution())).as("everyone follows")
			.containsOnly(new Resolution(5, 2));
		console.write("\u001B[8;0;0t");
		assertThat(big.buffer().resolution()).as("the group's largest, not its own").isEqualTo(new Resolution(8, 4));
		assertThat(console.getSize(new com.payonel.modularcomputers.runtime.Arguments(List.of())).values()).containsExactly(8L, 4L, 8L, 4L);
		console.unbind(new com.payonel.modularcomputers.runtime.Arguments(List.of("id:small")));
		big.write("\u001B[8;0;0t");
		assertThat(big.buffer().resolution()).as("the group's largest is its own now").isEqualTo(new Resolution(12, 6));
		assertThat(console.size()).as("the console keeps its size (a program may change it)").isEqualTo(new Resolution(8, 4));
	}
}
