package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.display.Resolution;
import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What a device holds for the program, and settings a program made, outlast a save (user, 2026-10-13: a save isn't a power cut). */
class SavedStateTest {
	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	@Test
	void keysWaitingInATerminalAreKept() {
		Terminal terminal = new Terminal(Resolution.DEFAULT);
		terminal.accepting(true); // on a running board\'s bus
		terminal.press("a");
		terminal.press("\u001b[A");
		Terminal loaded = new Terminal(Resolution.DEFAULT);
		loaded.restore(terminal.saved());
		assertThat(loaded.interruptPending()).isTrue();
		assertThat(loaded.read(args()).first()).as("a read takes every byte waiting").isEqualTo("a\u001b[A");
		assertThat(loaded.interruptPending()).isFalse();
	}

	@Test
	void aDeviceTakesNoDataWhileNotAcceptingAndDropsWhatItHadWhenItStops() {
		Terminal terminal = new Terminal(Resolution.DEFAULT);
		terminal.press("before"); // no running board: dropped
		assertThat(terminal.interruptPending()).isFalse();
		terminal.accepting(true);
		terminal.press("a");
		terminal.accepting(false); // its board halted, was turned off, or let it go
		assertThat(terminal.interruptPending()).isFalse();
		RedstoneTerminal card = new RedstoneTerminal();
		card.sense(0, 3);
		assertThat(card.interruptPending()).as("an event while not accepting: dropped").isFalse();
		card.accepting(true);
		card.sense(0, 5);
		assertThat(card.interruptPending()).isTrue();
		card.accepting(false);
		assertThat(card.interruptPending()).isFalse();
	}

	@Test
	void aTerminalsKeyQueueHasItsOwnLimitAndDropsWhatHasNoRoom() {
		Terminal terminal = new Terminal(Resolution.DEFAULT);
		terminal.accepting(true); // on a running board\'s bus
		StringBuilder expected = new StringBuilder();
		for (int i = 0; i < Terminal.INPUT_CAPACITY + 10; i++) {
			terminal.press("k" + i + ",");
			if (i < Terminal.INPUT_CAPACITY) {
				expected.append("k").append(i).append(",");
			}
		}
		assertThat(terminal.read(args()).first()).as("what waited stays; the newest are dropped").isEqualTo(expected.toString());
		assertThat(terminal.read(args()).first()).isNull();
	}

	@Test
	void aRedstoneTerminalKeepsItsEventsItsOutputsAndWhatItSaw() {
		RedstoneTerminal terminal = new RedstoneTerminal();
		terminal.accepting(true); // on a running board\'s bus
		terminal.set(args(Map.of("north", Map.of("powered", 7L))));
		terminal.sense(5, 9);
		RedstoneTerminal loaded = new RedstoneTerminal();
		loaded.restore(terminal.saved());
		assertThat(loaded.output(0)).isEqualTo(7);
		@SuppressWarnings("unchecked")
		Map<String, Object> event = (Map<String, Object>) loaded.pull(args()).first();
		assertThat(event).containsEntry("side", "bottom").containsEntry("new", 9L);
		loaded.sense(5, 9);
		assertThat(loaded.pull(args()).first()).as("the same signal after a load is no change").isNull();
	}

	@Test
	void aMotionSensorKeepsItsModeRangeAndEvents() {
		MotionSensorCard card = new MotionSensorCard();
		card.accepting(true); // on a running board\'s bus
		card.setMode(args("drops"));
		card.setRange(args(20L));
		card.sense("drop", Map.of("x", 1.0));
		MotionSensorCard loaded = new MotionSensorCard();
		loaded.restore(card.saved());
		assertThat(loaded.getMode(args()).first()).isEqualTo("drops");
		assertThat(loaded.getRange(args()).first()).isEqualTo(20L);
		assertThat(loaded.power().draw()).isEqualTo(card.power().draw());
		assertThat(loaded.interruptPending()).isTrue();
	}

	@Test
	void aWeatherCardKeepsWhatItSawSoALoadIsntAChange() {
		WeatherCard card = new WeatherCard();
		card.accepting(true); // on a running board\'s bus
		card.sense("rain");
		WeatherCard loaded = new WeatherCard();
		loaded.restore(card.saved());
		loaded.sense("rain");
		assertThat(loaded.interruptPending()).isFalse();
		assertThat(loaded.getWeather(args()).first()).isEqualTo("rain");
	}
}
