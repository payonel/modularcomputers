package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.VirtualBus;
import com.payonel.modularcomputers.runtime.Board;

/**
 * Stand-in EEPROM contents until there's a Lua runtime: the smallest useful firmware, written
 * as a mock program against the motherboard API. It prints to the console on the virtual bus,
 * which always exists, so it needs no bus walk and no checks.
 */
public final class HelloWorldProgram implements Program {
	public static final String GREETING = "hello world";

	@Override
	public void run(Board board) {
		board.invoke(VirtualBus.CONSOLE, "print", GREETING);
	}
}
