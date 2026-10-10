package com.payonel.modularcomputers.block;

import static org.assertj.core.api.Assertions.assertThat;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PowerButtonTest {
	/**
	 * A point on the computer's back face, given in texels as seen by someone standing behind
	 * it ({@code u} to their right, {@code v} down), as block-local coordinates.
	 */
	private static Vec3 onBack(Direction facing, double u, double v) {
		Direction back = facing.getOpposite();
		Direction right = facing.getClockWise();
		double along = right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? u / 16 : 1 - u / 16;
		double depth = back.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
		double x = right.getAxis() == Direction.Axis.X ? along : depth;
		double z = right.getAxis() == Direction.Axis.Z ? along : depth;
		return new Vec3(x, 1 - v / 16, z);
	}

	@ParameterizedTest
	@EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
	void buttonIsAtTheBottomRightOfTheBack(Direction facing) {
		Direction back = facing.getOpposite();
		assertThat(PowerButton.isHit(facing, back, onBack(facing, 11.5, 11.5))).as("button center").isTrue();
		assertThat(PowerButton.isHit(facing, back, onBack(facing, 9.1, 13.9))).as("housing corner").isTrue();
		assertThat(PowerButton.isHit(facing, back, onBack(facing, 4.5, 11.5))).as("bottom left").isFalse();
		assertThat(PowerButton.isHit(facing, back, onBack(facing, 11.5, 4.5))).as("top right").isFalse();
		assertThat(PowerButton.isHit(facing, back, onBack(facing, 14.5, 11.5))).as("right of the housing").isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
	void otherFacesHaveNoButton(Direction facing) {
		Vec3 spot = onBack(facing, 11.5, 11.5);
		for (Direction face : Direction.values()) {
			if (face != facing.getOpposite()) {
				assertThat(PowerButton.isHit(facing, face, spot)).as(face.toString()).isFalse();
			}
		}
	}
}
