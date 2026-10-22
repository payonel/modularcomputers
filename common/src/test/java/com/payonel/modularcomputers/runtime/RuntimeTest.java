package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Cpu;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.Eeprom;
import com.payonel.modularcomputers.machine.Hardware;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.Program;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Mock programs running on a machine through the motherboard API, as Lua code will. */
class RuntimeTest {
	/** A device with an interrupt line and a little buffer, like a keyboard's. */
	public static final class Sensor implements Device {
		private final List<Long> readings = new ArrayList<>();

		void measure(long value) {
			readings.add(value);
		}

		@Override
		public DeviceType type() {
			return DeviceType.SENSOR;
		}

		@Override
		public boolean interruptPending() {
			return !readings.isEmpty();
		}

		@Method(doc = "read(): the oldest reading, or nil")
		public Result read(Arguments args) {
			return readings.isEmpty() ? Result.ok() : Result.ok(readings.removeFirst());
		}

		@Method
		public Result explode(Arguments args) {
			throw new IllegalStateException("boom");
		}
	}

	private final Terminal display = new Terminal(Resolution.DEFAULT);
	private final Sensor sensor = new Sensor();
	private final boolean[] power = {true};
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of())), () -> power[0]);

	RuntimeTest() {
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("id:screen", display);
		devices.put("id:sensor-1", sensor);
		machine.setDevices(devices);
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
	}

	@AfterEach
	void powerDown() {
		if (machine.isPowered()) {
			machine.pressPower();
		}
	}

	private void run(Program program) {
		machine.board().setEeprom(new Eeprom(program));
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
	}

	@Test
	void callsAreImmediateButAMachineGetsTwentyATickThenWaitsForTheNext() {
		AtomicInteger calls = new AtomicInteger();
		run(board -> {
			for (int i = 0; i < 50; i++) {
				board.invoke("id:screen", "getSize");
				calls.incrementAndGet();
			}
			board.halt();
		});
		assertThat(calls.get()).as("power-on runs the first turn").isEqualTo(20);
		machine.tick();
		assertThat(calls.get()).isEqualTo(40);
		machine.tick();
		assertThat(calls.get()).isEqualTo(50);
		assertThat(machine.runner()).as("the program returned: the CPU halts").isEmpty();
		assertThat(machine.isPowered()).as("power stays on").isTrue();
	}

	@Test
	void devicesAreFoundByAddressInBusOrderWithTheirMethods() {
		List<Object> seen = new ArrayList<>();
		run(board -> {
			seen.add(board.list());
			seen.add(board.type("id:screen").orElseThrow());
			seen.add(board.type("console").orElseThrow());
			seen.add(board.methods("id:screen"));
			seen.add(board.doc("id:sensor-1", "read").orElseThrow());
			seen.add(board.invoke("id:screen", "getSize").values());
			seen.add(board.status().capacity());
			board.halt();
		});
		assertThat(seen).containsExactly(
			List.of(new Board.DeviceInfo("id:screen", "id:screen", "terminal"), new Board.DeviceInfo("id:sensor-1", "id:sensor-1", "sensor")),
			"terminal", "console", List.of("getSize", "read", "write"), "read(): the oldest reading, or nil", List.of(50L, 16L, 50L, 16L), 0);
	}

	@Test
	@SuppressWarnings("unchecked")
	void aCardInACaseSlotAnswersToItsPathAndItsId() {
		machine.setDevices(Map.of("id:screen", display, "exp:0", sensor), Map.of("id:card-7", "exp:0"));
		sensor.measure(7);
		List<Object> seen = new ArrayList<>();
		run(board -> {
			seen.add(board.list());
			seen.add(board.type("id:card-7").orElseThrow());
			seen.add(board.invoke("id:card-7", "read").first());
			board.halt();
		});
		assertThat((List<Object>) seen.get(0)).as("listed once, by its path, with its id").containsExactlyInAnyOrder(
			new Board.DeviceInfo("id:screen", "id:screen", "terminal"), new Board.DeviceInfo("exp:0", "id:card-7", "sensor"));
		assertThat(seen.subList(1, 3)).containsExactly("sensor", 7L);
	}

	@Test
	void deviceFailuresAreSoftAndNeverStopTheMachine() {
		List<String> errors = new ArrayList<>();
		run(board -> {
			errors.add(board.invoke("nowhere", "read").error());
			errors.add(board.invoke("id:sensor-1", "fly").error());
			errors.add(board.invoke("id:screen", "write", true).error());
			errors.add(board.invoke("id:sensor-1", "explode").error());
			errors.add(board.invoke("id:screen", "write", "hi").failed() ? "failed" : "ok");
			board.halt();
		});
		assertThat(errors).containsExactly("no such device: nowhere", "no such method: fly",
			"bad argument #1 (string expected, got boolean)", "device error: java.lang.IllegalStateException: boom", "ok");
		assertThat(display.buffer().row(0)).startsWith("hi");
		assertThat(machine.isPowered()).isTrue();
	}

	@Test
	void waitingEndsOnAnInterruptAndReadingClearsIt() {
		List<Object> seen = new ArrayList<>();
		run(board -> {
			board.list(); // nothing new on the bus
			seen.add(board.waitForInterrupt(60));
			seen.add(board.invoke("id:sensor-1", "read").first());
			seen.add(board.waitForInterrupt(0));
			board.halt();
		});
		machine.tick();
		machine.tick();
		assertThat(seen).as("still waiting").isEmpty();
		sensor.measure(42);
		machine.tick();
		assertThat(seen).containsExactly(List.of("id:sensor-1"), 42L, List.of());
	}

	@Test
	void waitingTimesOut() {
		List<Object> seen = new ArrayList<>();
		run(board -> {
			board.list();
			seen.add(board.waitForInterrupt(0.5)); // 10 ticks
			seen.add(board.uptime());
			board.halt();
		});
		for (int i = 0; i < 9; i++) {
			machine.tick();
		}
		assertThat(seen).isEmpty();
		machine.tick();
		assertThat(seen).containsExactly(List.of(), 10L);
	}

	@Test
	void theBusRaisesAnInterruptWhenDevicesComeOrGo() {
		List<Object> seen = new ArrayList<>();
		run(board -> {
			board.list();
			seen.add(board.waitForInterrupt(60));
			seen.add(board.list().stream().map(Board.DeviceInfo::address).toList());
			board.halt();
		});
		machine.tick();
		machine.setDevices(Map.of("id:screen", display)); // the sensor was unplugged
		machine.tick();
		assertThat(seen).containsExactly(List.of(Board.BUS), List.of("id:screen"));
	}

	@Test
	void theMotherboardChecksItsCpuAndPowerEveryTick() {
		run(board -> board.waitForInterrupt(60));
		assertThat(machine.tick()).isEqualTo(Machine.Supervision.OK);
		power[0] = false;
		assertThat(machine.tick()).isEqualTo(Machine.Supervision.LOST_POWER);
		assertThat(machine.isPowered()).isFalse();
		power[0] = true;
		run(board -> board.waitForInterrupt(60));
		machine.board().setCpu(null);
		assertThat(machine.tick()).isEqualTo(Machine.Supervision.LOST_CPU);
		assertThat(machine.isPowered()).isFalse();
	}

	@Test
	void powerOffStopsAWaitingProgramsThread() throws InterruptedException {
		CountDownLatch ended = new CountDownLatch(1);
		run(board -> {
			try {
				board.waitForInterrupt(600);
			} finally {
				ended.countDown();
			}
		});
		machine.pressPower();
		assertThat(ended.await(5, TimeUnit.SECONDS)).as("the program's thread unwinds").isTrue();
		assertThat(machine.runner()).isEmpty();
	}

	@Test
	void aCrashShowsTheAbortScreenAfterTheCallsBeforeIt() {
		run(board -> {
			for (int i = 0; i < 25; i++) {
				board.uptime();
			}
			throw new IllegalStateException("out of cheese");
		});
		assertThat(display.buffer().isBlank()).as("still running its first turn").isTrue();
		machine.tick();
		assertThat(display.buffer().row(0).strip()).isEqualTo(Cpu.ABORT + Cpu.CRASHED + "out of cheese");
		assertThat(machine.isPowered()).isTrue();
	}

	@Test
	void haltingEndsTheProgramWithPowerOnAndTheScreenKept() {
		List<String> after = new ArrayList<>();
		run(board -> {
			board.invoke("console", "write", "bye\n");
			board.halt();
			after.add("ran on"); // never: halt doesn't return
		});
		machine.tick();
		assertThat(machine.runner()).as("the CPU is halted").isEmpty();
		assertThat(machine.isPowered()).as("power stays on").isTrue();
		assertThat(display.buffer().row(0).strip()).isEqualTo("bye");
		assertThat(after).isEmpty();
	}

	@Test
	void returningWithoutHaltingIsAPanic() {
		run(board -> board.invoke("console", "write", "bye\n"));
		assertThat(machine.runner()).isEmpty();
		assertThat(machine.isPowered()).as("power stays on, as after any abort").isTrue();
		assertThat(display.buffer().row(0).strip()).isEqualTo(Cpu.ABORT + Cpu.RETURNED);
	}

	@Test
	void argumentsConvertLikeLua() {
		Arguments args = new Arguments(java.util.Arrays.asList(3, 2.0, 2.5, "7", null, true));
		assertThat(args.checkInteger(0)).isEqualTo(3L);
		assertThat(args.checkInteger(1)).as("a float with an integer value").isEqualTo(2L);
		assertThat(args.checkNumber(2)).isEqualTo(2.5);
		assertThat(args.checkString(0)).as("numbers convert to strings").isEqualTo("3");
		assertThat(args.optInteger(4, 9)).isEqualTo(9L);
		assertThat(args.checkBoolean(5)).isTrue();
		assertThat(Values.typeName(args.get(4))).isEqualTo("nil");
		assertThat(Values.normalize(List.of(1, 2.5f))).isEqualTo(List.of(1L, 2.5));
	}
}
