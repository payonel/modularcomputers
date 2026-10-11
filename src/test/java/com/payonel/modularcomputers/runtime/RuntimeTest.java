package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.Device;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.machine.Cpu;
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

	private final Display display = new Display(Resolution.DEFAULT);
	private final Sensor sensor = new Sensor();
	private final boolean[] power = {true};
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of())), () -> power[0]);

	RuntimeTest() {
		Map<String, Device> devices = new LinkedHashMap<>();
		devices.put("edp", display);
		devices.put("id:sensor-1", sensor);
		machine.setDevices(devices);
		machine.board().setCpu(new Cpu());
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
				board.invoke("edp", "getResolution");
				calls.incrementAndGet();
			}
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
			seen.add(board.type("edp").orElseThrow());
			seen.add(board.type("console").orElseThrow());
			seen.add(board.methods("edp"));
			seen.add(board.doc("id:sensor-1", "read").orElseThrow());
			seen.add(board.invoke("edp", "getResolution").values());
			seen.add(board.status().capacity());
		});
		assertThat(seen).containsExactly(List.of("edp", "id:sensor-1"), "display", "console",
			List.of("fill", "get", "getResolution", "set"), "read(): the oldest reading, or nil", List.of(50L, 16L), 0);
	}

	@Test
	@SuppressWarnings("unchecked")
	void aCardInACaseSlotAnswersToItsPathAndItsId() {
		machine.setDevices(Map.of("edp", display, "exp:0", sensor), Map.of("id:card-7", "exp:0"));
		sensor.measure(7);
		List<Object> seen = new ArrayList<>();
		run(board -> {
			seen.add(board.list());
			seen.add(board.type("id:card-7").orElseThrow());
			seen.add(board.invoke("id:card-7", "read").first());
		});
		assertThat((List<Object>) seen.get(0)).as("listed once, by its path").containsExactlyInAnyOrder("edp", "exp:0");
		assertThat(seen.subList(1, 3)).containsExactly("sensor", 7L);
	}

	@Test
	void deviceFailuresAreSoftAndNeverStopTheMachine() {
		List<String> errors = new ArrayList<>();
		run(board -> {
			errors.add(board.invoke("nowhere", "read").error());
			errors.add(board.invoke("id:sensor-1", "fly").error());
			errors.add(board.invoke("edp", "set", "one", 1, "x").error());
			errors.add(board.invoke("id:sensor-1", "explode").error());
			errors.add(board.invoke("edp", "set", 1, 1, "hi").failed() ? "failed" : "ok");
		});
		assertThat(errors).containsExactly("no such device: nowhere", "no such method: fly",
			"bad argument #1 (number expected, got string)", "device error: java.lang.IllegalStateException: boom", "ok");
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
			seen.add(board.list());
		});
		machine.tick();
		machine.setDevices(Map.of("edp", display)); // the sensor was unplugged
		machine.tick();
		assertThat(seen).containsExactly(List.of(Board.BUS), List.of("edp"));
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
