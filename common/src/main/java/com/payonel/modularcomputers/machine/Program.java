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
	 * A program may run for many ticks. It ends with {@link Board#poweroff()} or
	 * {@link Board#reboot()}; returning instead (or an error) is an abort.
	 */
	void run(Board board);

	/** The interrupt source a parked program waits on: none raises it. */
	String PARK = "park";

	/**
	 * Parks a program: it waits forever, the machine on and its screens as they are (a program's
	 * end that leaves the machine to look at: a test's; leaving the EEPROM would abort it).
	 */
	static void park(Board board) {
		while (true) {
			board.waitForInterrupt(Double.MAX_VALUE, PARK);
		}
	}
}
