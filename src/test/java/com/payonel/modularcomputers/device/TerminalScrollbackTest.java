package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.display.Resolution;
import org.junit.jupiter.api.Test;

/** A terminal keeps the lines that scroll off its screen, and its glass scrolls back over them. */
class TerminalScrollbackTest {
	private static final Resolution SMALL = new Resolution(10, 4);

	private final Terminal terminal = new Terminal(SMALL);

	/** Lines "line 1" .. "line n", each ending in a new line. */
	private void lines(int n) {
		StringBuilder text = new StringBuilder();
		for (int i = 1; i <= n; i++) {
			text.append("line ").append(i).append("\r\n");
		}
		terminal.write(text.toString());
	}

	private String row(int row) {
		return terminal.buffer().row(row).strip();
	}

	@Test
	void linesScrolledOffTheTopAreKept() {
		lines(10); // rows: line 8, line 9, line 10, (blank)
		assertThat(row(0)).isEqualTo("line 8");
		assertThat(terminal.scrollbackLines()).isEqualTo(7);
	}

	@Test
	void theGlassScrollsBackAndForth() {
		lines(10);
		terminal.scrollView(2);
		assertThat(row(0)).isEqualTo("line 6");
		assertThat(row(2)).isEqualTo("line 8");
		terminal.scrollView(100); // as far as there are lines
		assertThat(terminal.viewOffset()).isEqualTo(7);
		assertThat(row(0)).isEqualTo("line 1");
		terminal.scrollView(-7);
		assertThat(terminal.viewOffset()).isZero();
		assertThat(row(0)).isEqualTo("line 8");
	}

	@Test
	void outputAndKeysBringItBackToTheBottom() {
		lines(10);
		terminal.scrollView(3);
		terminal.write("x");
		assertThat(terminal.viewOffset()).isZero();
		assertThat(row(3)).as("the screen as it is, and the write on it").isEqualTo("x");
		terminal.scrollView(3);
		terminal.press("k");
		assertThat(terminal.viewOffset()).isZero();
		assertThat(row(0)).isEqualTo("line 8");
	}

	@Test
	void aSaveKeepsTheScreenNotTheScrolledView() {
		lines(10);
		terminal.scrollView(4);
		assertThat(terminal.screen().rows().get(0).strip()).isEqualTo("line 8");
	}

	@Test
	void theAlternateScreenKeepsNoLinesAndEd3ClearsThem() {
		lines(10);
		terminal.write("\u001B[?1049h");
		for (int i = 0; i < 10; i++) {
			terminal.write("full\r\n");
		}
		assertThat(terminal.scrollbackLines()).as("an editor's screen isn't history").isEqualTo(7);
		terminal.write("\u001B[?1049l\u001B[3J");
		assertThat(terminal.scrollbackLines()).isZero();
		assertThat(row(0)).as("ED 3 leaves the screen").isEqualTo("line 8");
	}

	@Test
	void powerOffForgetsThem() {
		lines(10);
		terminal.powerOff();
		assertThat(terminal.scrollbackLines()).isZero();
	}
}
