package com.payonel.modularcomputers.bus;

/**
 * Something attached to a computer's bus. Devices describe themselves; there are no drivers.
 * Programs use a device through its methods (public methods annotated
 * {@link com.payonel.modularcomputers.runtime.Method}), by its address on the bus.
 */
public interface Device {
	DeviceType type();

	/** What the player named it (an anvil's name on its item), if anything: a label, like a disk's. */
	default String label() {
		return null;
	}

	/**
	 * What it says of itself beyond its address, id, type and label (see
	 * {@link com.payonel.modularcomputers.runtime.Board#info}): its tier, its size. Values a
	 * program can hold.
	 */
	default java.util.Map<String, Object> info() {
		return java.util.Map.of();
	}

	/**
	 * The device's interrupt line: on while it has something for the program (a key pressed, data
	 * arrived), off once the program has read it. Level-triggered, as on real hardware: the data
	 * stays in the device, not in a machine-wide queue.
	 */
	/**
	 * The method that takes its next piece of data (a terminal's {@code read}, a sensor's
	 * {@code pull}), when it has a stream of it: the board can take it ahead of the program, into
	 * the ring in its RAM (user design, 2026-10-13). Null: none.
	 */
	default String streamMethod() {
		return null;
	}

	default boolean interruptPending() {
		return false;
	}

	/**
	 * Its power from the bus, if it takes any (see {@link BusPower}): it has power while a running
	 * computer has it on its bus, and owes what it draws. Null for a device that takes none; such
	 * a device just loses power when it leaves a running computer's bus, or the computer goes off.
	 */
	default BusPower power() {
		return null;
	}

	/** Its interrupt line, if it can wake a computer the moment it has data (see {@link InterruptLine}). */
	default InterruptLine interruptLine() {
		return null;
	}

	/** A program's turn changed it: push what it shows out now (to players), rather than at the next tick. */
	default void flush() {
	}

	/** It lost power. Volatile state goes away (a display goes black). */
	default void powerOff() {
	}

	/**
	 * What it holds that must outlast a save or an unload, as runtime values: data waiting for the
	 * program (keys, events), settings a program made. A save isn't a power cut: nothing is lost.
	 * The block it's in keeps it, and hands it back with {@link #restore}.
	 */
	default java.util.Map<String, Object> saved() {
		return java.util.Map.of();
	}

	/** Takes back what {@link #saved} gave, after a load. */
	default void restore(java.util.Map<String, Object> saved) {
	}

	/**
	 * Who to tell, at once, when what it saves ({@link #saved}) or shows changes: the block it's in,
	 * which marks its chunk changed. Minecraft writes only chunks marked changed, at a save or an
	 * unload, so an unmarked change would leave the old copy on disk. Server thread.
	 */
	default void onChange(Runnable changed) {
	}
}
