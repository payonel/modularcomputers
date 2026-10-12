package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.BusStatus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.VirtualBus;
import com.payonel.modularcomputers.console.ConsoleSplitter;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Board;
import com.payonel.modularcomputers.runtime.DeviceMethods;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The motherboard API as the host carries it out, on the server thread: programs reach it
 * through their runner, which hands each call over.
 */
final class MotherboardApi implements Board {
	private final Motherboard board;
	private final LongSupplier uptime;
	/** The bus's changes when the program last listed it: newer ones raise the bus's interrupt. */
	private int listedChanges;

	MotherboardApi(Motherboard board, LongSupplier uptime) {
		this.board = board;
		this.uptime = uptime;
		this.listedChanges = board.bus().changes();
	}

	/** A device by address: on the physical bus, or a virtual one by name. */
	private Optional<Object> device(String address) {
		Bus bus = board.bus();
		Optional<Object> physical = bus.device(address).map(Object.class::cast);
		return physical.isPresent() ? physical : board.virtualBus().device(address);
	}

	@Override
	public List<DeviceInfo> list() {
		listedChanges = board.bus().changes();
		Bus bus = board.bus();
		return bus.addresses().stream()
			.map(address -> new DeviceInfo(address, bus.id(address), type(address).orElseThrow()))
			.toList();
	}

	@Override
	public BusStatus status() {
		return board.bus().status();
	}

	@Override
	public Optional<String> type(String address) {
		return device(address).map(device -> device instanceof Device real ? real.type().name().toLowerCase(Locale.ROOT) : address);
	}

	@Override
	public List<String> methods(String address) {
		return device(address).map(device -> List.copyOf(DeviceMethods.of(device).keySet())).orElse(List.of());
	}

	@Override
	public Optional<String> doc(String address, String method) {
		return device(address).flatMap(device -> DeviceMethods.doc(device, method));
	}

	@Override
	public Result invoke(String address, String method, Object... args) {
		Optional<Object> device = device(address);
		if (device.isEmpty()) {
			return Result.fail("no such device: " + address);
		}
		try {
			return DeviceMethods.invoke(device.get(), method, new Arguments(Arrays.asList(args)));
		} catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage()); // an argument that isn't a value a program can hold
		}
	}

	@Override
	public List<String> waitForInterrupt(double seconds) {
		return interrupts(); // the runner does the waiting; called directly, it doesn't wait
	}

	@Override
	public long uptime() {
		return uptime.getAsLong();
	}

	/** Halting is the program runner's: it stops the program's thread, so it never reaches the host as a call. */
	@Override
	public void halt() {
		throw new UnsupportedOperationException("halt() is handled by the program runner");
	}

	/**
	 * The interrupts pending now: the bus's (it changed since last listed), the console's (keys
	 * wait at a terminal in its group), then each device's, in bus order. A terminal in the
	 * console's group raises the console's, not its own.
	 */
	List<String> interrupts() {
		List<String> pending = new ArrayList<>();
		Bus bus = board.bus();
		ConsoleSplitter console = board.virtualBus().console();
		if (bus.changes() != listedChanges) {
			pending.add(BUS);
		}
		if (console.interruptPending()) {
			pending.add(VirtualBus.CONSOLE);
		}
		for (String address : bus.addresses()) {
			Device device = bus.device(address).orElseThrow();
			if (device.interruptPending() && !(device instanceof Terminal && console.isBound(address))) {
				pending.add(address);
			}
		}
		return pending;
	}
}
