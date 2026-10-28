package com.payonel.modularcomputers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A golem's steps without pathfinding (the user's rules): free within an elevation (the block its
 * feet are in), whole-block jumps up and drops of up to three only toward its walk's height.
 */
class GolemStepsTest {
	private static final double GROUND = 64;
	private static final double SLAB = 64.5;
	private static final double CARPET = 64 + 1 / 16.0;

	@Test
	void withinAnElevationItStepsFreelyOntoSlabsAndCarpet() {
		assertThat(GolemSteps.step(GROUND, SLAB, 64)).as("onto a slab, a level walk").isNull();
		assertThat(GolemSteps.step(SLAB, GROUND, 64)).as("off it").isNull();
		assertThat(GolemSteps.step(GROUND, CARPET, 64)).isNull();
		assertThat(GolemSteps.step(SLAB, SLAB, 64)).as("along slabs").isNull();
	}

	@Test
	void aLevelWalkWontGoUpOrDownAnElevation() {
		assertThat(GolemSteps.step(SLAB, 65, 64)).as("from a slab to the full block beside it").isEqualTo("too high: 1");
		assertThat(GolemSteps.step(GROUND, 65, 64)).isEqualTo("too high: 1");
		assertThat(GolemSteps.step(GROUND, 63, 64)).isEqualTo("too low: 1");
	}

	@Test
	void itJumpsABlockAtATimeTowardItsHeightAndNoHigher() {
		assertThat(GolemSteps.step(GROUND, 65, 65)).isNull();
		assertThat(GolemSteps.step(SLAB, 65, 65)).as("half a block, into the next elevation").isNull();
		assertThat(GolemSteps.step(GROUND, 66, 70)).as("two at once").isEqualTo("too high: 2");
		assertThat(GolemSteps.step(SLAB, 65.5, 70)).as("a slab to a slab a block up: a jump").isNull();
		assertThat(GolemSteps.step(65, 66, 65)).as("past where it was to go").isEqualTo("too high: 1");
	}

	@Test
	void itDropsUpToThreeAtATimeTowardItsHeightAndNoLower() {
		assertThat(GolemSteps.step(GROUND, 61, 50)).as("three: a player's fall unhurt").isNull();
		assertThat(GolemSteps.step(GROUND, 60, 50)).isEqualTo("too low: 4");
		assertThat(GolemSteps.step(GROUND, 62, 63)).as("past where it was to go").isEqualTo("too low: 2");
		assertThat(GolemSteps.step(GROUND, 65, 60)).as("up, going down").isEqualTo("too high: 1");
	}
}
