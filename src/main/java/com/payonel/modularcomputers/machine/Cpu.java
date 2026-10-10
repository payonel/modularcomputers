package com.payonel.modularcomputers.machine;

/**
 * When the CPU gets power it loads the program from the motherboard's EEPROM slot (a fixed
 * place, like a real CPU's reset vector, so no bus walk) and executes it. Its only behavior of
 * its own is reporting a failed boot, like a kernel panic: with no EEPROM, or when the program
 * crashes, it writes the error to the console on the virtual bus and halts. Power stays on.
 */
public final class Cpu {
	public static final String NO_EEPROM = "no EEPROM installed";
	public static final String CRASHED = "EEPROM crashed: ";

	private Cpu() {
	}

	public static void powerOn(Motherboard board) {
		var eeprom = board.eeprom();
		if (eeprom.isEmpty()) {
			board.virtualBus().console().print(NO_EEPROM);
			return;
		}
		try {
			eeprom.get().program().run(board);
		} catch (RuntimeException e) {
			board.virtualBus().console().print(CRASHED + e.getMessage());
		}
	}
}
