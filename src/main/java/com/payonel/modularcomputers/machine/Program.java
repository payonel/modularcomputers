package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.runtime.Board;

/**
 * Code a CPU can execute. Today programs are Java stand-ins; once there's a Lua runtime, an
 * EEPROM's program will be its Lua code, run by that runtime.
 */
@FunctionalInterface
public interface Program {
	/**
	 * Runs on the CPU, using the motherboard API: the devices on the bus and the virtual ones.
	 * A program may run for many ticks; returning halts the CPU (power stays on).
	 */
	void run(Board board);
}
