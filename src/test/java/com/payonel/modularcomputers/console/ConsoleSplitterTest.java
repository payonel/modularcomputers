package com.payonel.modularcomputers.console;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The console as a tty: it sends text to every terminal in its group, after its output processing. */
class ConsoleSplitterTest {
	private static ConsoleSplitter console(Device... devices) {
		return new ConsoleSplitter(new Bus(List.of(devices)));
	}

	@Test
	void withNoTerminalItHasASizeAndKeepsItsOwnCopy() {
		ConsoleSplitter console = console(new Disk());
		assertThat(console.size()).isEqualTo(ConsoleSplitter.HEADLESS);
		console.write("nobody sees this\n");
		assertThat(console.screen().row(0).strip()).isEqualTo("nobody sees this");
	}

	@Test
	void writesTheSameTextToEveryTerminal() {
		Terminal one = new Terminal(new Resolution(6, 2));
		Terminal two = new Terminal(new Resolution(6, 3));
		ConsoleSplitter console = console(new Disk(), one, two);
		console.write("hi\nthere");
		assertThat(one.buffer().rows()).containsExactly("hi    ", "there ");
		assertThat(two.buffer().rows()).containsExactly("hi    ", "there ", "      ");
		assertThat(console.size()).isEqualTo(new Resolution(6, 2));
	}

	@Test
	void onlcrTurnsNewlinesIntoCrLfAndCanBeTurnedOff() {
		Terminal terminal = new Terminal(new Resolution(6, 3));
		ConsoleSplitter console = console(terminal);
		assertThat(console.modes()).containsEntry(ConsoleSplitter.ONLCR, true);
		console.write("ab\n");
		assertThat(console.setMode(ConsoleSplitter.ONLCR, false)).isTrue();
		console.write("cd\nef");
		assertThat(terminal.buffer().rows()).containsExactly("ab    ", "cd    ", "  ef  ");
		assertThat(console.setMode("nope", true)).isFalse();
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
		ConsoleSplitter console = console(new Disk());
		console.write("\u001B[32mgreen\nline two");
		Terminal late = new Terminal(Resolution.DEFAULT);
		late.write("old junk");
		console.redraw(late);
		assertThat(late.buffer().rows()).isEqualTo(console.screen().rows());
		assertThat(late.buffer().foregrounds()).isEqualTo(console.screen().foregrounds());
		console.write("!");
		assertThat(late.buffer().row(1).strip()).isEqualTo("line two");
	}

	@Test
	void resetBlanksItsCopyAndRestoresTheModes() {
		ConsoleSplitter console = console(new Terminal(Resolution.DEFAULT));
		console.setMode(ConsoleSplitter.ONLCR, false);
		console.write("a");
		console.reset();
		assertThat(console.screen().isBlank()).isTrue();
		assertThat(console.decoder().column()).isZero();
		assertThat(console.modes()).containsEntry(ConsoleSplitter.ONLCR, true);
	}
}
