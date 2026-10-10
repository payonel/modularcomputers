package com.payonel.modularcomputers.display;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;

/** A text display on the bus. Whatever writes to its buffer shows on the screen. */
public final class Display implements Device {
	private final TextBuffer buffer;

	public Display(Resolution resolution) {
		this.buffer = new TextBuffer(resolution);
	}

	public TextBuffer buffer() {
		return buffer;
	}

	@Override
	public DeviceType type() {
		return DeviceType.DISPLAY;
	}

	@Override
	public void powerOff() {
		buffer.clear();
	}
}
