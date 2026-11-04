package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The energy field card's stop and release (the user's: one door, a stop is a stop), and its ends past its reach and without power. */
class FieldCardTest {
	private final FieldCard card = new FieldCard();
	private boolean holding;
	private boolean moving;
	/** What its projector says: a move's end past its reach (its face), what it holds past it now (its face), and why it lost it. */
	private String beyond;
	private String outOfReach;
	private String lost;

	FieldCardTest() {
		card.projectorIn(new FieldCard.Projector() {
			@Override
			public Result scan(double range) {
				return Result.ok(List.of());
			}

			@Override
			public Result capture(String id, double[] at) {
				holding = true;
				return Result.ok("minecraft:pig");
			}

			@Override
			public Result move(double forward, double right, double up) {
				moving = true; // (it never arrives, here)
				return Result.ok(true);
			}

			@Override
			public void stop() {
				moving = false;
			}

			@Override
			public void release() {
				holding = false;
			}

			@Override
			public Map<String, Object> where() {
				return null;
			}

			@Override
			public String beyondReach(double forward, double right, double up) {
				return beyond;
			}

			@Override
			public String outOfReach() {
				return outOfReach;
			}

			@Override
			public String check() {
				return lost;
			}
		});
		card.accepting(true);
	}

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	@Test
	void aReleaseWaitsForAMoveAndAStopEndsItHoldingOn() {
		card.capture(args("pig"));
		card.move(args(0.0, 0.0, 3.0));
		assertThat(card.isFree("release")).as("a release is a command").isFalse();
		assertThat(card.refusal("release")).as("it waits for the move under way").isEqualTo(Buffered.BUSY);
		assertThat(card.isFree("stop")).as("a stop never waits").isTrue();
		assertThat(card.stop(args()).failed()).isFalse();
		assertThat(moving).as("its move stopped where it is").isFalse();
		assertThat(holding).as("holding on").isTrue();
		assertThat(card.pull(args()).first()).isEqualTo(Map.of("event", "move", "ok", false, "why", "stopped",
			"call", Map.of("args", List.of(), "progress", List.of(0L, 0L, 0L))));
		assertThat(card.refusal("release")).as("then free to let go").isNull();
		card.release(args());
		assertThat(holding).isFalse();
	}

	/** A move past its reach is blocked (the user's: too far is just blocked): its end at once, nothing moved, no block to name, the side of what it holds that meets the edge. */
	@Test
	void aMovePastItsReachEndsBlockedAtOnceAndMovesNothing() {
		card.capture(args("pig"));
		beyond = "up";
		card.called("move", List.of(0L, 0L, 17L));
		assertThat(card.move(args(0L, 0L, 17L)).first()).as("a command: true, its end says how it went").isEqualTo(true);
		assertThat(moving).as("nothing moved").isFalse();
		assertThat(card.pull(args()).first()).isEqualTo(Map.of("event", "move", "ok", false, "why", "blocked", "name", "", "face", "up",
			"call", Map.of("args", List.of(0L, 0L, 17L), "progress", List.of(0L, 0L, 0L))));
		assertThat(holding).as("holding on").isTrue();
	}

	/** What it holds past its reach, it lets go: blocked, a move under way's end and a notice (the user's), the side past the edge. */
	@Test
	void outOfItsReachItLetsGoBlockedItsMoveAndANotice() {
		card.capture(args("pig"));
		card.called("move", List.of(3L));
		card.move(args(3L));
		outOfReach = "front";
		lost = Buffered.BLOCKED;
		card.tick(true);
		assertThat(card.pull(args()).first()).isEqualTo(Map.of("event", "move", "ok", false, "why", "blocked", "name", "", "face", "front",
			"call", Map.of("args", List.of(3L), "progress", List.of(0L, 0L, 0L))));
		assertThat(card.pull(args()).first()).isEqualTo(Map.of("event", "notice", "why", "blocked", "name", "", "face", "front"));
		assertThat(holding).as("let go").isFalse();
	}

	/** Its power gone, it lets go: powerout (the user's), a move under way's end with its call, and a notice. */
	@Test
	void withoutPowerItLetsGoPowerout() {
		card.capture(args("pig"));
		card.called("move", List.of(3L));
		card.move(args(3L));
		card.tick(false);
		assertThat(card.pull(args()).first()).isEqualTo(Map.of("event", "move", "ok", false, "why", "powerout",
			"call", Map.of("args", List.of(3L), "progress", List.of(0L, 0L, 0L))));
		assertThat(card.pull(args()).first()).isEqualTo(Map.of("event", "notice", "why", "powerout"));
		assertThat(holding).as("let go").isFalse();
	}
}
