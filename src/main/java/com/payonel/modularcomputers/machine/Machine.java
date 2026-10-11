package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.display.Display;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A computer and its power switch, independent of Minecraft. The machine is only ever on with
 * a CPU in the socket: pressing power without one is refused (the caller reports the error),
 * and pulling the CPU out of a running machine cuts its power. Switching on hands control to
 * the CPU; switching off cuts power to the {@link Motherboard}. Installing a CPU never turns the
 * machine on by itself.
 * <p>
 * It also needs power (in a computer, a charged battery). Without it, pressing the button does
 * nothing at all, and a running machine whose power gives out simply goes off.
 */
public final class Machine {
	/** What pressing the power button did. */
	public enum PowerPress {
		ON,
		OFF,
		/** Nothing: there's no CPU to power on. */
		NO_CPU,
		/** Nothing at all: there's no power to turn on with. */
		NO_POWER,
	}

	private final Motherboard board;
	private final BooleanSupplier power;
	private boolean powered;

	/** A machine on a bench supply: it always has power. */
	public Machine(Motherboard board) {
		this(board, () -> true);
	}

	/** {@code power} says whether there's power to run on right now. */
	public Machine(Motherboard board, BooleanSupplier power) {
		this.board = board;
		this.power = power;
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
		if (!power.getAsBoolean()) {
			return PowerPress.NO_POWER;
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
	 * Call after the power may have gone (the battery ran flat or was pulled out). A running
	 * machine without power goes off. Returns whether it was running.
	 */
	public boolean powerLost() {
		if (!powered || power.getAsBoolean()) {
			return false;
		}
		powered = false;
		board.powerOff();
		return true;
	}

	/**
	 * Puts these devices on the bus, in order: a part went in or out, a cable was plugged or
	 * unplugged, or the player reordered them. While the machine runs, devices that left lose
	 * power (an unplugged screen goes blank), and displays that joined get the console drawn
	 * onto them (hot-plug, like fbcon taking over a new framebuffer).
	 */
	public void setDevices(List<Device> devices) {
		List<Device> before = board.bus().devices();
		board.bus().setDevices(devices);
		if (!powered) {
			return;
		}
		for (Device device : before) {
			if (!devices.contains(device)) {
				device.powerOff();
			}
		}
		for (Device device : devices) {
			if (!before.contains(device) && device instanceof Display display) {
				board.virtualBus().console().redraw(display);
			}
		}
	}

	/**
	 * Sets the switch from a save or a sync, without booting or cutting power: the devices
	 * restore their own state.
	 */
	public void restorePowered(boolean powered) {
		this.powered = powered;
	}
}
