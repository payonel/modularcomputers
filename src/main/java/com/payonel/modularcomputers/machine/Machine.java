package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.runtime.ProgramRunner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

	/** How many motherboard calls a machine's program gets each tick, before it's paused until the next. */
	public static final int DEFAULT_CALLS_PER_TICK = 20;

	/** What a tick's supervision found. */
	public enum Supervision {
		/** All well (or off). */
		OK,
		/** The CPU is gone: the machine died (an error beep). */
		LOST_CPU,
		/** Power is gone: the machine died (an error beep). */
		LOST_POWER,
	}

	private final Motherboard board;
	private final BooleanSupplier power;
	private final MotherboardApi api;
	private boolean powered;
	private ProgramRunner runner;
	private long uptime;
	private int callsPerTick = DEFAULT_CALLS_PER_TICK;

	/** A machine on a bench supply: it always has power. */
	public Machine(Motherboard board) {
		this(board, () -> true);
	}

	/** {@code power} says whether there's power to run on right now. */
	public Machine(Motherboard board, BooleanSupplier power) {
		this.board = board;
		this.power = power;
		this.api = new MotherboardApi(board, () -> uptime);
	}

	/** The program on the CPU, while one runs (or waits). */
	public Optional<ProgramRunner> runner() {
		return Optional.ofNullable(runner);
	}

	public void setCallsPerTick(int callsPerTick) {
		this.callsPerTick = callsPerTick;
	}

	/** Ticks since power-on. */
	public long uptime() {
		return uptime;
	}

	/**
	 * Every tick: the motherboard checks its CPU and power (like a socket's "CPU present" pin and
	 * a power supply's "power good" line), and a running program gets its turn.
	 */
	public Supervision tick() {
		if (!powered) {
			return Supervision.OK;
		}
		if (board.cpu().isEmpty()) {
			cutPower();
			return Supervision.LOST_CPU;
		}
		if (!power.getAsBoolean()) {
			cutPower();
			return Supervision.LOST_POWER;
		}
		uptime++;
		run();
		return Supervision.OK;
	}

	/** The program's turn: up to {@link #callsPerTick} calls. */
	private void run() {
		if (runner == null) {
			return;
		}
		ProgramRunner.State state = runner.tick(api, callsPerTick, uptime, api::interrupts);
		if (state == ProgramRunner.State.CRASHED) {
			board.cpu().ifPresent(cpu -> cpu.crashed(board, runner.crash()));
		}
		if (state == ProgramRunner.State.CRASHED || state == ProgramRunner.State.HALTED) {
			runner = null;
		}
	}

	private void cutPower() {
		powered = false;
		stopProgram();
		board.powerOff();
	}

	private void stopProgram() {
		if (runner != null) {
			runner.stop();
			runner = null;
		}
	}

	/** The machine is unloading (its chunk, or the world): the program stops. It isn't saved. */
	public void unload() {
		stopProgram();
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
			cutPower();
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
		uptime = 0;
		runner = cpu.get().powerOn(board).orElse(null);
		run(); // the CPU starts executing right away
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
		cutPower();
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
		cutPower();
		return true;
	}

	/**
	 * Puts these devices on the bus, in order: a part went in or out, a cable was plugged or
	 * unplugged, or the player reordered them. While the machine runs, devices that left lose
	 * power (an unplugged screen goes blank), and displays that joined get the console drawn
	 * onto them (hot-plug, like fbcon taking over a new framebuffer).
	 */
	public void setDevices(List<Device> devices) {
		Map<String, Device> numbered = new LinkedHashMap<>();
		for (int i = 0; i < devices.size(); i++) {
			numbered.put("device:" + i, devices.get(i));
		}
		setDevices(numbered);
	}

	/** {@link #setDevices(List)}, with each device at its address. */
	public void setDevices(Map<String, Device> addressed) {
		setDevices(addressed, Map.of());
	}

	/** {@link #setDevices(Map)}, with other names for some devices (see {@link com.payonel.modularcomputers.bus.Bus#setDevices(Map, Map)}). */
	public void setDevices(Map<String, Device> addressed, Map<String, String> aliases) {
		List<Device> devices = List.copyOf(addressed.values());
		List<Device> before = board.bus().devices();
		board.bus().setDevices(addressed, aliases);
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
	 * restore their own state. A program that was running isn't saved (the mock runtime isn't
	 * saveable): the CPU comes back halted.
	 */
	public void restorePowered(boolean powered) {
		this.powered = powered;
	}
}
