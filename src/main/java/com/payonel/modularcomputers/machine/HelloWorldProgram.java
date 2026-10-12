package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.VirtualBus;
import com.payonel.modularcomputers.runtime.Board;
import java.util.List;

/**
 * Stand-in EEPROM contents until there's a Lua runtime, written as a mock program against the
 * motherboard API:
 * <ol>
 * <li>prints the greeting to the console (every display, and the console's own copy);</li>
 * <li>finds every terminal on the bus, takes it out of the console's group, and labels it
 * "screen 0", "screen 1", ... in bus order (a built-in one comes first);</li>
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
		for (Board.DeviceInfo device : board.list()) {
			if (!"terminal".equals(device.type())) {
				continue;
			}
			board.invoke(VirtualBus.CONSOLE, "unbind", device.address());
			List<Object> size = board.invoke(device.address(), "getResolution").values();
			board.invoke(device.address(), "fill", 1, 1, size.get(0), size.get(1), " ");
			board.invoke(device.address(), "set", 1, 1, LABEL + screen++);
		}
		board.halt();
	}
}
