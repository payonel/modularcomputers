package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The operator card's answers: what the game gave of a command, and what it can't run. */
class OperatorCardTest {
	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	/** A command that ran answers what the game gave of it, and nothing the card made up. */
	@Test
	void aCommandThatRanAnswersWhatTheGameGaveOfIt() {
		OperatorCard card = new OperatorCard();
		card.runWith(command -> new OperatorCard.Outcome(0, 0, "Unknown item\n", "argument.item.id.invalid"));
		assertThat(card.command(args("give @p nothing")).first())
			.isEqualTo(Map.of("successes", 0L, "result", 0L, "message", "Unknown item\n", "key", "argument.item.id.invalid"));
	}

	/**
	 * A card in no world (a client's copy, a test's) can't run a command at all: the host's bug, an
	 * internal error, never an answer dressed as the game's ("not in a world" was).
	 */
	@Test
	void aCardInNoWorldIsAnInternalErrorNotTheGamesAnswer() {
		OperatorCard card = new OperatorCard();
		assertThat(card.command(args("time set day")).failure().table())
			.isEqualTo(Map.of("why", "fault", "condition", "internal_error", "part", "operator", "message", "internal error"));
	}
}
