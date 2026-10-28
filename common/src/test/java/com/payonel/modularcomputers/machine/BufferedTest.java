package com.payonel.modularcomputers.machine;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.Bus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A buffered device on the board (.agents/BUFFERED.md): one command at a time, the rest refused at once. */
class BufferedTest {
	private final Machine machine = new Machine(new Motherboard(Hardware.computer(), new Bus()));
	private final SlowDevice slow = new SlowDevice();

	BufferedTest() {
		machine.board().setCpu(new Cpu(CpuTier.TIER_3));
		new TestBus(machine).cable("id:slow", slow);
	}

	/** Runs {@code program} to its park, the device ticking with the machine. */
	private void run(Program program) {
		machine.board().setEeprom(new Eeprom(program));
		machine.pressPower();
		for (int i = 0; i < 200 && !Parked.parked(machine); i++) {
			slow.tick();
			machine.tick();
		}
		assertThat(Parked.parked(machine)).as("parked").isTrue();
	}

	@Test
	void aCommandWhileAnotherRunsIsRefusedBusyAndAFreeMethodAnswers() {
		List<Object> seen = new ArrayList<>();
		run(board -> {
			seen.add(board.invoke("id:slow", "work", 3L).values()); // starts: true at once
			seen.add(board.invoke("id:slow", "work", 1L).error()); // another command: refused, nothing done
			seen.add(board.invoke("id:slow", "peek").error()); // a read is a command too: it waits its turn
			seen.add(board.invoke("id:slow", "get").first()); // free: its live state, at once
			board.waitForInterrupt(10, "id:slow"); // its end
			seen.add(board.invoke("id:slow", "pull").first());
			seen.add(board.invoke("id:slow", "peek").first()); // idle: it takes commands again
			Program.park(board);
		});
		assertThat(seen).containsExactly(List.of(true), "busy", "busy", Map.of("working", true),
			Map.of("event", "work", "ok", true), 1L);
	}

	@Test
	void aRefusedCommandDoesNothingAndAStopEndsTheOneRunning() {
		List<Object> seen = new ArrayList<>();
		run(board -> {
			seen.add(board.invoke("id:slow", "work", 0L).error()); // the device's own refusal: nothing starts
			seen.add(board.invoke("id:slow", "peek").first()); // (idle still)
			seen.add(board.invoke("id:slow", "work", 50L).first());
			seen.add(board.invoke("id:slow", "stop").failed()); // free: never busy
			seen.add(board.invoke("id:slow", "pull").first()); // the one running ended, stopped
			seen.add(board.invoke("id:slow", "peek").first()); // and it's idle at once
			Program.park(board);
		});
		assertThat(seen).containsExactly("ticks: 0", 0L, true, false,
			Map.of("event", "work", "ok", false, "why", "stopped"), 0L);
		assertThat(slow.working()).isFalse();
	}
}
