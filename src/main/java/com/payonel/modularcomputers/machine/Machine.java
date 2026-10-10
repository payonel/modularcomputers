package com.payonel.modularcomputers.machine;

/**
 * A computer and its power state, independent of Minecraft. Power-on hands control to the
 * {@link Cpu}; power-off cuts power to the {@link Motherboard}.
 */
public final class Machine {
	private final Motherboard board;
	private boolean running;

	public Machine(Motherboard board) {
		this.board = board;
	}

	public Motherboard board() {
		return board;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		if (this.running == running) {
			return;
		}
		this.running = running;
		if (running) {
			Cpu.powerOn(board);
		} else {
			board.powerOff();
		}
	}

	public void togglePower() {
		setRunning(!running);
	}

	/**
	 * Sets the power state from a save or a sync, without booting or cutting power: the devices
	 * restore their own state.
	 */
	public void restoreRunning(boolean running) {
		this.running = running;
	}
}
