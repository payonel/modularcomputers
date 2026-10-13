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
 * The EEPROM holds plain text, run as the CPU's {@link Architecture} reads it: a Lua CPU runs it as
 * a Lua chunk ({@link LuaRunner}); the Java CPU runs the program class it names ({@link #load}).
 * <p>
 * Its only behavior of its own is the abort screen, like a kernel panic: with no EEPROM, when
 * the program crashes, or when it returns instead of halting (Linux panics when init exits), it
 * clears the console to a soft gray, writes {@code ABRT: <reason>} and halts. Power stays on.
 */
public final class Cpu {
	private final CpuTier tier;
	private final Architecture architecture;

	/** A Lua CPU, as every crafted one is. */
	public Cpu(CpuTier tier) {
		this(tier, Architecture.LUA);
	}

	public Cpu(CpuTier tier, Architecture architecture) {
		this.tier = tier;
		this.architecture = architecture;
	}

	public CpuTier tier() {
		return tier;
	}

	public Architecture architecture() {
		return architecture;
	}

	public static final String ABORT = "ABRT: ";
	public static final String NO_EEPROM = "no EEPROM installed";
	public static final String CRASHED = "EEPROM crashed: ";
	public static final String RETURNED = "EEPROM returned without halting";
	/** A wiped EEPROM: nothing to run, like a BIOS's "no bootable device". */
	public static final String BLANK = "EEPROM is blank";
	/** The image isn't a program for this CPU (Linux's ENOEXEC: a binary of another architecture). */
	public static final String EXEC_FORMAT = "EEPROM: exec format error";
	/** Before what's wrong with the EEPROM's text, such as Lua's syntax error. */
	public static final String EEPROM = "EEPROM: ";
	public static final int ABORT_FOREGROUND = 0x2E2E2E;
	public static final int ABORT_BACKGROUND = 0xBDBDBD;

	/**
	 * Starts the EEPROM's program, or shows the abort screen without one. A Java program attached
	 * to the EEPROM (a test's in-circuit programmer) runs on any CPU.
	 */
	public Optional<Runner> powerOn(Motherboard board) {
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
		if (architecture == Architecture.LUA) {
			return Optional.of(new LuaRunner(image)); // its firmware loads the image (and aborts when it doesn't compile)
		}
		Optional<Program> program = load(image);
		if (program.isEmpty()) {
			abort(board, EXEC_FORMAT);
			return Optional.empty();
		}
		return Optional.of(new ProgramRunner(program.get()::run, "cpu"));
	}

	/**
	 * The Java CPU's reading: the EEPROM's text names the program's class (its first line), which
	 * must be a {@link Program} with a public no-argument constructor. The class is looked at before
	 * it's initialized, so naming anything else runs nothing. Empty when it isn't a Java program.
	 */
	public static Optional<Program> load(String image) {
		String name = image.strip().lines().findFirst().orElse("").strip();
		if (!name.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*")) {
			return Optional.empty();
		}
		try {
			Class<?> type = Class.forName(name, false, Program.class.getClassLoader());
			if (!Program.class.isAssignableFrom(type)) {
				return Optional.empty();
			}
			return Optional.of((Program) type.getConstructor().newInstance());
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return Optional.empty();
		}
	}

	/** The program threw: the abort screen. */
	public void crashed(Motherboard board, Throwable error) {
		if (error instanceof LuaRunner.Aborted aborted) {
			abort(board, aborted.getMessage()); // the firmware's own reason, such as Lua's syntax error
			return;
		}
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
