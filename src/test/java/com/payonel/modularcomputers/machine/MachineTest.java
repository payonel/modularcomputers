package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachineTest {
	private final Display display = new Display(Resolution.DEFAULT);
	private final Bus bus = new Bus(List.of(new Keyboard(), display, new Disk()));

	private Machine machine(Eeprom eeprom) {
		return new Machine(new Motherboard(Hardware.allInOne(), bus, eeprom));
	}

	@Test
	void allInOneHasSolderedUnlimitedCpuAndMemory() {
		assertThat(Hardware.allInOne().parts()).extracting(Part::kind).containsExactlyInAnyOrder(PartKind.CPU, PartKind.MEMORY);
		assertThat(Hardware.allInOne().parts()).allSatisfy(part -> {
			assertThat(part.soldered()).isTrue();
			assertThat(part.isUnlimited()).isTrue();
		});
	}

	@Test
	void startsOffWithBlankDisplay() {
		Machine machine = machine(new Eeprom(new HelloWorldProgram()));
		assertThat(machine.isRunning()).isFalse();
		assertThat(display.buffer().isBlank()).isTrue();
	}

	@Test
	void powerOnRunsTheEepromProgramOnce() {
		int[] runs = {0};
		Machine machine = machine(new Eeprom(board -> runs[0]++));
		machine.setRunning(true);
		machine.setRunning(true);
		assertThat(runs[0]).isEqualTo(1);
		machine.togglePower();
		machine.togglePower();
		assertThat(runs[0]).as("each power-on runs it again").isEqualTo(2);
	}

	@Test
	void theProgramGetsTheMotherboard() {
		Motherboard[] seen = {null};
		Machine machine = machine(new Eeprom(board -> seen[0] = board));
		machine.setRunning(true);
		assertThat(seen[0]).isSameAs(machine.board());
		assertThat(seen[0].bus().devices()).hasSize(3);
	}

	@Test
	void emptyEepromSlotIsReportedOnTheConsole() {
		Machine machine = machine(null);
		machine.setRunning(true);
		assertThat(machine.isRunning()).as("power stays on").isTrue();
		assertThat(display.buffer().row(0).strip()).isEqualTo(Cpu.NO_EEPROM);
	}

	@Test
	void aCrashingProgramIsReportedOnTheConsole() {
		Machine machine = machine(new Eeprom(board -> {
			board.virtualBus().console().print("booting");
			throw new IllegalStateException("out of cheese");
		}));
		machine.setRunning(true);
		assertThat(machine.isRunning()).as("power stays on").isTrue();
		assertThat(display.buffer().row(0).strip()).isEqualTo("booting");
		assertThat(display.buffer().row(1).strip()).isEqualTo(Cpu.CRASHED + "out of cheese");
	}

	@Test
	void helloWorldProgramPrintsThroughTheConsole() {
		Machine machine = machine(new Eeprom(new HelloWorldProgram()));
		machine.setRunning(true);
		assertThat(display.buffer().row(0).strip()).isEqualTo(HelloWorldProgram.GREETING);
		assertThat(machine.board().virtualBus().console().row()).as("cursor on the next line").isEqualTo(1);
	}

	@Test
	void powerOffBlanksDisplaysAndResetsTheConsole() {
		Machine machine = machine(new Eeprom(new HelloWorldProgram()));
		machine.setRunning(true);
		machine.setRunning(false);
		assertThat(display.buffer().isBlank()).isTrue();
		assertThat(machine.board().virtualBus().console().row()).isZero();
		machine.setRunning(true);
		assertThat(display.buffer().row(0).strip()).as("prints at the top again").isEqualTo(HelloWorldProgram.GREETING);
	}

	@Test
	void restoringPowerStateDoesNotBoot() {
		int[] runs = {0};
		Machine machine = machine(new Eeprom(board -> runs[0]++));
		machine.restoreRunning(true);
		assertThat(machine.isRunning()).isTrue();
		assertThat(runs[0]).isZero();
	}
}
