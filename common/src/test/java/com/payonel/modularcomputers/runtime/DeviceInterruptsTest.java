package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.device.Cards;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.MotionSensorCard;
import com.payonel.modularcomputers.device.NetworkCard;
import com.payonel.modularcomputers.device.RedstoneCard;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.device.WeatherCard;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.TestBus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every device that raises interrupts wakes a program waiting on it, by whichever of its names the
 * program used (its id, as mcos names every device, or its slot's path), within a tick, in a slot
 * as a case puts it. Every card kind is checked, so a new one is too: a card with an interrupt line
 * and no way here to give it data fails, until it gets one in {@link #STIMULI}.
 */
class DeviceInterruptsTest {
	/** How long the program waits, in seconds: far longer than a wake may take. */
	private static final int WAIT_SECONDS = 10;

	/** Where the network cards are (any key: a level). */
	private static final Object WORLD = new Object();

	/** Gives a device of each type something for its program, as the world would. */
	private static final Map<Class<?>, Consumer<Device>> STIMULI = Map.of(
		RedstoneCard.class, device -> ((RedstoneCard) device).sense(0, 7),
		WeatherCard.class, device -> {
			((WeatherCard) device).sense("clear");
			((WeatherCard) device).sense("rain"); // (a change is an event, not the first look)
		},
		MotionSensorCard.class, device -> ((MotionSensorCard) device).sense(Map.of("mob", "minecraft:pig", "x", 1.0, "y", 0.0, "z", 0.0)),
		com.payonel.modularcomputers.device.SoundCard.class, device -> {
			var card = (com.payonel.modularcomputers.device.SoundCard) device;
			card.write(new com.payonel.modularcomputers.runtime.Arguments(List.of("x".repeat(20_000)))); // more than fits: refused
			card.play(0);
			card.play(20); // a second played: room for the writer
		},
		com.payonel.modularcomputers.device.InternetCard.class, device -> {
			var card = (com.payonel.modularcomputers.device.InternetCard) device;
			card.request(new com.payonel.modularcomputers.runtime.Arguments(List.of("http://127.0.0.1/"))); // (refused, on its thread: an event)
			long until = System.nanoTime() + 5_000_000_000L;
			while (!card.interruptPending() && System.nanoTime() < until) {
				Thread.onSpinWait();
			}
			card.tick(); // (its block's tick: its interrupt)
		},
		com.payonel.modularcomputers.device.AudioSensorCard.class, device -> ((com.payonel.modularcomputers.device.AudioSensorCard) device)
			.hear(Map.of("sound", "minecraft:entity.pig.ambient", "x", 1.0, "y", 0.0, "z", 0.0)),
		NetworkCard.class, device -> {
			NetworkCard card = (NetworkCard) device;
			card.place(WORLD, net.minecraft.core.BlockPos.ZERO, 0);
			NetworkCard peer = new NetworkCard(card.kind());
			peer.idFrom(java.util.UUID::randomUUID);
			peer.place(WORLD, net.minecraft.core.BlockPos.ZERO, 0);
			peer.send(new com.payonel.modularcomputers.runtime.Arguments(List.of(card.address(), "frame")));
		},
		Terminal.class, device -> ((Terminal) device).press("a"),
		Disk.class, device -> ((Disk) device).mkdir(new com.payonel.modularcomputers.runtime.Arguments(List.of("/made"))));

	private static final String BY_ID = "by its id";
	private static final String BY_SLOT = "by its slot";

	private final Terminal screen = new Terminal(Resolution.DEFAULT);
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus()));
	private final ConcurrentLinkedQueue<Runnable> server = new ConcurrentLinkedQueue<>();

	/** A device of every type (a new one each time: a test changes it), and the connector a case puts it on. */
	private static List<Map.Entry<String, java.util.function.Supplier<Device>>> devices() {
		List<Map.Entry<String, java.util.function.Supplier<Device>>> devices = new ArrayList<>();
		for (CardKind kind : CardKind.values()) {
			devices.add(Map.entry(Motherboard.exp(0), () -> Cards.make(kind)));
		}
		devices.add(Map.entry(Motherboard.exp(0), () -> new Terminal(Resolution.DEFAULT)));
		devices.add(Map.entry(Motherboard.sata(0), () -> new Disk(DiskTier.TIER_1, folder())));
		return devices;
	}

	static Stream<Arguments> wakes() {
		return devices().stream().filter(entry -> entry.getValue().get().interruptLine() != null)
			.flatMap(entry -> Stream.of(BY_ID, BY_SLOT).map(by -> Arguments.of(name(entry.getValue().get()), by, entry.getKey(), entry.getValue())));
	}

	private static Path folder() {
		try {
			Path folder = java.nio.file.Files.createTempDirectory("disk");
			folder.toFile().deleteOnExit();
			return folder;
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private static String name(Device device) {
		return device instanceof com.payonel.modularcomputers.device.Card card ? card.kind().name() : device.getClass().getSimpleName();
	}

	@AfterEach
	void powerOff() {
		if (machine.isPowered()) {
			machine.pressPower();
		}
	}

	@Test
	void everyDeviceWithAnInterruptHasAStimulusHere() {
		for (var entry : devices()) {
			Device device = entry.getValue().get();
			if (device.interruptLine() != null && !STIMULI.containsKey(device.getClass())) {
				fail(name(device) + " raises interrupts: give it data in DeviceInterruptsTest.STIMULI");
			}
		}
	}

	@ParameterizedTest(name = "{0}, waited on {1}")
	@MethodSource("wakes")
	void aDeviceWithDataWakesAProgramWaitingOnItByAnyName(String type, String by, String connector, java.util.function.Supplier<Device> make) {
		Device device = make.get();
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		machine.setServer(server::add);
		TestBus bus = new TestBus(machine);
		bus.cable("id:screen", screen);
		String id = bus.plug(connector, device);
		String name = by.equals(BY_ID) ? id : connector;
		bus.eeprom(new Eeprom("""
			local got = board.waitForInterrupt(%d, "%s")
			board.invoke("console", "write", tostring(got[1]))
			board.halt()
			""".formatted(WAIT_SECONDS, name)));
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
		settle(() -> machine.runner().map(r -> r.state() == Runner.State.WAITING).orElse(false));
		STIMULI.get(device.getClass()).accept(device);
		serve();
		machine.tick(); // the tick after its data came: the wait is answered in it
		assertThat(machine.runner().map(Runner::state)).as("woken at the next tick").isNotEqualTo(java.util.Optional.of(Runner.State.WAITING));
		settle(() -> machine.runner().isEmpty());
		assertThat(screen.buffer().row(0).strip()).as("answered by the name it was asked by").isEqualTo(name);
	}

	/** The tasks the machine asked the server thread for. */
	private void serve() {
		Runnable task;
		while ((task = server.poll()) != null) {
			task.run();
		}
	}

	/** Plays the server thread until {@code done}: its asked-for tasks, and a tick a millisecond. */
	private void settle(java.util.function.BooleanSupplier done) {
		long end = System.nanoTime() + 10_000_000_000L;
		while (!done.getAsBoolean()) {
			assertThat(System.nanoTime()).as("settled in time").isLessThan(end);
			serve();
			machine.tick();
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
