package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.runtime.ProgramRunner;
import java.util.Optional;

/**
 * A CPU installed on a motherboard. When it gets power it loads the program from the
 * motherboard's EEPROM slot (a fixed place, like a real CPU's reset vector, so no bus walk) and
 * starts executing it.
 * <p>
 * Its only behavior of its own is the abort screen, like a kernel panic: with no EEPROM, when
 * the program crashes, or when it returns instead of halting (Linux panics when init exits), it
 * clears the console to a soft gray, writes {@code ABRT: <reason>} and halts. Power stays on.
 */
public final class Cpu {
	public static final String ABORT = "ABRT: ";
	public static final String NO_EEPROM = "no EEPROM installed";
	public static final String CRASHED = "EEPROM crashed: ";
	public static final String RETURNED = "EEPROM returned without halting";
	public static final int ABORT_FOREGROUND = 0x2E2E2E;
	public static final int ABORT_BACKGROUND = 0xBDBDBD;

	/** Starts the EEPROM's program, or shows the abort screen without one. */
	public Optional<ProgramRunner> powerOn(Motherboard board) {
		var eeprom = board.eeprom();
		if (eeprom.isEmpty()) {
			abort(board, NO_EEPROM);
			return Optional.empty();
		}
		Program program = eeprom.get().program();
		return Optional.of(new ProgramRunner(program::run, "cpu"));
	}

	/** The program threw: the abort screen. */
	public void crashed(Motherboard board, Throwable error) {
		abort(board, CRASHED + error.getMessage());
	}

	/** The program returned instead of halting: the abort screen. */
	public void returned(Motherboard board) {
		abort(board, RETURNED);
	}

	/** Soft gray, cleared, then the reason: written to the console as escape sequences, like any program would. */
	private static void abort(Motherboard board, String reason) {
		ConsoleSplitter console = board.virtualBus().console();
		console.write("\u001B[0m" + color(38, ABORT_FOREGROUND) + color(48, ABORT_BACKGROUND) + "\u001B[2J\u001B[H" + ABORT + reason + "\n");
	}

	private static String color(int code, int rgb) {
		return "\u001B[" + code + ";2;" + (rgb >> 16 & 0xFF) + ";" + (rgb >> 8 & 0xFF) + ";" + (rgb & 0xFF) + "m";
	}
}
