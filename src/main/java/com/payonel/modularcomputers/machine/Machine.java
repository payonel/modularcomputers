package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.runtime.Runner;
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
	private Runner runner;
	private long uptime;
	/** Ticks the CPU executed since power use was last collected. */
	private long cpuTicks;
	/** Ticks it waited for an interrupt since then. */
	private long waitingTicks;
	/** Whether the CPU executed in the last whole tick (or waited, or was halted). */
	private boolean executedLastTick;
	/** Calls answered this tick, by the tick's turn and any interrupt turns. */
	private int callsThisTick;
	private boolean executedThisTick;
	/** In a turn: an interrupt raised by the program's own calls waits for its next wait. */
	private boolean running;
	/** What the machine's devices' interrupt lines call. */
	private final Runnable wake = this::wake;
	private int callsPerTick = DEFAULT_CALLS_PER_TICK;
	/** Runs work on the server thread soon (in a game, the server); null: only at ticks. */
	private java.util.concurrent.Executor server;

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

	/** How a runner on its own thread gets the server thread to serve it soon (in a game, the server). */
	public void setServer(java.util.concurrent.Executor server) {
		this.server = server;
	}

	/**
	 * The server thread serves the program now (a runner on its own thread asked): it carries out
	 * its waiting calls and answers its wait, within its server time.
	 */
	public void serve() {
		if (powered && runner != null && !running) {
			run();
		}
	}

	/** The program on the CPU, while one runs (or waits). */
	public Optional<Runner> runner() {
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
		callsThisTick = 0;
		executedThisTick = false;
		run();
		if (runner != null && !executedThisTick) {
			waitingTicks++; // it waited all through this tick
		}
		executedLastTick = executedThisTick;
		return Supervision.OK;
	}

	/**
	 * A device's interrupt line rose: a program waiting for an interrupt runs now, between ticks,
	 * as a real interrupt wakes a CPU from {@code HLT} at once. It spends what's left of this
	 * tick's calls, so waking it often buys no extra time.
	 */
	public void wake() {
		if (powered && runner != null && !running && runner.state() == Runner.State.WAITING) {
			run();
		}
	}

	/**
	 * The program's turn: what's left of this tick's {@link #callsPerTick} calls. Afterwards the
	 * devices push out what changed (a terminal's screen), so players see it now.
	 */
	private void run() {
		if (runner == null || running) {
			return;
		}
		int calls = callsPerTick - callsThisTick;
		if (calls <= 0 && runner.callLimited()) {
			return; // spent: the next tick carries on
		}
		Runner.State before = runner.state();
		long answeredBefore = runner.answered();
		Runner.State state;
		running = true;
		try {
			state = runner.tick(api, calls, uptime, api::interrupts);
		} finally {
			running = false;
		}
		int answered = (int) (runner.answered() - answeredBefore);
		callsThisTick += answered;
		boolean executed = answered > 0 || before != Runner.State.WAITING || state != Runner.State.WAITING;
		if (executed && !executedThisTick) {
			executedThisTick = true;
			cpuTicks++; // it executed this tick (waiting all through, it draws its waiting draw instead)
		}
		if (state == Runner.State.CRASHED) {
			board.cpu().ifPresent(cpu -> cpu.crashed(board, runner.crash()));
		} else if (state == Runner.State.RETURNED) {
			board.cpu().ifPresent(cpu -> cpu.returned(board));
		}
		if (state != Runner.State.RUNNING && state != Runner.State.WAITING) {
			runner = null; // the CPU is halted; power stays on, and the screens keep what they show
		}
		if (executed) {
			board.bus().devices().forEach(Device::flush);
		}
	}

	private void cutPower() {
		powered = false;
		stopProgram();
		releaseDevices();
		board.virtualBus().console().reset();
	}

	/** This machine stops supplying its devices: those it was the last supplier of lose power. */
	private void releaseDevices() {
		board.bus().devices().forEach(this::release);
	}

	private void release(Device device) {
		if (device.interruptLine() != null) {
			device.interruptLine().disconnect(wake);
		}
		BusPower power = device.power();
		if (power == null || power.release(this)) {
			device.powerOff();
		}
	}

	private void supply(Device device) {
		if (device.interruptLine() != null) {
			device.interruptLine().connect(wake);
		}
		if (device.power() != null) {
			device.power().supply(this);
		}
	}

	/**
	 * What it draws now, in power units a tick: the CPU at its tier's rate for what it did last
	 * tick (running or waiting; halted, nothing), and every powered device on its bus at its own
	 * draw. Per-call costs (a terminal's writes) come and go, so they aren't in it. Nothing while off.
	 */
	public long currentDraw() {
		if (!powered) {
			return 0;
		}
		CpuTier tier = board.cpu().map(Cpu::tier).orElse(CpuTier.TIER_1);
		long draw = runner == null ? 0 : executedLastTick ? tier.running() : tier.waiting();
		for (Device device : board.bus().devices()) {
			if (device.power() != null && device.power().powered()) {
				draw += device.power().draw();
			}
		}
		return draw;
	}

	/**
	 * Power used since the last call, up to game time {@code now}, in power units:
	 * the CPU's own (its tier's running draw for each tick it executed, its waiting draw for each
	 * tick it waited for an interrupt, nothing while halted) and what its bus
	 * devices owe (see {@link BusPower}; a device shared with another computer pays once, to
	 * whichever collects first). Nothing while off.
	 */
	public long powerUse(long now) {
		if (!powered) {
			return 0;
		}
		CpuTier tier = board.cpu().map(Cpu::tier).orElse(CpuTier.TIER_1);
		long used = cpuTicks * tier.running() + waitingTicks * tier.waiting();
		cpuTicks = 0;
		waitingTicks = 0;
		for (Device device : board.bus().devices()) {
			if (device.power() != null) {
				used += device.power().collect(now);
			}
		}
		return used;
	}

	private void stopProgram() {
		if (runner != null) {
			runner.stop();
			runner = null;
		}
	}

	/**
	 * The machine is unloading (its chunk, or the world) or gone: the program stops (it isn't
	 * saved), and it stops supplying its devices.
	 */
	public void unload() {
		com.payonel.modularcomputers.bus.BusHolds.releaseAll(board); // its devices are free for other boards
		stopProgram();
		if (powered) {
			releaseDevices();
		}
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
		cpuTicks = 0;
		waitingTicks = 0;
		callsThisTick = 0;
		executedThisTick = false;
		board.bus().devices().forEach(this::supply);
		runner = cpu.get().powerOn(board).orElse(null);
		if (runner != null) {
			Runner started = runner;
			// A runner on its own thread asks the server thread to serve it as soon as it can.
			started.serveWith(() -> {
				if (server != null) {
					server.execute(() -> {
						if (runner == started) {
							serve();
						}
					});
				}
			});
		}
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

	/** Puts the motherboard's {@link Motherboard#layout} on the bus, with hot-plug. */
	public void rebuildBus(long now) {
		Motherboard.Layout layout = board.layout(now);
		com.payonel.modularcomputers.bus.BusHolds.hold(board, layout.devices().values()); // one bus at a time
		setDevices(layout.devices(), layout.aliases());
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
		var console = board.virtualBus().console();
		console.busChanged();
		if (!powered) {
			return;
		}
		for (Device device : before) {
			if (!devices.contains(device)) {
				release(device);
			}
		}
		for (var entry : addressed.entrySet()) {
			if (before.contains(entry.getValue())) {
				continue;
			}
			supply(entry.getValue());
			// A terminal that joined is in the console's group: it gets the console drawn on it.
			if (entry.getValue() instanceof Terminal terminal && console.isBound(entry.getKey())) {
				console.redraw(terminal);
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
		if (powered) {
			board.bus().devices().forEach(this::supply);
		}
	}
}
