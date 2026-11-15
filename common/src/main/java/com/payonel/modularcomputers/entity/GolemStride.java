package com.payonel.modularcomputers.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A golem's walk, stepped across the world (the user's, 2026-11-14: direct): a straight line from
 * each of its points to the next (a walk's, from where it is to its target; a way found round
 * something, a block's middle each), its body, as wide as it is, going into every block it crosses
 * (a supercover: {@link GolemGround#sweep}), each judged as its body goes in ({@link GolemGround}).
 * Its feet stand on the highest floor its body is over (so it climbs as its edge meets a step, and
 * drops once all of it is off a ledge); each change of that floor is a step its rules judge
 * ({@link GolemSteps}: toward its walk's elevation only, or, with pathfinding, free), a block it
 * doesn't fit in is a wall, a barred one (fire, a fluid not water) stops it too. Its walk and its
 * wayfinder's search judge a step with this one code, so they agree.
 */
final class GolemStride {
	private static final double EPSILON = GolemGround.EPSILON;
	/** How many blocks its body is over at once, as it starts room for (four: a body narrower than a block; more, it grows). */
	private static final int UNDER = 4;

	/** Why it stopped: the block in its way (air too), its side that met it (a world direction), and the block it was going into. */
	record Stop(BlockPos block, Vec3 side, int x, int z) {
	}

	/** Its points, across (its feet go by the floors). */
	private double[] pointX, pointZ;
	private final int goal;
	private final boolean free;
	private int segment;
	private double along, length, ux, uz;
	private double x, z;
	private double support;
	private GolemGround.Sweep sweep;
	/** Its segment's next block to go into, and to come out of (in its sweep's order out). */
	private int nextIn, nextOut;
	/** The blocks its body is over, and their floors (NaN: none, nothing to stand on). */
	private int[] underX = new int[UNDER], underZ = new int[UNDER];
	private double[] underFloor = new double[UNDER];
	private int under;
	private double standX, standY, standZ;
	private @Nullable Stop stop;

	/**
	 * A walk from its first point through the rest, its feet at {@code feet} now, its walk's elevation
	 * {@code goal}; {@code free}: its elevation free on the way (pathfinding's).
	 */
	GolemStride(GolemGround ground, List<Vec3> points, double feet, int goal, boolean free) {
		this(ground, xs(points), zs(points), feet, goal, free);
	}

	/** A step from one point to another (its wayfinder's), as {@link #GolemStride(GolemGround, List, double, int, boolean)}. */
	GolemStride(GolemGround ground, double fromX, double fromZ, double toX, double toZ, double feet, int goal, boolean free) {
		this(ground, new double[] {fromX, toX}, new double[] {fromZ, toZ}, feet, goal, free);
	}

	private GolemStride(GolemGround ground, double[] pointX, double[] pointZ, double feet, int goal, boolean free) {
		this.pointX = pointX;
		this.pointZ = pointZ;
		this.goal = goal;
		this.free = free;
		x = pointX[0];
		z = pointZ[0];
		support = feet;
		GolemGround.Sweep here = GolemGround.sweep(x, z, x, z, ground.half());
		for (int i = 0; i < here.size(); i++) {
			GolemGround.Cell cell = ground.cell(here.x(i), here.z(i), feet);
			addUnder(here.x(i), here.z(i), cell.kind() == GolemGround.Kind.FLOOR ? cell.floor() : Double.NaN); // (where it stands: no step)
		}
		standX = x;
		standY = feet;
		standZ = z;
		standing();
		begin(ground);
	}

	private static double[] xs(List<Vec3> points) {
		return points.stream().mapToDouble(Vec3::x).toArray();
	}

	private static double[] zs(List<Vec3> points) {
		return points.stream().mapToDouble(Vec3::z).toArray();
	}

	/** Its next segment begun: the blocks its body crosses on it, in order. */
	private void begin(GolemGround ground) {
		while (!done()) {
			double dx = pointX[segment + 1] - pointX[segment], dz = pointZ[segment + 1] - pointZ[segment];
			length = Math.sqrt(dx * dx + dz * dz);
			if (length > EPSILON) {
				ux = dx / length;
				uz = dz / length;
				break;
			}
			segment++; // (no way to go: on to the next)
		}
		along = 0;
		nextIn = nextOut = 0;
		sweep = done() ? null : GolemGround.sweep(x, z, pointX[segment + 1], pointZ[segment + 1], ground.half());
	}

	/** The next block to go into on its segment (its sweep's index; -1: none): one it's over already isn't gone into again. */
	private int nextIn() {
		while (nextIn < sweep.size() && indexUnder(sweep.x(nextIn), sweep.z(nextIn)) >= 0 && sweep.get(nextIn).in() <= EPSILON) {
			nextIn++;
		}
		return nextIn < sweep.size() ? nextIn : -1;
	}

	/** The next block to come out of on its segment (-1: none before its end). */
	private int nextOut() {
		while (nextOut < sweep.size() && sweep.get(sweep.byOut()[nextOut]).out() >= length - EPSILON) {
			nextOut++; // (still over it at the end: on into the next segment)
		}
		return nextOut < sweep.size() ? sweep.byOut()[nextOut] : -1;
	}

	/**
	 * Walks on, {@code far} blocks at most; how far it went. It stops short once it's stepped up onto
	 * a higher floor (its feet rise before it goes on), at its end, and where it's stopped.
	 */
	double advance(GolemGround ground, double far) {
		double went = 0;
		while (stop == null && !done() && went < far - EPSILON) {
			double reach = Math.min(along + far - went, length);
			int in = nextIn(), out = nextOut();
			double inAt = in >= 0 ? sweep.get(in).in() : Double.POSITIVE_INFINITY;
			double outAt = out >= 0 ? sweep.get(out).out() : Double.POSITIVE_INFINITY;
			if (Math.min(inAt, outAt) <= reach + EPSILON) {
				boolean goingIn = inAt <= outAt; // (at once: in before out)
				double at = Math.max(along, goingIn ? inAt : outAt);
				went += at - along;
				moveTo(at);
				boolean rose;
				if (goingIn) {
					nextIn++;
					rose = goIn(ground, sweep.get(in), sweep.x(in), sweep.z(in));
				} else {
					nextOut++;
					rose = comeOut(sweep.x(out), sweep.z(out));
				}
				standing();
				if (rose) {
					break;
				}
				continue;
			}
			went += reach - along;
			moveTo(reach);
			if (reach >= length - EPSILON) {
				x = pointX[segment + 1]; // (exactly there)
				z = pointZ[segment + 1];
				segment++;
				begin(ground);
			}
			standing();
		}
		return went;
	}

	/** Its body goes into a block: judged; true when it steps up onto it. */
	private boolean goIn(GolemGround ground, GolemGround.Crossing c, int bx, int bz) {
		GolemGround.Cell cell = ground.cell(bx, bz, support);
		switch (cell.kind()) {
			case WALL, BARRED -> {
				stop = new Stop(cell.blame(), side(c), bx, bz);
				return false;
			}
			case VOID -> {
				addUnder(bx, bz, Double.NaN);
				return false;
			}
			default -> {
			}
		}
		if (cell.floor() > support + EPSILON) {
			GolemSteps.Stop why = free ? GolemSteps.freeStep(support, cell.floor()) : GolemSteps.step(support, cell.floor(), goal);
			if (why != null) {
				stop = new Stop(cell.blame(), side(c), bx, bz); // (the step it can't climb)
				return false;
			}
			BlockPos ceiling = ground.blocking(x, cell.floor(), z);
			if (ceiling != null) {
				stop = new Stop(ceiling, new Vec3(0, 1, 0), bx, bz); // (no room over it to step up)
				return false;
			}
			addUnder(bx, bz, cell.floor());
			support = cell.floor();
			return true;
		}
		addUnder(bx, bz, cell.floor());
		return false;
	}

	/** The side of its body that went into a block, as a direction in the world. */
	private static Vec3 side(GolemGround.Crossing c) {
		return c.alongX() ? new Vec3(c.sign(), 0, 0) : new Vec3(0, 0, c.sign());
	}

	/** Its body comes out of a block: the floor under it may drop, a step its rules judge. */
	private boolean comeOut(int bx, int bz) {
		int index = indexUnder(bx, bz);
		if (index < 0) {
			return false;
		}
		double left = underFloor[index];
		removeUnder(index);
		int highest = -1;
		for (int i = 0; i < under; i++) {
			if (!Double.isNaN(underFloor[i]) && (highest < 0 || underFloor[i] > underFloor[highest])) {
				highest = i;
			}
		}
		if (highest >= 0 && underFloor[highest] >= support - EPSILON) {
			return false; // (still over a floor as high)
		}
		GolemSteps.Stop why = highest < 0 ? GolemSteps.Stop.LOW
			: free ? GolemSteps.freeStep(support, underFloor[highest]) : GolemSteps.step(support, underFloor[highest], goal);
		if (why != null) {
			// (the air it can't drop through: over the floor it'd drop to, else over the block its middle is going into)
			int ax = highest >= 0 ? underX[highest] : (int) Math.floor(x), az = highest >= 0 ? underZ[highest] : (int) Math.floor(z);
			addUnder(bx, bz, left); // (it stays on the edge)
			stop = new Stop(new BlockPos(ax, (int) Math.floor(support + EPSILON), az), new Vec3(ux, 0, uz), ax, az);
			return false;
		}
		support = underFloor[highest];
		return false;
	}

	private void addUnder(int bx, int bz, double floor) {
		if (under == underX.length) {
			underX = java.util.Arrays.copyOf(underX, under * 2);
			underZ = java.util.Arrays.copyOf(underZ, under * 2);
			underFloor = java.util.Arrays.copyOf(underFloor, under * 2);
		}
		underX[under] = bx;
		underZ[under] = bz;
		underFloor[under++] = floor;
	}

	private void removeUnder(int index) {
		under--;
		underX[index] = underX[under];
		underZ[index] = underZ[under];
		underFloor[index] = underFloor[under];
	}

	private int indexUnder(int bx, int bz) {
		for (int i = 0; i < under; i++) {
			if (underX[i] == bx && underZ[i] == bz) {
				return i;
			}
		}
		return -1;
	}

	private void moveTo(double to) {
		x = pointX[segment] + ux * to;
		z = pointZ[segment] + uz * to;
		along = to;
	}

	/** The block its middle is over, its floor the one it stands on: the last block's middle it reached. */
	private void standing() {
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		int index = indexUnder(bx, bz);
		if (index >= 0 && Math.abs(underFloor[index] - support) < EPSILON) {
			standX = bx + 0.5;
			standY = support;
			standZ = bz + 0.5;
		}
	}

	/** Which of its segments it's on (from its first point: 0). */
	int segment() {
		return segment;
	}

	/** Whether it's at its last point. */
	boolean done() {
		return segment >= pointX.length - 1;
	}

	/** Why it stopped, or null. */
	@Nullable Stop stopped() {
		return stop;
	}

	/** Where its middle is now. */
	double x() {
		return x;
	}

	double z() {
		return z;
	}

	/** The floor its feet stand on now (where they're going: they rise and drop to it). */
	double support() {
		return support;
	}

	/** The last block's middle it reached, on its floor (or where it began, if none): where a walk cut short ends. */
	Vec3 stand() {
		return new Vec3(standX, standY, standZ);
	}

	/** Its points still to go to, from where it is now: what's left of it. */
	List<Vec3> rest() {
		List<Vec3> rest = new ArrayList<>();
		rest.add(new Vec3(x, support, z));
		for (int i = Math.min(segment + 1, pointX.length); i < pointX.length; i++) {
			rest.add(new Vec3(pointX[i], support, pointZ[i]));
		}
		return rest;
	}

	/** Its points moved by {@code by} (a field carried it). */
	void carry(Vec3 by) {
		pointX = pointX.clone();
		pointZ = pointZ.clone();
		for (int i = 0; i < pointX.length; i++) {
			pointX[i] += by.x;
			pointZ[i] += by.z;
		}
		x += by.x;
		z += by.z;
		support += by.y;
		standX += by.x;
		standY += by.y;
		standZ += by.z;
	}
}
