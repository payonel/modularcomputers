package com.payonel.modularcomputers.entity;

/**
 * How a golem walks without pathfinding (user design, 2026-10-25), in plain numbers: block by block,
 * each step from one floor to the next, its height changing only as its walk asks. Its elevation is
 * the block its feet are in (a slab's or a stair's lower half is the ground's elevation), so within
 * one it steps freely (onto a slab, along slabs, over carpet); it changes elevation only toward the
 * walk's {@code up}: up a block at a time (a jump, a rise of at most one block) while {@code up} is
 * above, down at most {@link #MAX_DROP} at a time (as far as a player falls unhurt) while it's
 * below, and never past it. Anything else blocks the walk there.
 */
public final class GolemSteps {
	/** The farthest it drops in a step, in blocks: a player's fall without damage. */
	public static final int MAX_DROP = 3;
	/** The highest it jumps in a step, in blocks. */
	public static final double MAX_JUMP = 1;
	/** Heights this close count as the same (floating point's, not a carpet's: those are within an elevation anyway). */
	private static final double EPSILON = 1e-6;

	private GolemSteps() {
	}

	/** The elevation of feet at {@code height}: the block they're in. */
	public static int elevation(double height) {
		return (int) Math.floor(height + EPSILON);
	}

	/**
	 * Whether it may step from feet at {@code from} to feet at {@code to}, on a walk asked to end at
	 * elevation {@code goal}: null, or why not.
	 */
	public static String step(double from, double to, int goal) {
		int now = elevation(from);
		int next = elevation(to);
		if (next == now) {
			return null; // (within its elevation: free)
		}
		if (next > now) {
			if (goal <= now || next - now > 1 || to - from > MAX_JUMP + EPSILON || next > goal) {
				return "too high: " + (next - now); // (a step up its walk wasn't to make, or more than a jump)
			}
			return null;
		}
		if (goal >= now || from - to > MAX_DROP + EPSILON || next < goal) {
			return "too low: " + (now - next); // (a drop its walk wasn't to make, or more than it takes)
		}
		return null;
	}
}
