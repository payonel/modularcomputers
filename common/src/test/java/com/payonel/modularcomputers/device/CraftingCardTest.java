package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Result;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A crafting card's craft ends in its why's own shape (the user's), a fault too. */
class CraftingCardTest {
	private final CraftingCard card = new CraftingCard();
	/** What its bench's craft answers. */
	private Result crafts = Result.ok(Map.of("name", "minecraft:stick", "count", 4L));
	/** The robot that works it (none: a bench with robotics but no robot's moves to wait for). */
	private RoboticsDevice robot;

	CraftingCardTest() {
		card.benchIn(new CraftingCard.Bench() {
			@Override
			public boolean robotics() {
				return true;
			}

			@Override
			public RoboticsDevice robot() {
				return robot;
			}

			@Override
			public Result list() {
				return Result.ok(List.of());
			}

			@Override
			public Result craft() {
				return crafts;
			}

			@Override
			public Result move(int from, int to, int count) {
				return Result.ok(0);
			}

			@Override
			public Result equip(int slot) {
				return Result.ok(true);
			}

			@Override
			public Result pickup() {
				return Result.ok(0);
			}

			@Override
			public Result drop(String face, int slot, int count) {
				return Result.ok(0);
			}

			@Override
			public Result store(String face, int slot, int count, int to) {
				return Result.ok(true, 0);
			}

			@Override
			public Result take(String face, int slot, int count, int to) {
				return Result.ok(0);
			}
		});
		card.accepting(true);
	}

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	/** Its craft's ticks till its end (every craft takes its time). */
	private void craftsTillItEnds() {
		for (int tick = 0; tick < CraftingCard.CRAFT_TICKS * 4 && card.busy(); tick++) {
			card.tick();
		}
	}

	/** What works its items gone mid-count: a fault, the bench missing, its call the crafts done. */
	@Test
	void itsBenchMissingMidCountEndsAFault() {
		card.called("craft", List.of(3L));
		card.craft(args(3L));
		card.tick(); // (no craft yet: it takes its time)
		for (int tick = 1; tick < CraftingCard.CRAFT_TICKS; tick++) {
			card.tick();
		}
		crafts = Buffered.missing(Buffered.MISSING_BENCH);
		craftsTillItEnds();
		assertThat(card.pull(args()).first()).isEqualTo(Map.of("event", "craft", "ok", false, "why", "fault", "condition", "missing", "part", "bench", "message", "crafting bench required",
			"call", Map.of("args", List.of(3L), "progress", 1L)));
	}

	/** A why with no shape of its own can't be an end (the user's: each why built by its own method). */
	/** A craft given while one runs is the board's to refuse, busy with its id: given past it, the host's bug, never a busy with no id. */
	@Test
	void aCraftGivenPastTheBoardWhileOneRunsIsAnInternalError() {
		robot = new RoboticsDevice();
		card.craft(args(2L));
		assertThat(card.craft(args()).failure().table())
			.isEqualTo(Map.of("why", "fault", "condition", "internal_error", "part", "crafting", "message", "internal error"));
	}

	@Test
	void aWhyWithNoShapeCantEndACraft() {
		card.craft(args());
		crafts = Result.fail("something else");
		org.assertj.core.api.Assertions.assertThatThrownBy(this::craftsTillItEnds).isInstanceOf(IllegalArgumentException.class);
	}
}
