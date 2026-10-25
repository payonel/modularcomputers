package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * The EEPROM holds plain text the CPU runs as Lua: a blank one is a panic; a Java program attached
 * to the chip (a test's in-circuit programmer) runs in its place.
 */
class CpuLoadTest {
	private final Machine machine = new Machine(new Motherboard(Hardware.computer(), new Bus()));

	@Test
	void aBlankEepromIsAPanic() {
		machine.board().setCpu(new Cpu(CpuTier.TIER_1));
		machine.board().setEeprom(new Eeprom(""));
		machine.pressPower();
		assertThat(machine.board().virtualBus().console().screen().row(0).strip()).isEqualTo(Cpu.ABORT + Cpu.BLANK);
	}

	@Test
	void anAttachedProgramRunsInPlaceOfTheImage() {
		AtomicBoolean ran = new AtomicBoolean();
		machine.board().setCpu(new Cpu(CpuTier.TIER_1));
		Eeprom eeprom = new Eeprom("");
		eeprom.flash(board -> {
			ran.set(true);
			board.halt();
		});
		machine.board().setEeprom(eeprom);
		machine.pressPower();
		assertThat(ran).isTrue();
	}
}
