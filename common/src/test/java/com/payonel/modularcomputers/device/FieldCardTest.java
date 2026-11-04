package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The energy field card's stop and release (the user's: one door, a stop is a stop). */
class FieldCardTest {
	private final FieldCard card = new FieldCard();
	private boolean holding;
	private boolean moving;

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
			public String check() {
				return null;
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
}
