package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Bus power: devices have power while a running computer has them on its bus, owe what they
 * draw, and are paid for once, by whichever computer collects first. Nothing ticks for it.
 */
class BusPowerTest {
	private final Terminal screen = new Terminal(Resolution.DEFAULT);

	private static Machine machine(Program program) {
		Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of())));
		machine.board().setCpu(new Cpu());
		machine.board().setEeprom(new Eeprom(program));
		return machine;
	}

	private static final Program HALT = board -> {
		board.invoke("console", "write", "hi\n");
		board.halt();
	};

	@Test
	void aDeviceOwesItsDrawForTheTimeItHadPowerAndItsWork() {
		BusPower power = new BusPower(0.5);
		power.charge(10);
		assertThat(power.collect(0)).as("no power, nothing owed").isZero();
		power.supply("a");
		assertThat(power.collect(100)).as("from the first collection on").isZero();
		power.charge(0.25);
		power.charge(0.25);
		assertThat(power.collect(120)).as("20 ticks at a half, and half a tick of work").isEqualTo(10);
		assertThat(power.collect(121)).as("the half left over, and another half").isEqualTo(1);
		assertThat(power.collect(122)).isZero();
	}

	@Test
	void aHaltedCpuDrawsNothingButItsTerminalsDo() {
		Machine machine = machine(HALT);
		machine.setDevices(Map.of("id:screen", screen));
		machine.pressPower();
		assertThat(machine.runner()).as("halted").isEmpty();
		machine.powerUse(1000);
		assertThat(machine.powerUse(1020)).as("half a CPU for 20 ticks").isEqualTo(10);
	}

	@Test
	void aRunningCpuDrawsOneATickAndNothingWhileItWaits() {
		Machine machine = machine(board -> {
			for (int i = 0; i < 100; i++) {
				board.uptime();
			}
			board.list();
			board.waitForInterrupt(3600);
		});
		machine.pressPower(); // turn 1
		for (int i = 0; i < 9; i++) {
			machine.tick();
		}
		assertThat(machine.powerUse(0)).as("ran 5 ticks (20 calls each), then waited").isEqualTo(6);
	}

	@Test
	void writesCostPower() {
		Machine machine = machine(board -> {
			for (int i = 0; i < 8; i++) {
				board.invoke("id:screen", "write", "x");
			}
			board.halt();
		});
		machine.setDevices(Map.of("id:screen", screen));
		machine.pressPower();
		assertThat(machine.powerUse(0)).as("one tick of CPU, and 8 writes at a quarter each").isEqualTo(1 + 2);
	}

	@Test
	void aSharedTerminalIsPaidForOnceAndStaysLitWhileAnyComputerSuppliesIt() {
		Machine first = machine(HALT);
		Machine second = machine(HALT);
		first.setDevices(Map.of("id:screen", screen));
		second.setDevices(Map.of("id:screen", screen));
		first.pressPower();
		second.pressPower();
		first.powerUse(0);
		second.powerUse(0);
		assertThat(first.powerUse(40)).isEqualTo(20);
		assertThat(second.powerUse(40)).as("already paid").isZero();

		first.pressPower(); // off
		assertThat(screen.buffer().isBlank()).as("the other computer still powers it").isFalse();
		assertThat(second.powerUse(60)).as("now the other pays").isEqualTo(10);
		second.pressPower();
		assertThat(screen.buffer().isBlank()).as("no power: dark").isTrue();
	}

	@Test
	void aComputerThatUnloadsOrGoesAwayLeavesItsTerminalsDark() {
		Machine machine = machine(HALT);
		machine.setDevices(Map.of("id:screen", screen));
		machine.pressPower();
		assertThat(screen.buffer().isBlank()).isFalse();
		machine.unload();
		assertThat(screen.buffer().isBlank()).isTrue();
		assertThat(screen.power().powered()).isFalse();
	}

	@Test
	void aTerminalThatLeavesTheBusLosesPowerAndOneThatJoinsGetsIt() {
		Machine machine = machine(HALT);
		machine.pressPower();
		machine.setDevices(Map.of("id:screen", screen));
		assertThat(screen.power().powered()).isTrue();
		assertThat(screen.buffer().row(0).strip()).as("the console redrawn on it").isEqualTo("hi");
		machine.setDevices(Map.of());
		assertThat(screen.power().powered()).isFalse();
		assertThat(screen.buffer().isBlank()).isTrue();
	}
}
