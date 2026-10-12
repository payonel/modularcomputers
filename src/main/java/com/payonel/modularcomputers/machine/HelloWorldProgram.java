package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.VirtualBus;
import com.payonel.modularcomputers.runtime.Board;

/**
 * Stand-in EEPROM contents until there's a Lua runtime, written as a mock program against the
 * motherboard API (the user changes it to try things out; tests never use it):
 * <ol>
 * <li>writes the greeting to the console;</li>
 * <li>finds every terminal on the bus. The first ("screen 0", the built-in one when there is
 * one) stays with the console; each other one is taken out of the console's group, cleared and
 * labeled "screen 1", "screen 2", ... in bus order;</li>
 * <li>then echoes typing to the console, forever: it waits for the console's interrupt (keys
 * typed at any terminal in its group, merged), reads them and writes them back to the console.
 * Enter starts a new line and Backspace rubs out, as a tty's echo does. Keys typed at an
 * unbound terminal are that terminal's own: this program doesn't wait on them.</li>
 * </ol>
 * A screen unplugged and plugged back in is a new arrival, in the console's group again, so the
 * console redraws its copy onto it.
 */
public final class HelloWorldProgram implements Program {
	public static final String GREETING = "hello world";
	public static final String LABEL = "screen ";
	private static final double WAIT_SECONDS = 3600;

	@Override
	public void run(Board board) {
		board.invoke(VirtualBus.CONSOLE, "write", GREETING + "\n");
		int screen = 0;
		for (Board.DeviceInfo device : board.list()) {
			if (!"terminal".equals(device.type())) {
				continue;
			}
			if (screen == 0) {
				screen++; // screen 0 stays bound: it shows the console
				continue;
			}
			board.invoke(VirtualBus.CONSOLE, "unbind", device.address());
			// Clear the screen, cursor home, then the label: plain text with escape sequences.
			board.invoke(device.address(), "write", "\u001B[2J\u001B[H" + LABEL + screen++);
		}
		while (true) {
			board.waitForInterrupt(WAIT_SECONDS, VirtualBus.CONSOLE);
			Object key;
			while ((key = board.invoke(VirtualBus.CONSOLE, "read").first()) != null) {
				board.invoke(VirtualBus.CONSOLE, "write", echo(key.toString()));
			}
		}
	}

	/** What a tty echoes for a key: Enter a new line, Backspace a rub-out, anything else itself. */
	private static String echo(String key) {
		return switch (key) {
			case "\r" -> "\n";
			case "\u007F" -> "\b \b";
			default -> key;
		};
	}
}
