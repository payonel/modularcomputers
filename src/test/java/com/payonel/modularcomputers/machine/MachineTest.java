package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.Keyboard;
import com.payonel.modularcomputers.display.Display;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachineTest {
	private final Display display = new Display(Resolution.DEFAULT);
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of(new Keyboard(), display, new Disk()))));

	private Machine withParts(boolean cpu, Program program) {
		machine.board().setCpu(cpu ? new Cpu() : null);
		machine.board().setEeprom(program == null ? null : new Eeprom(program));
		return machine;
	}

	private TextBuffer screen() {
		return display.buffer();
	}

	@Test
	void allInOneHasSolderedUnlimitedMemory() {
		assertThat(Hardware.allInOne().parts()).singleElement().satisfies(part -> {
			assertThat(part.kind()).isEqualTo(PartKind.MEMORY);
			assertThat(part.soldered()).isTrue();
			assertThat(part.isUnlimited()).isTrue();
		});
	}

	@Test
	void startsOffWithBlankDisplay() {
		withParts(true, new HelloWorldProgram());
		assertThat(machine.isPowered()).isFalse();
		assertThat(screen().isBlank()).isTrue();
	}

	@Test
	void withoutCpuThePowerSwitchWorksButNothingRuns() {
		int[] runs = {0};
		withParts(false, board -> runs[0]++);
		machine.setPowered(true);
		assertThat(machine.isPowered()).isTrue();
		assertThat(runs[0]).isZero();
		assertThat(screen().isBlank()).isTrue();
	}

	@Test
	void powerOnRunsTheEepromProgramOnce() {
		int[] runs = {0};
		withParts(true, board -> runs[0]++);
		machine.setPowered(true);
		machine.setPowered(true);
		assertThat(runs[0]).isEqualTo(1);
		machine.togglePower();
		machine.togglePower();
		assertThat(runs[0]).as("each power-on runs it again").isEqualTo(2);
	}

	@Test
	void theProgramGetsTheMotherboard() {
		Motherboard[] seen = {null};
		withParts(true, board -> seen[0] = board);
		machine.setPowered(true);
		assertThat(seen[0]).isSameAs(machine.board());
		assertThat(seen[0].bus().devices()).hasSize(3);
	}

	@Test
	void installingACpuWhilePoweredWaitsForTheNextPowerOn() {
		int[] runs = {0};
		withParts(false, board -> runs[0]++);
		machine.setPowered(true);
		machine.board().setCpu(new Cpu());
		assertThat(runs[0]).isZero();
		machine.togglePower();
		machine.togglePower();
		assertThat(runs[0]).isEqualTo(1);
	}

	@Test
	void noEepromShowsTheAbortScreen() {
		withParts(true, null);
		machine.setPowered(true);
		assertThat(machine.isPowered()).as("power stays on").isTrue();
		assertThat(screen().row(0).strip()).isEqualTo(Cpu.ABORT + Cpu.NO_EEPROM);
		assertThat(screen().foreground(0, 0)).isEqualTo(Cpu.ABORT_FOREGROUND);
		// The whole screen turns soft gray, not just the text.
		Resolution size = screen().resolution();
		for (int row = 0; row < size.rows(); row++) {
			for (int column = 0; column < size.columns(); column++) {
				assertThat(screen().background(column, row)).isEqualTo(Cpu.ABORT_BACKGROUND);
			}
		}
	}

	@Test
	void aCrashingProgramShowsTheAbortScreen() {
		withParts(true, board -> {
			board.virtualBus().console().print("booting");
			throw new IllegalStateException("out of cheese");
		});
		machine.setPowered(true);
		assertThat(machine.isPowered()).as("power stays on").isTrue();
		assertThat(screen().row(0).strip()).as("the abort screen replaces what was there")
			.isEqualTo(Cpu.ABORT + Cpu.CRASHED + "out of cheese");
		assertThat(screen().background(0, 5)).isEqualTo(Cpu.ABORT_BACKGROUND);
	}

	@Test
	void helloWorldProgramPrintsThroughTheConsole() {
		withParts(true, new HelloWorldProgram());
		machine.setPowered(true);
		assertThat(screen().row(0).strip()).isEqualTo(HelloWorldProgram.GREETING);
		assertThat(screen().background(0, 0)).isEqualTo(TextBuffer.DEFAULT_BACKGROUND);
		assertThat(machine.board().virtualBus().console().row()).as("cursor on the next line").isEqualTo(1);
	}

	@Test
	void powerOffBlanksDisplaysAndResetsTheConsole() {
		withParts(true, null);
		machine.setPowered(true);
		machine.setPowered(false);
		assertThat(screen().isBlank()).isTrue();
		var console = machine.board().virtualBus().console();
		assertThat(console.row()).isZero();
		assertThat(console.background()).isEqualTo(TextBuffer.DEFAULT_BACKGROUND);
		machine.board().setEeprom(new Eeprom(new HelloWorldProgram()));
		machine.setPowered(true);
		assertThat(screen().row(0).strip()).as("prints at the top again").isEqualTo(HelloWorldProgram.GREETING);
		assertThat(screen().background(0, 0)).isEqualTo(TextBuffer.DEFAULT_BACKGROUND);
	}

	@Test
	void restoringPowerStateDoesNotBoot() {
		int[] runs = {0};
		withParts(true, board -> runs[0]++);
		machine.restorePowered(true);
		assertThat(machine.isPowered()).isTrue();
		assertThat(runs[0]).isZero();
	}
}
