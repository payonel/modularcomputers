package com.payonel.modularcomputers.machine;

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
 * With nothing to run (no RAM, no EEPROM, a blank one) it faults at power-on ({@link Fault}): the
 * machine aborts, its board recording why ({@link Shutdown}), as a program leaving its EEPROM
 * (returning, or an error) does too.
 */
public final class Cpu {
	private final CpuTier tier;

	public Cpu(CpuTier tier) {
		this.tier = tier;
	}

	public CpuTier tier() {
		return tier;
	}

	public static final String NO_EEPROM = "no EEPROM installed";
	/** No RAM: nothing to run in, as a real PC that won't POST (user, 2026-10-13: memory is the RAM installed). */
	public static final String NO_RAM = "no RAM installed";
	/** RAM put in or taken out while a program runs: its memory is gone from under it. */
	public static final String RAM_CHANGED = "RAM changed while running";
	public static final String CRASHED = "EEPROM crashed: ";
	public static final String RETURNED = "EEPROM returned";
	/** A wiped EEPROM: nothing to run, like a BIOS's "no bootable device". */
	public static final String BLANK = "EEPROM is blank";
	/** Before what's wrong with the EEPROM's text, such as Lua's syntax error. */
	public static final String EEPROM = "EEPROM: ";

	/** Nothing to run at power-on: why. */
	public static final class Fault extends Exception {
		public Fault(String why) {
			super(why, null, false, false);
		}
	}

	/**
	 * Starts the EEPROM's program, or faults without one. A Java program attached to the EEPROM (a
	 * test's in-circuit programmer) runs on any CPU.
	 */
	public Runner powerOn(Motherboard board) throws Fault {
		if (board.chassis().memory().isEmpty()) {
			throw new Fault(NO_RAM);
		}
		var installed = board.eeprom();
		if (installed.isEmpty()) {
			throw new Fault(NO_EEPROM);
		}
		Eeprom eeprom = installed.get();
		if (eeprom.attached().isPresent()) {
			return new ProgramRunner(eeprom.attached().get()::run, "cpu");
		}
		String image = eeprom.image(); // read from the EEPROM itself, at every power-on
		if (image.isBlank()) {
			throw new Fault(BLANK);
		}
		return new LuaRunner(image); // its firmware loads the image (and aborts when it doesn't compile)
	}

	/** A saved program, going on: its VM restored. */
	public Optional<Runner> resume(byte[] saved) {
		return Optional.of(LuaRunner.restore(saved));
	}

	/** What a program's crash says, as its board records it: the firmware's own reason (Lua's syntax error), or the error. */
	public static String crashMessage(Throwable error) {
		if (error instanceof LuaRunner.Aborted aborted) {
			return aborted.getMessage();
		}
		return CRASHED + (error == null ? "" : error.getMessage());
	}
}
