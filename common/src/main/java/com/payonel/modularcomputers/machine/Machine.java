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
	/** What the machine's devices' wake lines call (see {@link Device#wakeLine}). */
	private final Runnable wakeUp = this::wakeUp;
	/** A device asked to switch the machine on: at its next tick. */
	private boolean wakeAsked;
	/** How it switches on when a device wakes it: its power button (in a game, the block's, which clicks and lights). */
	private Runnable waker = this::pressPower;
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
		this.api = new MotherboardApi(board, () -> uptime, () -> runner == null ? 0 : runner.memoryUsed(),
			() -> runner == null ? com.payonel.modularcomputers.runtime.MemoryAccount.UNLIMITED : runner.memoryAccount());
		board.bus().devices().forEach(this::wireWake);
	}

	/** How a device's wake switches it on: by {@code waker} (the block's power button), not the machine's own. */
	public void wakeWith(Runnable waker) {
		this.waker = waker;
	}

	/** A device's wake line rose (see {@link Device#wakeLine}): off, it switches on at its next tick. */
	private void wakeUp() {
		if (!powered) {
			wakeAsked = true;
		}
	}

	private void wireWake(Device device) {
		if (device.wakeLine() != null) {
			device.wakeLine().connect(wakeUp);
		}
	}

	private void unwireWake(Device device) {
		if (device.wakeLine() != null) {
			device.wakeLine().disconnect(wakeUp);
		}
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

	/**
	 * Ticks since power-on, counting only ticks it's been loaded and on: like Linux's
	 * CLOCK_MONOTONIC, it stands still while the computer is unloaded (a hibernated machine), and
	 * it's saved with it. Waits end on deadlines on this clock. Real time is another device's (a
	 * future card), not the board's.
	 */
	public long uptime() {
		return uptime;
	}

	/** After a load: the clock goes on from where it was saved. */
	public void restoreUptime(long uptime) {
		this.uptime = uptime;
	}

	/**
	 * Every tick: the motherboard checks its CPU and power (like a socket's "CPU present" pin and
	 * a power supply's "power good" line), and a running program gets its turn.
	 */
	public Supervision tick() {
		if (!powered) {
			if (wakeAsked) {
				wakeAsked = false;
				waker.run(); // a device woke it, as its power button would (a wake from a frame that came while it was on is dropped)
			}
			return Supervision.OK;
		}
		wakeAsked = false;
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
		publishInterrupts(); // at once, whether it waits or not: its next wait sees the news
		if (powered && runner != null && !running && runner.state() == Runner.State.WAITING) {
			run();
		}
	}

	/**
	 * Tells the program which sources have an interrupt raised now, as an interrupt controller's
	 * status register does: read by the CPU without asking the devices. Server thread.
	 */
	private void publishInterrupts() {
		if (powered && runner != null) {
			runner.interruptsRaised(api.interrupts());
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
		if (state == Runner.State.POWERED_OFF || state == Runner.State.REBOOTING) {
			runner = null;
			cutPower(); // ACPI soft-off: as the power button does
			if (state == Runner.State.REBOOTING) {
				pressPower(); // and on again: the CPU boots its EEPROM afresh
			}
			return;
		}
		if (state != Runner.State.RUNNING && state != Runner.State.WAITING) {
			runner = null; // the CPU is halted; power stays on, and the screens keep what they show
			setAccepting(false);
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
		setAccepting(false);
	}

	/**
	 * Whether the devices on its bus take data (keys, events) now (user, 2026-10-13). A device takes
	 * data only while all three hold: a board holds it on its bus, the board powers it, and the
	 * board's CPU runs a program (not halted, aborted or off). When any ends, it drops all it holds;
	 * until they hold again, it drops what comes in. This machine's devices are on its bus and held
	 * by it, and it powers them while it's on, so it's on with a program running.
	 */
	private boolean accepting;

	private void setAccepting(boolean now) {
		if (now == accepting) {
			return;
		}
		accepting = now;
		board.bus().devices().forEach(device -> device.accepting(now));
	}

	/** A program starts afresh: its devices start empty (what came in before is dropped), then take data. */
	private void startAccepting() {
		board.bus().devices().forEach(device -> device.accepting(false));
		accepting = false;
		setAccepting(true);
	}

	/**
	 * The machine is unloading (its chunk, or the world): it's frozen, not switched off. Its program
	 * stops here (it was saved, and goes on after a load) and it stops supplying its devices, but they
	 * aren't powered off: what they hold for the program (keys, events) and show stays for when it's
	 * back. Breaking it is a power cut ({@link #removed}).
	 */
	public void unload() {
		com.payonel.modularcomputers.bus.BusHolds.releaseAll(board); // its devices are free for other boards
		board.bus().devices().forEach(this::unwireWake);
		stopProgram();
		if (powered) {
			board.bus().devices().forEach(device -> {
				if (device.interruptLine() != null) {
					device.interruptLine().disconnect(wake);
				}
				if (device.power() != null) {
					device.power().release(this);
				}
			});
		}
	}

	/** The machine is gone (broken): its power is cut, and the devices it was the last to supply lose power. */
	public void removed() {
		if (powered) {
			releaseDevices();
		}
		unload();
	}

	public Motherboard board() {
		return board;
	}

	/** The machine is on (and so has a CPU). */
	public boolean isPowered() {
		return powered;
	}

	/** On, but its CPU has stopped: the program halted, or the CPU aborted or crashed. Power stays on. */
	public boolean isHalted() {
		return powered && runner == null;
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
		memoryAtStart = board.chassis().memory();
		adopt(cpu.get().powerOn(board).orElse(null));
		if (runner != null) {
			startAccepting();
		}
		run(); // the CPU starts executing right away
		return PowerPress.ON;
	}

	/** Takes {@code started} as the program on the CPU; a runner on its own thread asks the server thread to serve it as soon as it can. */
	private void adopt(Runner started) {
		runner = started;
		if (started != null) {
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
	}

	/**
	 * The running program's state, to save with the computer (a Lua CPU's, by Styx); empty when
	 * nothing runs or it can't be saved now (it then comes back halted). Server thread.
	 */
	public Optional<byte[]> persistProgram() {
		if (!powered || runner == null) {
			return Optional.empty();
		}
		return runner.persist(api, who.get());
	}

	/** The computer as a log names it (which board, where): read from the block it's in when needed. */
	private java.util.function.Supplier<String> who = () -> "a computer";

	public void describedBy(java.util.function.Supplier<String> who) {
		this.who = who;
	}

	/** After a load: the saved program goes on where it was ({@link #persistProgram}). The machine must be on, with its CPU. */
	public void resumeProgram(byte[] saved) {
		if (!powered || runner != null || board.cpu().isEmpty()) {
			return;
		}
		try {
			memoryAtStart = board.chassis().memory();
			adopt(board.cpu().get().resume(saved).orElse(null));
			setAccepting(runner != null); // it goes on: what its devices kept for it (saved with them) stays
		} catch (RuntimeException e) {
			com.payonel.modularcomputers.ModularComputers.LOGGER.warn("A saved computer didn't restore; its CPU comes back halted", e);
			return;
		}
		run();
	}

	/** The RAM the running program started with (its memory): a change aborts it. */
	private List<com.payonel.modularcomputers.runtime.Board.MemoryInfo> memoryAtStart = List.of();

	/**
	 * Call after the parts changed. RAM put in or taken out while the CPU runs a program aborts it
	 * (user, 2026-10-13), as a real machine crashes: the CPU's abort screen, power stays on.
	 * Returns whether it did.
	 */
	public boolean memoryChanged() {
		if (!powered || runner == null || board.chassis().memory().equals(memoryAtStart)) {
			return false;
		}
		stopProgram();
		board.cpu().ifPresent(cpu -> cpu.memoryChanged(board));
		return true;
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

	/** Puts the motherboard's {@link Motherboard#layout} on the bus, with hot-plug. */
	public void rebuildBus(long now) {
		Motherboard.Layout layout = board.layout(now);
		com.payonel.modularcomputers.bus.BusHolds.hold(board, layout.devices().values()); // one bus at a time
		setDevices(layout.devices(), layout.aliases());
	}

	/**
	 * The bus becomes these devices, by address, with other names for some (a part's id). While the
	 * machine runs, devices that left lose power (an unplugged screen goes blank) and drop their
	 * data, and displays that joined get the console drawn onto them (hot-plug, as fbcon takes over
	 * a new framebuffer). Only {@link #rebuildBus}
	 * calls it, with the motherboard's layout: a bus is only ever what a case's parts and cables make.
	 */
	private void setDevices(Map<String, Device> addressed, Map<String, String> aliases) {
		List<Device> devices = List.copyOf(addressed.values());
		List<Device> before = board.bus().devices();
		Map<String, Device> was = new java.util.HashMap<>();
		board.bus().forEach(was::put);
		board.bus().setDevices(addressed, aliases);
		for (Device device : before) {
			if (!devices.contains(device)) {
				unwireWake(device); // (its wake line is wired on or off)
			}
		}
		for (Device device : devices) {
			if (!before.contains(device)) {
				wireWake(device);
			}
		}
		if (runner != null) {
			List<String> left = was.entrySet().stream().filter(entry -> addressed.get(entry.getKey()) != entry.getValue()).map(Map.Entry::getKey).toList();
			if (!left.isEmpty()) {
				runner.devicesLeft(left);
			}
		}
		var console = board.virtualBus().console();
		console.busChanged();
		if (!powered) {
			return;
		}
		for (Device device : before) {
			if (!devices.contains(device)) {
				release(device);
				device.accepting(false); // off its bus: it drops what it held
			}
		}
		for (var entry : addressed.entrySet()) {
			if (before.contains(entry.getValue())) {
				continue;
			}
			supply(entry.getValue());
			if (accepting) {
				entry.getValue().accepting(false); // what it had before it joined isn't this program's
				entry.getValue().accepting(true);
			}
			// A terminal that joined is in the console's group: it gets the console drawn on it.
			if (entry.getValue() instanceof Terminal terminal && console.isBound(entry.getKey())) {
				console.redraw(terminal);
			}
		}
		publishInterrupts(); // the bus changed: an interrupt of its own
	}

	/**
	 * Sets the switch from a save or a sync, without booting or cutting power: the devices
	 * restore their own state. The program comes back with {@link #resumeProgram} (a Lua CPU's);
	 * without it, the CPU is halted.
	 */
	public void restorePowered(boolean powered) {
		this.powered = powered;
		if (powered) {
			board.bus().devices().forEach(this::supply);
		}
	}
}
