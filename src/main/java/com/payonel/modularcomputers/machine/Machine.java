package com.payonel.modularcomputers.machine;

/**
 * A computer and its power switch, independent of Minecraft. Switching on hands control to the
 * CPU in the socket, if there is one; with no CPU the machine is powered but nothing runs.
 * Switching off cuts power to the {@link Motherboard}.
 * <p>
 * Parts aren't hot-plugged: installing a CPU in a powered machine does nothing until the next
 * power-on, and removing one leaves the devices as they were (a display keeps its last
 * picture).
 */
public final class Machine {
	private final Motherboard board;
	private boolean powered;

	public Machine(Motherboard board) {
		this.board = board;
	}

	public Motherboard board() {
		return board;
	}

	/** The power switch is on. */
	public boolean isPowered() {
		return powered;
	}

	public void setPowered(boolean powered) {
		if (this.powered == powered) {
			return;
		}
		this.powered = powered;
		if (powered) {
			board.cpu().ifPresent(cpu -> cpu.powerOn(board));
		} else {
			board.powerOff();
		}
	}

	public void togglePower() {
		setPowered(!powered);
	}

	/**
	 * Sets the switch from a save or a sync, without booting or cutting power: the devices
	 * restore their own state.
	 */
	public void restorePowered(boolean powered) {
		this.powered = powered;
	}
}
