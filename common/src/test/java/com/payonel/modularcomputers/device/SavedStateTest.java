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
		RedstoneCard card = new RedstoneCard();
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
	void aRedstoneCardKeepsItsEventsItsOutputsAndWhatItSaw() {
		RedstoneCard card = new RedstoneCard();
		card.accepting(true); // on a running board\'s bus
		card.set(args(Map.of("north", Map.of("powered", 7L))));
		card.sense(5, 9);
		RedstoneCard loaded = new RedstoneCard();
		loaded.restore(card.saved());
		loaded.accepting(true);
		assertThat(loaded.pull(args()).first()).as("the world's change, kept").isEqualTo(Map.of("event", "change", "face", "down", "level", 9L, "powered", 0L));
		assertThat(loaded.refusal("set")).as("its set, saved before its tick: still waiting").isEqualTo("busy");
		loaded.tick();
		assertThat(loaded.output(0)).as("and applied after the load").isEqualTo(7);
		assertThat(((Map<?, ?>) loaded.pull(args()).first()).get("event")).as("and it still ends").isEqualTo("set");
		loaded.sense(5, 9);
		assertThat(loaded.pull(args()).first()).as("the same signal after a load is no change").isNull();
	}

	/**
	 * A load's real order: its card ticks before its program goes on (a case's tick comes first), so a
	 * set waiting waits for its program to take data, or its end would be dropped.
	 */
	@Test
	void aRedstoneSetRestoredWaitsForItsProgramToEndWhereItHears() {
		RedstoneCard card = new RedstoneCard();
		card.accepting(true);
		card.set(args(Map.of("north", Map.of("powered", 7L))));
		RedstoneCard loaded = new RedstoneCard();
		loaded.restore(card.saved());
		loaded.tick(); // (ticked before its program resumes: it takes no data yet)
		assertThat(loaded.output(0)).as("not yet: no program to hear its end").isZero();
		loaded.accepting(true); // (its program goes on)
		loaded.tick();
		assertThat(loaded.output(0)).isEqualTo(7);
		assertThat(((Map<?, ?>) loaded.pull(args()).first()).get("event")).as("its end, heard").isEqualTo("set");
	}

	/**
	 * A card that joins its computer's bus after the program went on (a hub's chunk loading later) is
	 * reset as it joins: what it brought from the save is kept through that first reset, a set waiting
	 * too; any later reset clears.
	 */
	@Test
	void whatALoadBroughtIsKeptThroughTheFirstResetOnly() {
		RedstoneCard card = new RedstoneCard();
		card.accepting(true);
		card.sense(5, 9);
		card.set(args(Map.of("north", Map.of("powered", 7L))));
		RedstoneCard loaded = new RedstoneCard();
		loaded.restore(card.saved());
		loaded.accepting(false); // (it joins: "what it had before isn't this program's", but it is)
		loaded.accepting(true);
		assertThat(loaded.pull(args()).first()).as("its event, kept").isEqualTo(Map.of("event", "change", "face", "down", "level", 9L, "powered", 0L));
		loaded.tick();
		assertThat(loaded.output(0)).as("its set, kept and applied").isEqualTo(7);
		loaded.sense(5, 3);
		loaded.accepting(false); // (a later reset: its program's gone)
		loaded.accepting(true);
		assertThat(loaded.pull(args()).first()).as("cleared now").isNull();
	}

	/** A craft under way at a save goes on after the load (the user's): how many are left, done and of what, kept. */
	@Test
	void aCraftUnderWayAtASaveGoesOnAfterTheLoad() {
		CraftingCard loaded = new CraftingCard();
		loaded.restore(Map.of("events", List.of(), "lost", 0L, "toCraft", 3L, "crafts", 2L, "crafted", 8L, "name", "minecraft:stick", "args", List.of(5L)));
		loaded.accepting(false); // (the first reset after a load keeps what the load brought)
		loaded.accepting(true);
		assertThat(loaded.busy()).as("still crafting").isTrue();
		assertThat(loaded.pull(args()).first()).as("no end yet").isNull();
		loaded.stop(args());
		assertThat(loaded.pull(args()).first()).as("its crafts done kept, its call's").isEqualTo(
			Map.of("event", "craft", "ok", false, "why", "stopped", "call", Map.of("args", List.of(5L), "progress", 2L)));
	}

	/** A field holding at a save holds on after the load (the user's): its hold is the world's record, and its card goes on with it, a move under way too. */
	@Test
	void aFieldHoldingAtASaveHoldsOnAfterTheLoad() {
		FieldCard loaded = new FieldCard();
		java.util.UUID hold = java.util.UUID.randomUUID();
		loaded.restore(Map.of("events", List.of(), "lost", 0L, "state", FieldCard.MOVING, "hold", hold.toString(), "args", List.of(0L, 0L, 3L)));
		loaded.accepting(false); // (the first reset after a load keeps what the load brought)
		loaded.accepting(true);
		assertThat(loaded.state()).as("still moving").isEqualTo(FieldCard.MOVING);
		assertThat(loaded.hold()).isEqualTo(hold);
		assertThat(loaded.pull(args()).first()).as("no end: it goes on").isNull();
	}

	/** A piston's move under way at a save ends after the load, its program hearing (a save isn't a power cut). */
	@Test
	void aPistonsMoveUnderWayAtASaveEndsAfterTheLoad() {
		RoboticPiston piston = new RoboticPiston();
		piston.moveWith((side, motion) -> RoboticPiston.Moved.DONE);
		piston.accepting(true);
		assertThat(piston.extend(args("north")).first()).isEqualTo(true);
		RoboticPiston loaded = new RoboticPiston();
		loaded.restore(piston.saved());
		loaded.accepting(true);
		assertThat(loaded.refusal("push")).as("its move still under way").isEqualTo(Buffered.BUSY);
		for (int tick = 0; tick < RoboticPiston.MOVE_TICKS; tick++) {
			loaded.tick();
		}
		assertThat(loaded.pull(args()).first()).isEqualTo(Map.of("event", "extend", "ok", true));
	}

	@Test
	void aMotionSensorKeepsItsEvents() {
		MotionSensorCard card = new MotionSensorCard();
		card.accepting(true); // on a running board\'s bus
		card.sense(Map.of("event", "motion", "name", "minecraft:pig", "at", Map.of("forward", 1.0, "right", -2.0, "up", 0.0)));
		MotionSensorCard loaded = new MotionSensorCard();
		loaded.restore(card.saved());
		assertThat(loaded.interruptPending()).isTrue();
		assertThat(loaded.pull(args()).first()).isEqualTo(Map.of("event", "motion", "name", "minecraft:pig", "at", Map.of("forward", 1.0, "right", -2.0, "up", 0.0)));
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
