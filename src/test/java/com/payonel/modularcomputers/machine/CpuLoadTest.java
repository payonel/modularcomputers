package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import com.payonel.modularcomputers.runtime.Board;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * The EEPROM holds plain text; the Java CPU runs the program class it names, and anything else is
 * a panic: blank, or not a program for this CPU (Linux's "exec format error").
 */
class CpuLoadTest {
	static final AtomicBoolean RAN = new AtomicBoolean();

	/** A program the CPU can load by name: a public class with a public no-argument constructor. */
	public static final class Halts implements Program {
		@Override
		public void run(Board board) {
			RAN.set(true);
			board.halt();
		}
	}

	private final Machine machine = new Machine(new Motherboard(Hardware.allInOne(), new Bus(List.of())));

	private String boot(String image) {
		machine.board().setCpu(new Cpu(CpuTier.TIER_1, Architecture.JAVA));
		machine.board().setEeprom(new Eeprom(image));
		machine.pressPower();
		return machine.board().virtualBus().console().screen().row(0).strip();
	}

	@Test
	void itRunsTheProgramClassTheTextNames() {
		RAN.set(false);
		boot(Halts.class.getName() + "\n");
		assertThat(RAN).isTrue();
		assertThat(machine.isPowered()).isTrue();
	}

	@Test
	void aBlankEepromIsAPanic() {
		assertThat(boot("")).isEqualTo(Cpu.ABORT + Cpu.BLANK);
	}

	@Test
	void textThatIsntAJavaProgramIsAnExecFormatError() {
		assertThat(boot("print('hello')")).as("Lua, say").isEqualTo(Cpu.ABORT + Cpu.EXEC_FORMAT);
		machine.pressPower();
		assertThat(boot("java.lang.String")).as("a class that isn't a program").isEqualTo(Cpu.ABORT + Cpu.EXEC_FORMAT);
		machine.pressPower();
		assertThat(boot("no.such.Program")).isEqualTo(Cpu.ABORT + Cpu.EXEC_FORMAT);
	}

	@Test
	void anAttachedProgramRunsInPlaceOfTheImage() {
		AtomicBoolean ran = new AtomicBoolean();
		machine.board().setCpu(new Cpu(CpuTier.TIER_1));
		Eeprom chip = new Eeprom("");
		chip.flash(board -> {
			ran.set(true);
			board.halt();
		});
		machine.board().setEeprom(chip);
		machine.pressPower();
		assertThat(ran).isTrue();
	}
}
