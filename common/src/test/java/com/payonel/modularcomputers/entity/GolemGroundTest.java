package com.payonel.modularcomputers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The blocks a golem's body crosses on a straight move (the user's, 2026-11-14: a supercover as wide
 * as it is), in the order it goes into them.
 */
class GolemGroundTest {
	/** A golem's half width, in blocks (its body 0.8 wide). */
	private static final double HALF = 0.4;

	private static List<String> blocks(GolemGround.Sweep sweep) {
		return sweep.placed().stream().map(c -> c.x() + "," + c.z()).toList();
	}

	@Test
	void aStraightStepCrossesItsOwnBlockAndTheNext() {
		assertThat(blocks(GolemGround.sweep(0.5, 0.5, 1.5, 0.5, HALF))).containsExactly("0,0", "1,0");
	}

	/** On the diagonal its body clips both blocks beside the corner it passes: none is slipped between. */
	@Test
	void aDiagonalStepCrossesBothBlocksBesideTheCorner() {
		assertThat(blocks(GolemGround.sweep(0.5, 0.5, 1.5, 1.5, HALF))).containsExactlyInAnyOrder("0,0", "1,0", "0,1", "1,1");
	}

	/** walk(4, 2)'s line: every block its body crosses, the one forward then right would miss (2, 1) among them, in order. */
	@Test
	void aLineCrossesEveryBlockItsBodyGoesOver() {
		var sweep = GolemGround.sweep(0.5, 0.5, 4.5, 2.5, HALF).placed();
		assertThat(sweep.stream().map(c -> c.x() + "," + c.z())).contains("2,1", "4,2").doesNotContain("4,0", "0,2");
		for (int i = 1; i < sweep.size(); i++) {
			assertThat(sweep.get(i).in()).isGreaterThanOrEqualTo(sweep.get(i - 1).in());
		}
		GolemGround.Crossing ahead = sweep.stream().filter(c -> c.x() == 2 && c.z() == 1).findFirst().orElseThrow();
		assertThat(ahead.alongX()).as("met on its east side: its front, going east").isTrue();
		assertThat(ahead.sign()).isEqualTo(1);
	}

	/** A step from a block's middle is worked out once: the same crossings, wherever it is. */
	@Test
	void aStepsCrossingsAreTheSameAnywhere() {
		var here = GolemGround.sweep(0.5, 0.5, 1.5, 1.5, HALF).placed();
		var there = GolemGround.sweep(-99.5, 40.5, -98.5, 41.5, HALF).placed();
		assertThat(there).hasSameSizeAs(here);
		for (int i = 0; i < here.size(); i++) {
			assertThat(there.get(i).x() - here.get(i).x()).isEqualTo(-100);
			assertThat(there.get(i).z() - here.get(i).z()).isEqualTo(40);
			assertThat(there.get(i).in()).isEqualTo(here.get(i).in());
		}
	}
}
