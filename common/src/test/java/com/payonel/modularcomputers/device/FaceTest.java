package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** A face by the world's name or by its block's front, left and right the block's own (as OpenComputers' sides). */
class FaceTest {
	@Test
	void theWorldsNamesAreTheirOwnWhateverTheFront() {
		for (Face face : Face.values()) {
			assertThat(Face.named(face.id(), null)).isEqualTo(face);
			assertThat(Face.named(face.id().toUpperCase(), Face.SOUTH)).isEqualTo(face);
		}
	}

	@Test
	void relativeNamesTurnWithTheFrontLeftAndRightAsSomeoneFacingItSeesThem() {
		assertThat(Face.named("front", Face.NORTH)).isEqualTo(Face.NORTH);
		assertThat(Face.named("back", Face.NORTH)).isEqualTo(Face.SOUTH);
		assertThat(Face.named("left", Face.NORTH)).as("facing north, its own left is west").isEqualTo(Face.WEST);
		assertThat(Face.named("right", Face.NORTH)).isEqualTo(Face.EAST);
		assertThat(Face.named("left", Face.EAST)).isEqualTo(Face.NORTH);
		assertThat(Face.named("right", Face.SOUTH)).as("as OpenComputers': a block facing south has west on its right").isEqualTo(Face.WEST);
		assertThat(Face.named("back", Face.UP)).isEqualTo(Face.DOWN);
	}

	@Test
	void aNameNoFaceHasOrOneTheBlockCantMeanIsRefusedWithWhy() {
		assertThatThrownBy(() -> Face.named("sideways", Face.NORTH)).hasMessageContaining("no such face");
		assertThatThrownBy(() -> Face.named("front", null)).hasMessageContaining("no front");
		assertThatThrownBy(() -> Face.named("left", Face.UP)).hasMessage("no left: facing up");
	}
}
