package com.payonel.modularcomputers.machine;

/**
 * Stand-in EEPROM contents until there's a Lua runtime: the smallest useful firmware. It
 * writes to the console on the virtual bus, which always exists, so it needs no bus walk and
 * no checks.
 */
public final class HelloWorldProgram implements Program {
	public static final String GREETING = "hello world";

	@Override
	public void run(Motherboard board) {
		board.virtualBus().console().print(GREETING);
	}
}
