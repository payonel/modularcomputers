package com.payonel.modularcomputers.machine;

import java.util.Locale;

/**
 * Why a computer last stopped, as its board records it (user design, 2026-10-26; a chipset's
 * power-failure flags, a BMC's event log): read by the firmware at the next boot ({@code
 * board.lastShutdown()}), and by a probe without powering it on. An abort is any way out but the
 * board's own power-off or restart: the program leaving its EEPROM (returning, or an error), the
 * power running out, the CPU or the RAM changing under it. An aborted computer is off, its light
 * blinking amber until it's next powered on. {@code time} is the world's (game ticks).
 */
public record Shutdown(Cause cause, String message, long time) {
	/** Why. Aborts are the faults; the rest are clean. */
	public enum Cause {
		/** Never stopped yet (a new board). */
		NONE(false),
		/** The program asked the board to power off, or restart. */
		CLEAN(false),
		/** Its power button, pressed while it ran. */
		BUTTON(false),
		/** The power ran out: its buffer empty, no battery to carry it. */
		POWER_LOST(true),
		/** Its CPU was taken out or swapped while it ran. */
		CPU_CHANGED(true),
		/** RAM was put in, taken out or swapped while it ran. */
		RAM_CHANGED(true),
		/** Nothing to run: no RAM, no EEPROM, a blank one, or text that doesn't compile. */
		NO_PROGRAM(true),
		/** The program returned out of its EEPROM (as Linux's init exiting). */
		RETURNED(true),
		/** The program raised an error out of its EEPROM. */
		CRASHED(true),
		/** It was on, but its program wasn't saved with the world (it couldn't be), so it can't go on. */
		UNSAVED(true);

		private final boolean abort;

		Cause(boolean abort) {
			this.abort = abort;
		}

		/** Whether it's a fault: an abort. */
		public boolean abort() {
			return abort;
		}

		/** Its name as programs read it: "power lost", "cpu changed". */
		public String word() {
			return name().toLowerCase(Locale.ROOT).replace('_', ' ');
		}

		/** A cause from its word, or NONE. */
		public static Cause of(String word) {
			for (Cause cause : values()) {
				if (cause.word().equals(word)) {
					return cause;
				}
			}
			return NONE;
		}
	}

	public static final Shutdown NONE = new Shutdown(Cause.NONE, "", 0);

	public boolean aborted() {
		return cause.abort();
	}
}
