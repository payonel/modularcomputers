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
	private Machine machine = new Machine(new Motherboard(Hardware.computer(), new Bus()));
	/** The case's devices once it has a CPU: two disks and a cabled screen. */
	private TestBus bus;
	private static final String DISPLAY = "id:display";
	private static final String PLUGGED = "id:plugged";

	/** The tests' own program, not the mod's stand-in firmware (which changes). */
	private static final String GREETING = "hello world";
	private static final Program HELLO = board -> {
		board.invoke("console", "write", GREETING + "\n");
		com.payonel.modularcomputers.machine.Program.park(board);
	};

	private Machine withParts(boolean cpu, Program program) {
		machine.board().setCpu(cpu ? new Cpu(CpuTier.TIER_3) : null);
		if (cpu) {
			bus = new TestBus(machine);
			bus.disk(new Disk(DiskTier.TIER_1));
			bus.cable(DISPLAY, display);
			bus.disk(new Disk(DiskTier.TIER_1));
		}
		machine.board().setEeprom(program == null ? null : new Eeprom(program));
		return machine;
	}

	private TextBuffer screen() {
		return display.buffer();
	}

	@Test
	void aComputerHasSolderedUnlimitedMemory() {
		assertThat(Hardware.computer().parts()).singleElement().satisfies(part -> {
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
		withParts(false, board -> { runs[0]++; com.payonel.modularcomputers.machine.Program.park(board); });
		assertThat(machine.pressPower()).isEqualTo(Machine.PowerPress.NO_CPU);
		assertThat(machine.isPowered()).isFalse();
		assertThat(runs[0]).isZero();
		assertThat(screen().isBlank()).isTrue();
	}

	@Test
	void withoutPowerPressingPowerDoesNothingEvenWithoutACpu() {
		boolean[] power = {false};
		Machine unpowered = new Machine(new Motherboard(Hardware.computer(), new Bus()), () -> power[0]);
		assertThat(unpowered.pressPower()).isEqualTo(Machine.PowerPress.NO_POWER);
		unpowered.board().setCpu(new Cpu(CpuTier.TIER_3));
		new TestBus(unpowered).cable(DISPLAY, display);
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
		withParts(true, board -> { runs[0]++; com.payonel.modularcomputers.machine.Program.park(board); });
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
		withParts(true, board -> { seen.add(board.list().stream().map(com.payonel.modularcomputers.runtime.Board.DeviceInfo::address).toList()); com.payonel.modularcomputers.machine.Program.park(board); });
		machine.pressPower();
		assertThat(seen).containsExactly(List.of(Motherboard.sata(0), Motherboard.sata(1), DISPLAY)); // a case's own, in connector order, then the cabled
	}

	@Test
	void installingACpuDoesNotTurnTheMachineOn() {
		int[] runs = {0};
		withParts(false, board -> { runs[0]++; com.payonel.modularcomputers.machine.Program.park(board); });
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
	void noEepromAbortsAndTheBoardRecordsWhy() {
		withParts(true, null);
		machine.pressPower();
		assertThat(machine.isPowered()).as("off").isFalse();
		assertThat(machine.isAborted()).as("aborted: its light blinks amber").isTrue();
		assertThat(machine.lastShutdown().cause()).isEqualTo(Shutdown.Cause.NO_PROGRAM);
		assertThat(machine.lastShutdown().message()).isEqualTo(Cpu.NO_EEPROM);
	}

	@Test
	void aCrashingProgramAbortsWithItsError() {
		withParts(true, board -> {
			board.invoke("console", "write", "booting\n");
			throw new IllegalStateException("out of cheese");
		});
		machine.pressPower();
		assertThat(machine.isAborted()).isTrue();
		assertThat(machine.lastShutdown().cause()).isEqualTo(Shutdown.Cause.CRASHED);
		assertThat(machine.lastShutdown().message()).isEqualTo(Cpu.CRASHED + "out of cheese");
		machine.board().setEeprom(new Eeprom(Program::park)); // (fixed: this one runs)
		machine.pressPower();
		assertThat(machine.isAborted()).as("powered on again: not blinking").isFalse();
		assertThat(machine.lastShutdown().cause()).as("the record kept for the next boot to read").isEqualTo(Shutdown.Cause.CRASHED);
	}

	@Test
	void aPowerOffIsCleanAndThePowerButtonToo() {
		withParts(true, com.payonel.modularcomputers.runtime.Board::poweroff);
		machine.pressPower();
		assertThat(machine.isPowered()).isFalse();
		assertThat(machine.isAborted()).isFalse();
		assertThat(machine.lastShutdown().cause()).isEqualTo(Shutdown.Cause.CLEAN);
		withParts(true, Program::park);
		machine.pressPower();
		machine.pressPower();
		assertThat(machine.isAborted()).isFalse();
		assertThat(machine.lastShutdown().cause()).isEqualTo(Shutdown.Cause.BUTTON);
	}

	@Test
	void losingPowerAbortsAndPowerComingBackRestoresItWhenItsEepromSaysSo() {
		boolean[] supply = {true};
		boolean[] restore = {false};
		machine = new Machine(new Motherboard(Hardware.computer(), new com.payonel.modularcomputers.bus.Bus()), () -> supply[0]);
		machine.restoreOnPowerLossWith(() -> restore[0]);
		withParts(true, Program::park);
		machine.pressPower();
		supply[0] = false;
		machine.tick();
		assertThat(machine.isAborted()).isTrue();
		assertThat(machine.lastShutdown().cause()).isEqualTo(Shutdown.Cause.POWER_LOST);
		supply[0] = true;
		machine.tick();
		assertThat(machine.isPowered()).as("off: restore on power loss is off").isFalse();
		restore[0] = true;
		machine.tick();
		assertThat(machine.isPowered()).as("on again by itself").isTrue();
	}

	@Test
	void aCpuSwappedOrRamChangedWhileItRunsAborts() {
		withParts(true, Program::park);
		machine.pressPower();
		machine.board().setCpu(new Cpu(CpuTier.TIER_3)); // (another CPU, as the board sets one when its stack changes)
		machine.tick();
		assertThat(machine.lastShutdown().cause()).isEqualTo(Shutdown.Cause.CPU_CHANGED);
		assertThat(machine.isAborted()).isTrue();
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
		withParts(true, board -> { runs[0]++; com.payonel.modularcomputers.machine.Program.park(board); });
		machine.restorePowered(true);
		assertThat(machine.isPowered()).isTrue();
		assertThat(runs[0]).isZero();
	}

	@Test
	void aScreenPluggedIntoARunningMachineShowsTheConsole() {
		withParts(true, HELLO);
		machine.pressPower();
		Terminal plugged = new Terminal(Resolution.DEFAULT);
		bus.cable(PLUGGED, plugged);
		assertThat(plugged.buffer().rows()).isEqualTo(screen().rows());
		assertThat(plugged.buffer().isBlank()).isFalse();
	}

	@Test
	void aScreenPluggedIntoAnOffMachineStaysAsItWas() {
		withParts(true, HELLO);
		Terminal plugged = new Terminal(Resolution.DEFAULT);
		plugged.buffer().write(0, 0, "other");
		bus.cable(PLUGGED, plugged);
		assertThat(plugged.buffer().row(0)).startsWith("other");
	}

	@Test
	void unpluggingAScreenFromARunningMachineBlanksIt() {
		withParts(true, HELLO);
		machine.pressPower();
		Terminal plugged = new Terminal(Resolution.DEFAULT);
		bus.cable(PLUGGED, plugged);
		assertThat(plugged.buffer().isBlank()).isFalse();
		bus.uncable(PLUGGED);
		assertThat(plugged.buffer().isBlank()).isTrue();
		assertThat(screen().isBlank()).isFalse();
	}

	@Test
	void theConsoleRemembersWhatItShowedWhileHeadless() {
		withParts(true, HELLO);
		bus.uncable(DISPLAY);
		machine.pressPower();
		bus.cable(DISPLAY, display);
		assertThat(screen().isBlank()).isFalse();
	}
}
