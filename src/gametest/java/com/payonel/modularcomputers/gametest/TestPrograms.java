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
		board.invoke("console", "write", GREETING + "\n");
		board.halt();
	};

	/**
	 * Writes the greeting, then echoes whatever is typed at the console's terminals back to the
	 * console, forever (Enter starts a new line).
	 */
	public static final Program ECHO = board -> {
		board.invoke("console", "write", GREETING + "\n");
		board.list();
		while (true) {
			for (String source : board.waitForInterrupt(3600)) {
				if (source.equals("bus")) {
					board.list();
					continue;
				}
				Object key;
				while ((key = board.invoke(source, "read").first()) != null) {
					board.invoke("console", "write", key.equals("\r") ? "\n" : key);
				}
			}
		}
	};

	private TestPrograms() {
	}

	/** Reflashes the EEPROM in the computer's slot. */
	public static void flash(AllInOneBlockEntity computer, Program program) {
		computer.machine().board().eeprom().orElseThrow().flash(program);
	}
}
