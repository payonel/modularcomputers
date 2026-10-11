package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.machine.Program;

/**
 * Programs the tests flash onto a computer's EEPROM, so they never depend on the mod's own
 * stand-in firmware, which changes as it's used to try out other things.
 */
public final class TestPrograms {
	public static final String GREETING = "hello world";
	/** Prints the greeting to the console, then halts. */
	public static final Program HELLO = board -> {
		board.invoke("console", "print", GREETING);
		board.halt();
	};

	private TestPrograms() {
	}

	/** Reflashes the EEPROM in the computer's slot. */
	public static void flash(AllInOneBlockEntity computer, Program program) {
		computer.machine().board().eeprom().orElseThrow().flash(program);
	}
}
