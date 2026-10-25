package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.runtime.LuaRunner;
import com.payonel.modularcomputers.runtime.ProgramRunner;
import com.payonel.modularcomputers.runtime.Runner;
import java.util.Optional;

/**
 * A CPU installed on a motherboard. When it gets power it loads the program from the
 * motherboard's EEPROM slot (a fixed place, like a real CPU's reset vector, so no bus walk) and
 * starts executing it.
 * <p>
 * The EEPROM holds plain text, which the CPU runs as a Lua chunk ({@link LuaRunner}).
 * <p>
 * Its only behavior of its own is the abort screen, like a kernel panic: with no EEPROM, when
 * the program crashes, or when it returns instead of halting (Linux panics when init exits), it
 * clears the console to a soft gray, writes {@code ABRT: <reason>} and halts. Power stays on.
 */
public final class Cpu {
	private final CpuTier tier;

	public Cpu(CpuTier tier) {
		this.tier = tier;
	}

	public CpuTier tier() {
		return tier;
	}

	public static final String ABORT = "ABRT: ";
	public static final String NO_EEPROM = "no EEPROM installed";
	/** No RAM: nothing to run in, as a real PC that won't POST (user, 2026-10-13: memory is the RAM installed). */
	public static final String NO_RAM = "no RAM installed";
	/** RAM put in or taken out while a program runs: its memory is gone from under it. */
	public static final String RAM_CHANGED = "RAM changed while running";
	public static final String CRASHED = "EEPROM crashed: ";
	public static final String RETURNED = "EEPROM returned without halting";
	/** A wiped EEPROM: nothing to run, like a BIOS's "no bootable device". */
	public static final String BLANK = "EEPROM is blank";
	/** Before what's wrong with the EEPROM's text, such as Lua's syntax error. */
	public static final String EEPROM = "EEPROM: ";
	public static final int ABORT_FOREGROUND = 0x2E2E2E;
	public static final int ABORT_BACKGROUND = 0xBDBDBD;

	/**
	 * Starts the EEPROM's program, or shows the abort screen without one. A Java program attached
	 * to the EEPROM (a test's in-circuit programmer) runs on any CPU.
	 */
	public Optional<Runner> powerOn(Motherboard board) {
		if (board.chassis().memory().isEmpty()) {
			abort(board, NO_RAM);
			return Optional.empty();
		}
		var installed = board.eeprom();
		if (installed.isEmpty()) {
			abort(board, NO_EEPROM);
			return Optional.empty();
		}
		Eeprom eeprom = installed.get();
		if (eeprom.attached().isPresent()) {
			return Optional.of(new ProgramRunner(eeprom.attached().get()::run, "cpu"));
		}
		String image = eeprom.image(); // read from the EEPROM itself, at every power-on
		if (image.isBlank()) {
			abort(board, BLANK);
			return Optional.empty();
		}
		return Optional.of(new LuaRunner(image)); // its firmware loads the image (and aborts when it doesn't compile)
	}

	/** A saved program, going on: its VM restored. */
	public Optional<Runner> resume(byte[] saved) {
		return Optional.of(LuaRunner.restore(saved));
	}

	/** The program threw: the abort screen. */
	public void crashed(Motherboard board, Throwable error) {
		if (error instanceof LuaRunner.Aborted aborted) {
			abort(board, aborted.getMessage()); // the firmware's own reason, such as Lua's syntax error
			return;
		}
		abort(board, CRASHED + error.getMessage());
	}

	/** The RAM changed under the running program (user, 2026-10-13): the abort screen. */
	public void memoryChanged(Motherboard board) {
		abort(board, RAM_CHANGED);
	}

	/** The program returned instead of halting: the abort screen. */
	public void returned(Motherboard board) {
		abort(board, RETURNED);
	}

	/** Soft gray, cleared, then the reason: written to the console as escape sequences, like any program would. */
	private static void abort(Motherboard board, String reason) {
		ConsoleSplitter console = board.virtualBus().console();
		console.write("\u001B[0m" + color(38, ABORT_FOREGROUND) + color(48, ABORT_BACKGROUND) + "\u001B[2J\u001B[H" + ABORT + reason + "\r\n");
	}

	private static String color(int code, int rgb) {
		return "\u001B[" + code + ";2;" + (rgb >> 16 & 0xFF) + ";" + (rgb >> 8 & 0xFF) + ";" + (rgb & 0xFF) + "m";
	}
}
