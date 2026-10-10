package com.payonel.modularcomputers.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where the all-in-one's power button is: bottom right of the back face, as seen from behind.
 * The housing spans texels 9..13 on the back texture; the glowing button is the 3x3 inside it
 * (see the block model and scripts/art/all_in_one_textures.py). Clicks anywhere on the housing
 * count.
 */
public final class PowerButton {
	static final int HOUSING_X = 9;
	static final int HOUSING_Y = 9;
	static final int HOUSING_SIZE = 5;

	private PowerButton() {
	}

	/**
	 * @param facing the direction the computer's front faces
	 * @param face   the face that was clicked
	 * @param local  the click position relative to the block's minimum corner (0..1 per axis)
	 */
	public static boolean isHit(Direction facing, Direction face, Vec3 local) {
		if (face != facing.getOpposite()) {
			return false;
		}
		// Someone looking at the back looks in the facing direction; their right is clockwise.
		Direction right = facing.getClockWise();
		double along = local.get(right.getAxis());
		double u = 16 * (right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : 1 - along);
		double v = 16 * (1 - local.y);
		return u >= HOUSING_X && u < HOUSING_X + HOUSING_SIZE && v >= HOUSING_Y && v < HOUSING_Y + HOUSING_SIZE;
	}
}
