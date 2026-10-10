package com.payonel.modularcomputers.machine;

/**
 * A computer and its power switch, independent of Minecraft. The machine is only ever on with
 * a CPU in the socket: pressing power without one is refused (the caller reports the error),
 * and pulling the CPU out of a running machine cuts its power. Switching on hands control to
 * the CPU; switching off cuts power to the {@link Motherboard}. Installing a CPU never turns the
 * machine on by itself.
 */
public final class Machine {
	/** What pressing the power button did. */
	public enum PowerPress {
		ON,
		OFF,
		/** Nothing: there's no CPU to power on. */
		NO_CPU,
	}

	private final Motherboard board;
	private boolean powered;

	public Machine(Motherboard board) {
		this.board = board;
	}

	public Motherboard board() {
		return board;
	}

	/** The machine is on (and so has a CPU). */
	public boolean isPowered() {
		return powered;
	}

	public PowerPress pressPower() {
		if (powered) {
			powered = false;
			board.powerOff();
			return PowerPress.OFF;
		}
		var cpu = board.cpu();
		if (cpu.isEmpty()) {
			return PowerPress.NO_CPU;
		}
		powered = true;
		cpu.get().powerOn(board);
		return PowerPress.ON;
	}

	/**
	 * Call after the CPU socket was emptied. A running machine loses power. Returns whether it
	 * was running.
	 */
	public boolean cpuRemoved() {
		if (!powered || board.cpu().isPresent()) {
			return false;
		}
		powered = false;
		board.powerOff();
		return true;
	}

	/**
	 * Sets the switch from a save or a sync, without booting or cutting power: the devices
	 * restore their own state.
	 */
	public void restorePowered(boolean powered) {
		this.powered = powered;
	}
}
