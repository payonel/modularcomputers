package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.console.ConsoleSplitter;

/**
 * A CPU installed on a motherboard. When it gets power it loads the program from the
 * motherboard's EEPROM slot (a fixed place, like a real CPU's reset vector, so no bus walk) and
 * executes it.
 * <p>
 * Its only behavior of its own is the abort screen, like a kernel panic: with no EEPROM, or
 * when the program crashes, it clears the console to a soft gray, writes {@code ABRT: <reason>}
 * and halts. Power stays on.
 */
public final class Cpu {
	public static final String ABORT = "ABRT: ";
	public static final String NO_EEPROM = "no EEPROM installed";
	public static final String CRASHED = "EEPROM crashed: ";
	public static final int ABORT_FOREGROUND = 0x2E2E2E;
	public static final int ABORT_BACKGROUND = 0xBDBDBD;

	public void powerOn(Motherboard board) {
		var eeprom = board.eeprom();
		if (eeprom.isEmpty()) {
			abort(board, NO_EEPROM);
			return;
		}
		try {
			eeprom.get().program().run(board);
		} catch (RuntimeException e) {
			abort(board, CRASHED + e.getMessage());
		}
	}

	private static void abort(Motherboard board, String reason) {
		ConsoleSplitter console = board.virtualBus().console();
		console.setColors(ABORT_FOREGROUND, ABORT_BACKGROUND);
		console.clear();
		console.print(ABORT + reason);
	}
}
