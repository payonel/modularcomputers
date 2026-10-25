package com.payonel.modularcomputers.device;

import static com.payonel.modularcomputers.device.Terminal.MouseAction.MOVE;
import static com.payonel.modularcomputers.device.Terminal.MouseAction.PRESS;
import static com.payonel.modularcomputers.device.Terminal.MouseAction.RELEASE;
import static com.payonel.modularcomputers.device.Terminal.MouseAction.WHEEL;
import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.VtDecoder;
import org.junit.jupiter.api.Test;

/**
 * A terminal's mouse, as xterm's: nothing goes to the program until it asks (DECSET 9, 1000, 1002,
 * 1003), each mode reporting what it says, in X10's bytes or SGR's encoding (1006), with the
 * modifiers held; a reset, or the mode turned off, ends it; a save keeps it.
 */
class TerminalMouseTest {
	private final Terminal terminal = new Terminal(Resolution.DEFAULT);

	TerminalMouseTest() {
		terminal.accepting(true);
	}

	private String sent() {
		return terminal.takeInput(Long.MAX_VALUE, true).orElse("");
	}

	/** X10's encoding of a report: ESC [ M, then the button, the column and the row, each a byte 32 past it (and 1 for the cell). */
	private static String x10(int code, int column, int row) {
		return "\u001B[M" + (char) (32 + code) + (char) (33 + column) + (char) (33 + row);
	}

	@Test
	void nothingGoesToTheProgramUntilItAsks() {
		terminal.mouse(PRESS, Terminal.MOUSE_LEFT, 4, 2, 0);
		terminal.mouse(RELEASE, Terminal.MOUSE_LEFT, 4, 2, 0);
		terminal.mouse(MOVE, 0, 5, 2, 0);
		terminal.mouse(WHEEL, Terminal.WHEEL_UP, 5, 2, 0);
		assertThat(sent()).isEmpty();
	}

	@Test
	void normalTrackingReportsPressesReleasesAndTheWheelNotMotion() {
		terminal.write("\u001B[?1000h");
		terminal.mouse(PRESS, Terminal.MOUSE_RIGHT, 4, 2, 0);
		terminal.mouse(MOVE, 0, 5, 2, 0);
		terminal.mouse(RELEASE, Terminal.MOUSE_RIGHT, 5, 2, 0);
		terminal.mouse(WHEEL, Terminal.WHEEL_DOWN, 5, 2, 0);
		assertThat(sent()).as("a release doesn't say which button (3); the wheel is 64 and 65")
			.isEqualTo(x10(2, 4, 2) + x10(3, 5, 2) + x10(65, 5, 2));
	}

	@Test
	void buttonEventTrackingReportsDragsAndAnyEventEveryMotion() {
		terminal.write("\u001B[?1002h");
		terminal.mouse(MOVE, 0, 1, 1, 0);
		terminal.mouse(PRESS, Terminal.MOUSE_LEFT, 1, 1, 0);
		terminal.mouse(MOVE, 0, 2, 1, 0);
		terminal.mouse(RELEASE, Terminal.MOUSE_LEFT, 2, 1, 0);
		assertThat(sent()).as("a drag: motion (32) and the button held").isEqualTo(x10(0, 1, 1) + x10(32, 2, 1) + x10(3, 2, 1));
		terminal.write("\u001B[?1003h");
		terminal.mouse(MOVE, 0, 3, 1, 0);
		assertThat(sent()).as("motion with nothing held: 32 and 3").isEqualTo(x10(35, 3, 1));
	}

	@Test
	void sgrEncodingSaysWhichButtonWasReleasedAndTheModifiers() {
		terminal.write("\u001B[?1000h\u001B[?1006h");
		terminal.mouse(PRESS, Terminal.MOUSE_LEFT, 9, 4, Terminal.MOUSE_CONTROL | Terminal.MOUSE_SHIFT);
		terminal.mouse(RELEASE, Terminal.MOUSE_LEFT, 9, 4, 0);
		terminal.mouse(WHEEL, Terminal.WHEEL_UP, 9, 4, 0);
		assertThat(sent()).isEqualTo("\u001B[<20;10;5M\u001B[<0;10;5m\u001B[<64;10;5M");
	}

	@Test
	void x10TrackingReportsPressesOnlyWithoutModifiers() {
		terminal.write("\u001B[?9h");
		terminal.mouse(PRESS, Terminal.MOUSE_MIDDLE, 0, 0, Terminal.MOUSE_CONTROL);
		terminal.mouse(RELEASE, Terminal.MOUSE_MIDDLE, 0, 0, 0);
		assertThat(sent()).isEqualTo(x10(1, 0, 0));
	}

	@Test
	void turningItOffOrResettingEndsItAndASaveKeepsIt() {
		terminal.write("\u001B[?1003h\u001B[?1006h");
		VtDecoder copy = new VtDecoder(new com.payonel.modularcomputers.display.TextBuffer(Resolution.DEFAULT), reply -> {
		});
		copy.restore(terminal.decoder().state(), terminal.decoder().pendingParams(), null);
		assertThat(copy.mouseTracking()).isEqualTo(VtDecoder.MOUSE_ANY_EVENT);
		assertThat(copy.mouseSgr()).isTrue();
		terminal.write("\u001B[?1000l");
		assertThat(terminal.decoder().mouseTracking()).as("another mode's reset leaves it").isEqualTo(VtDecoder.MOUSE_ANY_EVENT);
		terminal.write("\u001B[?1003l");
		terminal.mouse(PRESS, Terminal.MOUSE_LEFT, 0, 0, 0);
		assertThat(sent()).isEmpty();
		terminal.write("\u001B[?1000h\u001Bc");
		terminal.mouse(PRESS, Terminal.MOUSE_LEFT, 0, 0, 0);
		assertThat(sent()).as("ESC c: back to no tracking").isEmpty();
	}
}
