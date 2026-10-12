package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.power.PowerUnits;
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
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		machine.board().setEeprom(new Eeprom(program));
		return machine;
	}

	private static final Program HALT = board -> {
		board.invoke("console", "write", "hi\n");
		board.halt();
	};

	@Test
	void aDeviceOwesItsDrawForTheTimeItHadPowerAndItsWork() {
		BusPower power = new BusPower(10);
		power.charge(5);
		assertThat(power.collect(0)).as("no power, nothing owed").isZero();
		power.supply("a");
		assertThat(power.collect(100)).as("from the first collection on").isZero();
		power.charge(1);
		power.charge(1);
		assertThat(power.collect(120)).as("20 ticks at 10, and two writes").isEqualTo(202);
		assertThat(power.collect(120)).as("paid up").isZero();
	}

	@Test
	void aHaltedCpuDrawsNothingButItsTerminalsDo() {
		Machine machine = machine(HALT);
		machine.setDevices(Map.of("id:screen", screen));
		machine.pressPower();
		assertThat(machine.runner()).as("halted").isEmpty();
		machine.powerUse(1000);
		assertThat(machine.powerUse(1020)).as("a terminal for 20 ticks").isEqualTo(20 * PowerUnits.TERMINAL);
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
		assertThat(machine.powerUse(0)).as("ran 6 ticks (20 calls each), then waited 4").isEqualTo(6 * CpuTier.TIER_3.running() + 4 * CpuTier.TIER_3.waiting());
	}

	@Test
	void eachCpuTierDrawsItsOwnRunningAndWaiting() {
		for (CpuTier tier : CpuTier.values()) {
			Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of())));
			machine.board().setCpu(new Cpu(tier));
			machine.board().setEeprom(new Eeprom(board -> {
				board.list();
				board.waitForInterrupt(3600);
			}));
			machine.pressPower(); // executes the first tick, then waits
			for (int i = 0; i < 10; i++) {
				machine.tick();
			}
			assertThat(machine.powerUse(0)).as(tier.name()).isEqualTo(tier.running() + 10L * tier.waiting());
		}
		assertThat(CpuTier.TIER_1.waiting()).as("lower tiers idle hungrier").isGreaterThan(CpuTier.TIER_3.waiting());
		assertThat(CpuTier.TIER_1.running()).as("and run leaner").isLessThan(CpuTier.TIER_3.running());
	}

	@Test
	void disksDrawLessAtHigherTiers() {
		for (DiskTier tier : DiskTier.values()) {
			Machine machine = machine(HALT);
			machine.setDevices(Map.of("sata:0", new Disk(tier)));
			machine.pressPower();
			machine.powerUse(100);
			assertThat(machine.powerUse(120)).as(tier.name()).isEqualTo(20L * tier.draw());
		}
		assertThat(DiskTier.TIER_3.draw()).isLessThan(DiskTier.TIER_1.draw());
		assertThat(DiskTier.TIER_3.bytes()).isGreaterThan(DiskTier.TIER_1.bytes());
	}

	@Test
	void theLoadIsWhatItDrawsNow() {
		Machine machine = machine(board -> {
			board.list();
			for (int i = 0; i < 60; i++) {
				board.uptime();
			}
			board.waitForInterrupt(3600);
		});
		machine.setDevices(Map.of("id:screen", screen));
		assertThat(machine.currentDraw()).as("off").isZero();
		machine.pressPower();
		machine.tick();
		assertThat(machine.currentDraw()).as("running").isEqualTo(CpuTier.TIER_3.running() + Terminal.DRAW);
		for (int i = 0; i < 5; i++) {
			machine.tick();
		}
		assertThat(machine.currentDraw()).as("waiting, at once: no average").isEqualTo(CpuTier.TIER_3.waiting() + Terminal.DRAW);
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
		assertThat(machine.powerUse(0)).as("one tick of CPU, and 8 writes").isEqualTo(CpuTier.TIER_3.running() + 8 * PowerUnits.TERMINAL_WRITE);
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
		assertThat(first.powerUse(40)).isEqualTo(40 * PowerUnits.TERMINAL);
		assertThat(second.powerUse(40)).as("already paid").isZero();

		first.pressPower(); // off
		assertThat(screen.buffer().isBlank()).as("the other computer still powers it").isFalse();
		assertThat(second.powerUse(60)).as("now the other pays").isEqualTo(20 * PowerUnits.TERMINAL);
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
