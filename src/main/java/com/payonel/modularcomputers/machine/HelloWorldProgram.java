package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.VirtualBus;
import com.payonel.modularcomputers.runtime.Board;
import java.util.List;

/**
 * Stand-in EEPROM contents until there's a Lua runtime, written as a mock program against the
 * motherboard API:
 * <ol>
 * <li>prints the greeting to the console (every display, and the console's own copy);</li>
 * <li>finds every display on the bus, takes it out of the console's group, and labels it
 * "screen 0", "screen 1", ... in bus order;</li>
 * <li>halts. Power stays on, and the screens keep their labels.</li>
 * </ol>
 * A screen unplugged and plugged back in is a new arrival, in the console's group again, so the
 * console redraws its copy onto it: the greeting comes back.
 */
public final class HelloWorldProgram implements Program {
	public static final String GREETING = "hello world";
	public static final String LABEL = "screen ";

	@Override
	public void run(Board board) {
		board.invoke(VirtualBus.CONSOLE, "print", GREETING);
		int screen = 0;
		for (String address : board.list()) {
			if (!"display".equals(board.type(address).orElse(null))) {
				continue;
			}
			board.invoke(VirtualBus.CONSOLE, "unbind", address);
			List<Object> size = board.invoke(address, "getResolution").values();
			board.invoke(address, "fill", 1, 1, size.get(0), size.get(1), " ");
			board.invoke(address, "set", 1, 1, LABEL + screen++);
		}
		board.halt();
	}
}
