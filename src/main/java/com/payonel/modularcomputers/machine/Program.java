package com.payonel.modularcomputers.machine;

/**
 * Code a CPU can execute. Today programs are Java stand-ins; once there's a Lua runtime, an
 * EEPROM's program will be its Lua code, run by that runtime.
 */
@FunctionalInterface
public interface Program {
	/** Runs with access to the motherboard: its physical bus and its virtual bus. */
	void run(Motherboard board);
}
