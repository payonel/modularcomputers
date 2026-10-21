package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.device.Disk;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.display.TextBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachineTest {
	private final Terminal display = new Terminal(Resolution.DEFAULT);
	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of(new Disk(DiskTier.TIER_1), display, new Disk(DiskTier.TIER_1)))));

	/** The tests' own program, not the mod's stand-in firmware (which changes). */
	private static final String GREETING = "hello world";
	private static final Program HELLO = board -> {
		board.invoke("console", "write", GREETING + "\n");
		board.halt();
	};

	private Machine withParts(boolean cpu, Program program) {
		machine.board().setCpu(cpu ? new Cpu(CpuTier.TIER_3) : null);
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
		withParts(true, HELLO);
		assertThat(machine.isPowered()).isFalse();
		assertThat(screen().isBlank()).isTrue();
	}

	@Test
	void withoutCpuPressingPowerIsRefused() {
		int[] runs = {0};
		withParts(false, board -> { runs[0]++; board.halt(); });
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.NO_CPU);
		assertThat(machine.isPowered()).isFalse();
		assertThat(runs[0]).isZero();
		assertThat(screen().isBlank()).isTrue();
	}

	@Test
	void withoutPowerPressingPowerDoesNothingEvenWithoutACpu() {
		boolean[] power = {false};
		Machine unpowered = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of(display))), () -> power[0]);
		assertThat(unpowered.pressPower()).isEqualTo(Machine.PowerPress.NO_POWER);
		unpowered.board().setCpu(new Cpu(CpuTier.TIER_3));
		unpowered.board().setEeprom(new Eeprom(HELLO));
		assertThat(unpowered.pressPower()).isEqualTo(Machine.PowerPress.NO_POWER);
		assertThat(unpowered.isPowered()).isFalse();
		assertThat(screen().isBlank()).isTrue();

		power[0] = true;
		assertThat(unpowered.powerLost()).as("off already").isFalse();
		assertThat(unpowered.pressPower()).isEqualTo(Machine.PowerPress.ON);
		assertThat(unpowered.powerLost()).as("still has power").isFalse();
		assertThat(screen().isBlank()).isFalse();
		power[0] = false;
		assertThat(unpowered.powerLost()).as("it was running").isTrue();
		assertThat(unpowered.isPowered()).isFalse();
		assertThat(screen().isBlank()).as("the display goes dark").isTrue();
	}

	@Test
	void pressingPowerTogglesAndEachPowerOnRunsTheProgram() {
		int[] runs = {0};
		withParts(true, board -> { runs[0]++; board.halt(); });
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.ON);
		assertThat(machine.isPowered()).isTrue();
		assertThat(runs[0]).isEqualTo(1);
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.OFF);
		assertThat(machine.isPowered()).isFalse();
		machine.pressPower();
		assertThat(runs[0]).as("each power-on runs it again").isEqualTo(2);
	}

	@Test
	void theProgramGetsTheMotherboardApi() {
		List<List<String>> seen = new java.util.ArrayList<>();
		withParts(true, board -> { seen.add(board.list().stream().map(com.payonel.modularcomputers.runtime.Board.DeviceInfo::address).toList()); board.halt(); });
		machine.pressPower();
		assertThat(seen).containsExactly(List.of("device:0", "device:1", "device:2"));
	}

	@Test
	void installingACpuDoesNotTurnTheMachineOn() {
		int[] runs = {0};
		withParts(false, board -> { runs[0]++; board.halt(); });
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		assertThat(machine.isPowered()).isFalse();
		assertThat(runs[0]).isZero();
	}

	@Test
	void removingTheCpuFromARunningMachineCutsPower() {
		withParts(true, HELLO);
		machine.pressPower();
		machine.board().setCpu(null);
		assertThat(machine.cpuRemoved()).as("it was running").isTrue();
		assertThat(machine.isPowered()).isFalse();
		assertThat(screen().isBlank()).as("the display goes dark").isTrue();
		assertThat(machine.cpuRemoved()).as("already off").isFalse();
	}

	@Test
	void cpuRemovedIsANoOpWhileTheCpuIsStillThere() {
		withParts(true, HELLO);
		machine.pressPower();
		assertThat(machine.cpuRemoved()).isFalse();
		assertThat(machine.isPowered()).isTrue();
	}

	@Test
	void noEepromShowsTheAbortScreen() {
		withParts(true, null);
		machine.pressPower();
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
			board.invoke("console", "write", "booting\n");
			throw new IllegalStateException("out of cheese");
		});
		machine.pressPower();
		assertThat(machine.isPowered()).as("power stays on").isTrue();
		assertThat(screen().row(0).strip()).as("the abort screen replaces what was there")
			.isEqualTo(Cpu.ABORT + Cpu.CRASHED + "out of cheese");
		assertThat(screen().background(0, 5)).isEqualTo(Cpu.ABORT_BACKGROUND);
	}

	@Test
	void aProgramPrintsThroughTheConsole() {
		withParts(true, HELLO);
		machine.pressPower();
		assertThat(screen().row(0).strip()).isEqualTo(GREETING);
		assertThat(screen().background(0, 0)).isEqualTo(TextBuffer.DEFAULT_BACKGROUND);
		assertThat(machine.board().virtualBus().console().decoder().row()).as("cursor on the next line").isEqualTo(1);
	}

	@Test
	void powerOffBlanksDisplaysAndResetsTheConsole() {
		withParts(true, null);
		machine.pressPower();
		machine.pressPower();
		assertThat(screen().isBlank()).isTrue();
		var console = machine.board().virtualBus().console();
		assertThat(console.decoder().row()).isZero();
		assertThat(console.screen().isBlank()).isTrue();
		machine.board().setEeprom(new Eeprom(HELLO));
		machine.pressPower();
		assertThat(screen().row(0).strip()).as("prints at the top again").isEqualTo(GREETING);
		assertThat(screen().background(0, 0)).isEqualTo(TextBuffer.DEFAULT_BACKGROUND);
	}

	@Test
	void restoringPowerStateDoesNotBoot() {
		int[] runs = {0};
		withParts(true, board -> { runs[0]++; board.halt(); });
		machine.restorePowered(true);
		assertThat(machine.isPowered()).isTrue();
		assertThat(runs[0]).isZero();
	}

	@Test
	void aScreenPluggedIntoARunningMachineShowsTheConsole() {
		withParts(true, HELLO);
		machine.pressPower();
		Terminal plugged = new Terminal(Resolution.DEFAULT);
		List<com.payonel.modularcomputers.bus.Device> devices = new java.util.ArrayList<>(machine.board().bus().devices());
		devices.add(plugged);
		machine.setDevices(devices);
		assertThat(plugged.buffer().rows()).isEqualTo(screen().rows());
		assertThat(plugged.buffer().isBlank()).isFalse();
	}

	@Test
	void aScreenPluggedIntoAnOffMachineStaysAsItWas() {
		withParts(true, HELLO);
		Terminal plugged = new Terminal(Resolution.DEFAULT);
		plugged.buffer().write(0, 0, "other");
		machine.setDevices(List.of(display, plugged));
		assertThat(plugged.buffer().row(0)).startsWith("other");
	}

	@Test
	void unpluggingAScreenFromARunningMachineBlanksIt() {
		withParts(true, HELLO);
		machine.pressPower();
		Terminal plugged = new Terminal(Resolution.DEFAULT);
		machine.setDevices(List.of(display, plugged));
		assertThat(plugged.buffer().isBlank()).isFalse();
		machine.setDevices(List.of(display));
		assertThat(plugged.buffer().isBlank()).isTrue();
		assertThat(screen().isBlank()).isFalse();
	}

	@Test
	void theConsoleRemembersWhatItShowedWhileHeadless() {
		withParts(true, HELLO);
		machine.setDevices(List.of());
		machine.pressPower();
		machine.setDevices(List.of(display));
		assertThat(screen().isBlank()).isFalse();
	}
}
