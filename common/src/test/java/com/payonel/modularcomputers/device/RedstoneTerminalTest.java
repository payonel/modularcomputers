package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payonel.modularcomputers.runtime.Arguments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A tier 1 redstone terminal's sides, as programs see and set them (user design, 2026-10-20). */
class RedstoneTerminalTest {
	private static final int NORTH = 0;
	private static final int TOP = 4;

	private final RedstoneTerminal terminal = new RedstoneTerminal();
	/** A world: a cable against the top (port open), what comes in on each side, and what changed. */
	private final boolean[] cabled = new boolean[6];
	private final boolean[] closed = new boolean[6];
	private final int[] input = new int[6];
	private final List<Integer> paid = new ArrayList<>();
	private int outputChanges;

	@BeforeEach
	void world() {
		cabled[TOP] = true;
		terminal.world(new RedstoneTerminal.World() {
			@Override
			public boolean cable(int side) {
				return cabled[side] && !closed[side];
			}

			@Override
			public int input(int side) {
				return input[side];
			}

			@Override
			public void port(int side, boolean open) {
				closed[side] = !open;
			}

			@Override
			public void outputChanged() {
				outputChanges++;
			}
		});
		terminal.payWith(paid::add);
	}

	private static Arguments args(Object... values) {
		return new Arguments(List.of(values));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, Object>> sides(Object answer) {
		return (Map<String, Map<String, Object>>) answer;
	}

	@Test
	void everySideIsACableSideOrARedstoneSideWithItsLevel() {
		input[NORTH] = 6;
		var sides = sides(terminal.get(args()).first());
		assertThat(sides.keySet()).containsExactly("north", "south", "east", "west", "top", "bottom");
		assertThat(sides.get("top")).containsEntry("mode", "cable").containsEntry("level", 1L).containsEntry("powered", 0L);
		assertThat(sides.get("north")).containsEntry("mode", "redstone").containsEntry("level", 6L).containsEntry("powered", 0L);
	}

	@Test
	void aRedstoneSidesLevelIsTheStrongestSignalThereComingInOrItsOwn() {
		input[NORTH] = 6;
		terminal.set(args(Map.of("north", Map.of("level", 3L))));
		assertThat(sides(terminal.get(args()).first()).get("north")).containsEntry("level", 6L).containsEntry("powered", 3L);
		input[NORTH] = 0;
		terminal.set(args(Map.of("north", Map.of("level", 0L))));
		assertThat(sides(terminal.get(args()).first()).get("north")).containsEntry("level", 0L).containsEntry("powered", 0L);
	}

	@Test
	void setPutsOutALevelOnARedstoneSideAndIgnoresItOnACableSide() {
		var sides = sides(terminal.set(args(Map.of("north", Map.of("level", 9L), "top", Map.of("level", 5L)))).first());
		assertThat(terminal.output(NORTH)).isEqualTo(9);
		assertThat(terminal.output(TOP)).as("a cable side's level is ignored").isZero();
		assertThat(sides.get("north")).containsEntry("powered", 9L).as("its own signal is the level there").containsEntry("level", 9L);
		assertThat(sides).as("set answers every side").hasSize(6);
		assertThat(paid).as("a change costs power; no change, nothing").containsExactly(RedstoneTerminal.CHANGE_COST);
		assertThat(outputChanges).isEqualTo(1);
		terminal.set(args(Map.of("north", Map.of("level", 9L))));
		assertThat(paid).hasSize(1);
		terminal.set(args(Map.of("north", Map.of("level", 99L))));
		assertThat(terminal.output(NORTH)).as("15 at most").isEqualTo(RedstoneTerminal.MAX_SIGNAL);
	}

	@Test
	void settingASidesModeOpensOrClosesItsPort() {
		var sides = sides(terminal.set(args(Map.of("top", Map.of("mode", "redstone", "level", 4L)))).first());
		assertThat(closed[TOP]).isTrue();
		assertThat(sides.get("top")).as("closed: a redstone side, and its level counts").containsEntry("mode", "redstone").containsEntry("powered", 4L);
		terminal.set(args(Map.of("top", Map.of("mode", "cable"))));
		assertThat(closed[TOP]).isFalse();
		terminal.set(args(Map.of("north", Map.of("mode", "cable"))));
		assertThat(sides(terminal.get(args()).first()).get("north")).as("open with no cable there: still redstone").containsEntry("mode", "redstone");
	}

	@Test
	void getTakesTheSidesToAskAboutAsKeysOrAList() {
		Map<Object, Object> keyed = new LinkedHashMap<>();
		keyed.put("north", Map.of("mode", "cable", "level", 3L)); // its fields aren't filters
		assertThat(sides(terminal.get(args(keyed)).first()).keySet()).containsExactly("north");
		assertThat(sides(terminal.get(args(List.of("top", "west"))).first()).keySet()).containsExactly("top", "west");
	}

	@Test
	void aBadTableChangesNothing() {
		assertThatThrownBy(() -> terminal.set(args(Map.of("north", Map.of("level", 3L), "up", Map.of("level", 1L)))))
			.hasMessageContaining("no such side: up");
		assertThatThrownBy(() -> terminal.set(args(Map.of("north", Map.of("mode", "red")))))
			.hasMessageContaining("a tier 1 redstone terminal has no color mode");
		assertThatThrownBy(() -> terminal.set(args(Map.of("north", Map.of("mode", "bundled"))))).hasMessageContaining("no such mode");
		assertThatThrownBy(() -> terminal.set(args(Map.of("north", Map.of("level", 2.5))))).hasMessageContaining("integer expected");
		assertThat(terminal.output(NORTH)).isZero();
		assertThat(paid).isEmpty();
	}

	@Test
	void whatItPutsOutHoldsWhenThePowerGoes() {
		terminal.set(args(Map.of("north", Map.of("level", 12L))));
		terminal.powerOff();
		assertThat(terminal.output(NORTH)).isEqualTo(12);
	}

	@Test
	void aChangeComingInIsAnEventWhileItTakesData() {
		terminal.sense(NORTH, 4);
		assertThat(terminal.interruptPending()).as("not on a running board: dropped").isFalse();
		terminal.accepting(true);
		terminal.sense(NORTH, 7);
		@SuppressWarnings("unchecked")
		Map<String, Object> event = (Map<String, Object>) terminal.pull(args()).first();
		assertThat(event).containsEntry("name", "redstone").containsEntry("side", "north").containsEntry("old", 4L).containsEntry("new", 7L);
	}
}
