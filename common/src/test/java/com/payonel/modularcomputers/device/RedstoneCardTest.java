package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payonel.modularcomputers.runtime.Arguments;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A redstone card's faces, as programs see and set them (user design, 2026-10-21). */
class RedstoneCardTest {
	private static final int NORTH = 0;
	private static final int DOWN = 5;

	private final RedstoneCard card = new RedstoneCard();

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, Object>> sides(Object answer) {
		return (Map<String, Map<String, Object>>) answer;
	}

	@Test
	void everyFaceHasItsLevelAndWhatItPutsOut() {
		card.sense(NORTH, 6);
		var sides = sides(card.get(args()).first());
		assertThat(sides.keySet()).containsExactly("north", "south", "east", "west", "up", "down");
		assertThat(sides.get("north")).containsEntry("level", 6L).containsEntry("powered", 0L);
	}

	@Test
	void setPoweredPutsOutASignalAndItsLevelIsTheStrongestThere() {
		card.accepting(true); // (a set is applied while its program runs)
		assertThat(card.set(args(Map.of("north", Map.of("powered", 9L)))).first()).as("a command: true at once").isEqualTo(true);
		assertThat(card.output(NORTH)).as("applied at its next tick").isZero();
		card.tick();
		assertThat(card.output(NORTH)).isEqualTo(9);
		var sides = sides(card.get(args()).first());
		assertThat(sides.get("north")).containsEntry("powered", 9L).as("its own signal is the level there").containsEntry("level", 9L);
		assertThat(sides).hasSize(6);
		card.sense(NORTH, 12);
		assertThat(sides(card.get(args()).first()).get("north")).containsEntry("level", 12L).containsEntry("powered", 9L);
		card.set(args(Map.of("north", Map.of("powered", 99L))));
		card.tick();
		assertThat(card.output(NORTH)).as("15 at most").isEqualTo(RedstoneCard.MAX_SIGNAL);
	}

	@Test
	void aCardReadsWhatTheOtherCardsInItsBlockPutOut() {
		card.block(side -> side == DOWN ? 15 : 0); // another card in the block puts out 15 below
		assertThat(sides(card.get(args()).first()).get("down")).containsEntry("level", 15L).containsEntry("powered", 0L);
	}

	@Test
	void setIgnoresALevelItIsWhatsDetected() {
		card.set(args(Map.of("north", Map.of("level", 9L))));
		card.tick();
		assertThat(card.output(NORTH)).isZero();
	}

	@Test
	void getTakesTheFacesToAskAboutAsKeysOrAList() {
		Map<Object, Object> keyed = new LinkedHashMap<>();
		keyed.put("north", Map.of("powered", 3L)); // its fields aren't filters
		assertThat(sides(card.get(args(keyed)).first()).keySet()).containsExactly("north");
		assertThat(sides(card.get(args(List.of("up", "west"))).first()).keySet()).containsExactly("up", "west");
	}

	@Test
	void aBadTableChangesNothing() {
		assertThatThrownBy(() -> card.set(args(Map.of("north", Map.of("powered", 3L), "sideways", Map.of("powered", 1L)))))
			.hasMessageContaining("no such face: sideways");
		assertThatThrownBy(() -> card.set(args(Map.of("north", Map.of("powered", 2.5))))).hasMessageContaining("powered: integer expected");
		assertThat(card.output(NORTH)).isZero();
	}

	@Test
	void withoutPowerItPutsOutNothing() {
		card.set(args(Map.of("north", Map.of("powered", 12L))));
		card.powerOff();
		assertThat(card.output(NORTH)).isZero();
	}

	@Test
	void aChangeComingInIsAnEventWhileItTakesData() {
		card.sense(NORTH, 4);
		assertThat(card.interruptPending()).as("not on a running board: dropped").isFalse();
		card.accepting(true);
		card.sense(NORTH, 7);
		@SuppressWarnings("unchecked")
		Map<String, Object> event = (Map<String, Object>) card.pull(args()).first();
		assertThat(event).isEqualTo(Map.of("name", "redstone", "side", "north", "level", 7L, "powered", 0L));
	}

	@Test
	void anyChangeToAFaceIsAnEventFromWhereverItCame() {
		card.accepting(true);
		int[] block = {0};
		card.block(side -> side == DOWN ? block[0] : 0);
		card.set(args(Map.of("north", Map.of("powered", 5L))));
		card.tick();
		@SuppressWarnings("unchecked")
		Map<String, Object> set = (Map<String, Object>) card.pull(args()).first();
		assertThat(set).as("its own set: its command's end, no face's change").containsEntry("event", "set").containsEntry("ok", true);
		assertThat(sides(set.get("result")).get("north")).containsEntry("level", 5L).containsEntry("powered", 5L);
		block[0] = 12; // another card in the block puts out 12 below
		card.report();
		assertThat(card.pull(args()).first()).as("another card's").isEqualTo(Map.of("name", "redstone", "side", "down", "level", 12L, "powered", 0L));
		card.sense(DOWN, 3); // weaker than what's there: no change
		assertThat(card.pull(args()).first()).isNull();
	}

	@Test
	void aFaceIsNamedByTheWorldsNameOrByItsBlocksFront() {
		card.accepting(true); // (a set is applied while its program runs)
		card.frontFrom(() -> Face.EAST); // (its case faces east: facing it, we look west, and its left is south)
		card.set(args(Map.of("front", Map.of("powered", 4L), "left", Map.of("powered", 2L))));
		card.tick();
		assertThat(card.output(2)).as("east, its front").isEqualTo(4);
		assertThat(card.output(1)).as("south, its left").isEqualTo(2);
		assertThat(sides(card.get(args(List.of("front"))).first()).keySet()).as("answered by the world's names").containsExactly("east");
		card.frontFrom(() -> null); // (no block has said where its front is)
		assertThatThrownBy(() -> card.set(args(Map.of("back", Map.of("powered", 1L))))).hasMessageContaining("no front");
	}
}
