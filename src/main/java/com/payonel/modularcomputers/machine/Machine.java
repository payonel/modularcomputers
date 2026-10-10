package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;

/**
 * A computer's state, independent of Minecraft. There is no CPU emulation or VM yet: turning
 * a machine on just prints {@link #GREETING}. An off machine's display is blank (black).
 */
public final class Machine {
	public static final String GREETING = "hello world";

	private final Hardware hardware;
	private final TextBuffer display;
	private boolean running;

	public Machine(Hardware hardware, Resolution resolution) {
		this.hardware = hardware;
		this.display = new TextBuffer(resolution);
	}

	public Hardware hardware() {
		return hardware;
	}

	public Resolution resolution() {
		return display.resolution();
	}

	public TextBuffer display() {
		return display;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		if (this.running == running) {
			return;
		}
		this.running = running;
		display.clear();
		if (running) {
			display.write(0, 0, GREETING);
		}
	}

	public void togglePower() {
		setRunning(!running);
	}
}
