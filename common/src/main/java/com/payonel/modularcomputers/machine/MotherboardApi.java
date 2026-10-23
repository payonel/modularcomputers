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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The motherboard API as the host carries it out, on the server thread: programs reach it
 * through their runner, which hands each call over.
 */
final class MotherboardApi implements Board {
	private final Motherboard board;
	private final LongSupplier uptime;
	/** What the program holds of the memory (its runtime counts it). */
	private final LongSupplier memoryUsed;
	/** The program's memory, which devices charge what they hold for it to. */
	private final java.util.function.Supplier<com.payonel.modularcomputers.runtime.MemoryAccount> account;
	/** The bus's changes when the program last listed it: newer ones raise the bus's interrupt. */
	private int listedChanges;

	MotherboardApi(Motherboard board, LongSupplier uptime, LongSupplier memoryUsed,
		java.util.function.Supplier<com.payonel.modularcomputers.runtime.MemoryAccount> account) {
		this.board = board;
		this.uptime = uptime;
		this.memoryUsed = memoryUsed;
		this.account = account;
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
	public PowerInfo power() {
		return board.chassis().power();
	}

	@Override
	public HardwareInfo hardware() {
		Chassis chassis = board.chassis();
		CpuInfo cpu = board.cpu().map(Cpu::tier).map(tier -> new CpuInfo(tier.number(), board.busCapacity())).orElse(null);
		List<MemoryInfo> memory = chassis.memory();
		long total = memory.stream().mapToLong(MemoryInfo::capacity).sum();
		return new HardwareInfo(chassis.tier(), cpu, memory, total, memoryUsed.getAsLong());
	}

	@Override
	public Optional<Map<String, Object>> info(String address) {
		return device(address).map(device -> {
			Map<String, Object> info = new LinkedHashMap<>();
			Bus bus = board.bus();
			if (device instanceof Device real) {
				String at = bus.address(address).orElse(address);
				info.put("address", at);
				if (bus.id(at) != null) {
					info.put("id", bus.id(at));
				}
				info.put("type", real.type().name().toLowerCase(Locale.ROOT));
				if (real.label() != null) {
					info.put("label", real.label());
				}
				info.putAll(real.info());
			} else {
				info.put("address", address); // a virtual device: its name is all it has
				info.put("type", address);
			}
			return info;
		});
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
			return DeviceMethods.invoke(device.get(), method, new Arguments(Arrays.asList(args), account.get()));
		} catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage()); // an argument that isn't a value a program can hold
		}
	}

	@Override
	public Result invoke(com.payonel.modularcomputers.runtime.Handle handle, String method, Object... args) {
		if (handle == null || !handle.isOpen()) {
			return Result.fail("closed handle");
		}
		try {
			return DeviceMethods.invoke(handle, method, new Arguments(Arrays.asList(args), account.get()));
		} catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
	}

	@Override
	public List<String> waitForInterrupt(double seconds, String... sources) {
		// The runner does the waiting; called directly, it doesn't wait. A source is answered by the
		// name it was asked by (a card's id, or its slot's path).
		List<String> raised = interrupts();
		if (sources.length == 0) {
			return raised;
		}
		List<String> ready = new ArrayList<>();
		for (String address : raised) {
			for (String source : sources) {
				if (addressOf(source).equals(address) && !ready.contains(source)) {
					ready.add(source);
				}
			}
		}
		return ready;
	}

	@Override
	public String addressOf(String name) {
		return board.bus().address(name).orElse(name);
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

	@Override
	public void poweroff() {
		throw new UnsupportedOperationException("poweroff() is handled by the program runner");
	}

	@Override
	public void reboot() {
		throw new UnsupportedOperationException("reboot() is handled by the program runner");
	}

	/**
	 * The interrupts pending now: the bus's (it changed since last listed), the console's (keys
	 * wait at a terminal in its group), then each device's, in bus order. A terminal in the
	 * console's group raises the console's, not its own.
	 */
	@Override
	public Optional<String> streamMethod(String address) {
		if (address.equals(VirtualBus.CONSOLE)) {
			return Optional.of("read");
		}
		return board.bus().device(address).map(Device::streamMethod);
	}

	@Override
	public int inboxSize() {
		int size = 0;
		for (MemoryInfo stick : board.chassis().memory()) {
			size += MemoryTier.values()[stick.tier() - 1].inbox();
		}
		return size;
	}

	List<String> interrupts() {
		List<String> pending = new ArrayList<>();
		Bus bus = board.bus();
		ConsoleSplitter console = board.virtualBus().console();
		if (bus.changes() != listedChanges) {
			pending.add(BUS);
		}
		int consoleAt = pending.size();
		boolean[] keys = {false}; // keys wait at a terminal in the console's group: the console's interrupt too
		bus.forEach((address, device) -> {
			if (device.interruptPending()) {
				pending.add(address);
				if (device instanceof Terminal && console.isBound(address)) {
					keys[0] = true;
				}
			}
		});
		if (keys[0]) {
			pending.add(consoleAt, VirtualBus.CONSOLE);
		}
		return pending;
	}
}
