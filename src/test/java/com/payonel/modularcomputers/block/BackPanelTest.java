package com.payonel.modularcomputers.block;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.block.BackPanel.Region;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BackPanelTest {
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

	private static Optional<Region> hit(Direction facing, double u, double v) {
		return BackPanel.hit(facing, facing.getOpposite(), onBack(facing, u, v));
	}

	@ParameterizedTest
	@EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
	void powerButtonIsAtTheBottomRight(Direction facing) {
		assertThat(hit(facing, 11.5, 11.5)).as("button center").contains(Region.POWER);
		assertThat(hit(facing, 9.1, 13.9)).as("housing corner").contains(Region.POWER);
		assertThat(hit(facing, 14.5, 11.5)).as("right of the housing").isEmpty();
		assertThat(hit(facing, 4.5, 11.5)).as("bottom left").isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
	void caseWindowIsAcrossTheTop(Direction facing) {
		assertThat(hit(facing, 7.5, 4.5)).as("window center").contains(Region.CASE);
		assertThat(hit(facing, 3.1, 2.1)).as("top-left corner").contains(Region.CASE);
		assertThat(hit(facing, 12.9, 7.9)).as("bottom-right corner").contains(Region.CASE);
		assertThat(hit(facing, 1.5, 4.5)).as("left of the window").isEmpty();
		assertThat(hit(facing, 7.5, 8.5)).as("below the window").isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
	void otherFacesHaveNothingToClick(Direction facing) {
		for (double[] spot : new double[][] {{11.5, 11.5}, {7.5, 4.5}}) {
			for (Direction face : Direction.values()) {
				if (face != facing.getOpposite()) {
					assertThat(BackPanel.hit(facing, face, onBack(facing, spot[0], spot[1]))).as(face.toString()).isEmpty();
				}
			}
		}
	}
}
