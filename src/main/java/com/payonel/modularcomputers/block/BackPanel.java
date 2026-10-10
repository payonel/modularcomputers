package com.payonel.modularcomputers.block;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The clickable parts of the all-in-one's back face, in texels as seen from behind (u to the
 * right, v down). They match the back texture and the block model; see
 * scripts/art/all_in_one_textures.py.
 */
public final class BackPanel {
	public enum Region {
		/** The power button's housing at the bottom right (texels 9..13); the glowing button sits inside. */
		POWER(9, 9, 5, 5),
		/** The window into the case at the top, showing the motherboard (texels 3..12 by 2..7). Opens the case view. */
		CASE(3, 2, 10, 6);

		final int u;
		final int v;
		final int width;
		final int height;

		Region(int u, int v, int width, int height) {
			this.u = u;
			this.v = v;
			this.width = width;
			this.height = height;
		}

		boolean contains(double u, double v) {
			return u >= this.u && u < this.u + width && v >= this.v && v < this.v + height;
		}
	}

	private BackPanel() {
	}

	/**
	 * @param facing the direction the computer's front faces
	 * @param face   the face that was clicked
	 * @param local  the click position relative to the block's minimum corner (0..1 per axis)
	 */
	public static Optional<Region> hit(Direction facing, Direction face, Vec3 local) {
		if (face != facing.getOpposite()) {
			return Optional.empty();
		}
		// Someone looking at the back looks in the facing direction; their right is clockwise.
		Direction right = facing.getClockWise();
		double along = local.get(right.getAxis());
		double u = 16 * (right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : 1 - along);
		double v = 16 * (1 - local.y);
		for (Region region : Region.values()) {
			if (region.contains(u, v)) {
				return Optional.of(region);
			}
		}
		return Optional.empty();
	}
}
