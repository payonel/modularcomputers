package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.display.Resolution;
import org.junit.jupiter.api.Test;

class MachineTest {
	private final Machine machine = new Machine(Hardware.allInOne(), Resolution.DEFAULT);

	@Test
	void allInOneHasSolderedUnlimitedCpuMemoryAndDisk() {
		Hardware hardware = Hardware.allInOne();
		assertThat(hardware.parts()).extracting(Part::kind)
			.containsExactlyInAnyOrder(PartKind.CPU, PartKind.MEMORY, PartKind.STORAGE);
		assertThat(hardware.parts()).allSatisfy(part -> {
			assertThat(part.soldered()).isTrue();
			assertThat(part.isUnlimited()).isTrue();
		});
		assertThat(hardware.has(PartKind.EEPROM)).isFalse();
	}

	@Test
	void startsOffWithBlankDisplay() {
		assertThat(machine.isRunning()).isFalse();
		assertThat(machine.display().isBlank()).isTrue();
	}

	@Test
	void powerOnPrintsGreetingOnTheFirstRow() {
		machine.togglePower();
		assertThat(machine.isRunning()).isTrue();
		assertThat(machine.display().row(0)).startsWith(Machine.GREETING).hasSize(Resolution.DEFAULT.columns());
		assertThat(machine.display().row(0).strip()).isEqualTo("hello world");
		for (int row = 1; row < Resolution.DEFAULT.rows(); row++) {
			assertThat(machine.display().row(row).isBlank()).isTrue();
		}
	}

	@Test
	void powerOffBlanksTheDisplay() {
		machine.togglePower();
		machine.togglePower();
		assertThat(machine.isRunning()).isFalse();
		assertThat(machine.display().isBlank()).isTrue();
	}

	@Test
	void settingTheSameStateTwiceKeepsTheDisplay() {
		machine.setRunning(true);
		machine.display().write(0, 1, "x");
		machine.setRunning(true);
		assertThat(machine.display().get(0, 1)).isEqualTo('x');
	}
}
