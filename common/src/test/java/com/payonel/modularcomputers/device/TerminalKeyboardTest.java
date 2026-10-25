package com.payonel.modularcomputers.device;

import static com.payonel.modularcomputers.device.TerminalKeyboard.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.display.Resolution;
import org.junit.jupiter.api.Test;

/** What a terminal's keyboard sends, by the mode programs set: a VT100's by default, the kitty protocol's on request. */
class TerminalKeyboardTest {
	private final Terminal terminal = new Terminal(Resolution.DEFAULT);

	TerminalKeyboardTest() {
		terminal.accepting(true);
	}

	private String sent() {
		return terminal.takeInput(Long.MAX_VALUE, true).orElse("");
	}

	@Test
	void byDefaultItsAVt100sKeyboard() {
		terminal.key(UP, 0, PRESS);
		terminal.key(ENTER, 0, PRESS);
		terminal.key('c', CONTROL, PRESS);
		terminal.key(LEFT, CONTROL, PRESS);
		terminal.key(DELETE, 0, PRESS);
		terminal.key(F1, 0, PRESS);
		terminal.key(TAB, SHIFT, PRESS);
		terminal.type("é");
		assertThat(sent()).as("typed text goes as UTF-8").isEqualTo("\u001B[A\r\u0003\u001B[1;5D\u001B[3~\u001BOP\u001B[Z\u00C3\u00A9");
	}

	@Test
	void itSendsNothingForReleasesModifiersOrTextKeys() {
		terminal.key(UP, 0, RELEASE);
		terminal.key(LEFT_SHIFT, 0, PRESS);
		terminal.key('a', 0, PRESS); // (its text comes typed)
		assertThat(sent()).isEmpty();
	}

	@Test
	void cursorKeysApplicationModeChangesTheArrows() {
		terminal.write("\u001B[?1h");
		terminal.key(UP, 0, PRESS);
		terminal.key(HOME, 0, PRESS);
		terminal.write("\u001B[?1l");
		terminal.key(UP, 0, PRESS);
		assertThat(sent()).isEqualTo("\u001BOA\u001BOH\u001B[A");
	}

	@Test
	void theKittyProtocolReportsEveryKeyUpAndDown() {
		terminal.write("\u001B[>11u"); // disambiguate, event types, every key
		terminal.key(LEFT_SHIFT, 0, PRESS);
		terminal.key('a', SHIFT, PRESS);
		terminal.key('a', SHIFT, REPEAT);
		terminal.key('a', SHIFT, RELEASE);
		terminal.key(UP, 0, RELEASE);
		terminal.key(ESCAPE, 0, PRESS);
		terminal.type("A"); // (not sent: the key events say it all)
		assertThat(sent()).isEqualTo("\u001B[57441u\u001B[97;2u\u001B[97;2:2u\u001B[97;2:3u\u001B[1;1:3A\u001B[27u");
	}

	@Test
	void disambiguatingAloneLeavesTextAndSendsCtrlAsEscapeCodes() {
		terminal.write("\u001B[>1u");
		terminal.key('c', CONTROL, PRESS);
		terminal.key(ESCAPE, 0, PRESS);
		terminal.key(ENTER, 0, PRESS);
		terminal.type("x");
		assertThat(sent()).isEqualTo("\u001B[99;5u\u001B[27u\rx");
	}

	@Test
	void flagsArePushedPoppedAndAskedFor() {
		terminal.write("\u001B[>1u\u001B[>8u\u001B[?u");
		assertThat(sent()).isEqualTo("\u001B[?8u");
		terminal.write("\u001B[<u\u001B[?u");
		assertThat(sent()).isEqualTo("\u001B[?1u");
		terminal.write("\u001B[<u\u001B[?u");
		assertThat(sent()).isEqualTo("\u001B[?0u");
		terminal.write("\u001B[=3;1u\u001B[=1;3u\u001B[?u"); // set 3, then take 1 off
		assertThat(sent()).isEqualTo("\u001B[?2u");
	}

	@Test
	void plainCsiUStillRestoresTheCursor() {
		terminal.write("ab\u001B[sxyz\u001B[uQ");
		assertThat(terminal.buffer().row(0)).startsWith("abQyz");
	}

	@Test
	void aPasteIsItsTextAsTypedNewLinesAsEnterAndNothingElse() {
		terminal.paste("one\r\ntwo\nthree\u001B[201~\u0007\tfour");
		assertThat(sent()).as("no ESC, no bell; tabs stay").isEqualTo("one\rtwo\rthree[201~\tfour");
	}

	@Test
	void withBracketedPasteOnAPasteComesWrapped() {
		terminal.write("\u001B[?2004h");
		terminal.paste("a\nb");
		assertThat(sent()).isEqualTo("\u001B[200~a\rb\u001B[201~");
		terminal.write("\u001B[?2004l");
		terminal.paste("c");
		assertThat(sent()).as("off again").isEqualTo("c");
	}
}
