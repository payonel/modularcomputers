package com.payonel.modularcomputers.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A pathfinding walk's way back onto its line (the user's, 2026-11-14: Bug2's): the line from where
 * its command started to its target, which it walks while nothing's in its way; once something is,
 * a search, all at once in a tick, from where it stands for the nearest way back onto the line past
 * what's in its way (a block whose middle is a block from the line at most, farther along it than
 * where it left it and what stopped it, or its target), within the pathfinding range's blocks walked
 * ({@link #range()}, the {@code robot.pathfinding_range} setting) and the game's own budget for a search
 * that far ({@link #NODES_PER_BLOCK} places a block). An A* over the blocks round it, eight ways from
 * each, each step judged as its walk judges one ({@link GolemStride}: the same code), so a way it
 * finds is one its walk takes; in the loaded world (a chunk not loaded is nothing: no floor), which it
 * never writes into the server's shared cache of places. No mazes (the user's): a way round too long
 * or winding for the range or the budget is no way.
 *
 * <p>And a flight's way, the game's for fliers ({@link #flights}), made to agree with a flight's
 * moves: what bars them (fire, a fluid not water) is no way.
 */
public final class GolemWayfinder {
	/** How far a search goes, in blocks walked: the setting's, its default till the settings load. */
	private static volatile int range = com.payonel.modularcomputers.config.Defaults.Robot.PATHFINDING_RANGE;
	/** How many places a search explores at most, a block of its range: the game's own (its PathFinder's: its follow range's 16 a block). */
	public static final int NODES_PER_BLOCK = 16;
	/** How much more the distance left weighs than the way walked: the game's own (its PathFinder's), quicker than the shortest way, a little longer. */
	private static final float HEURISTIC_WEIGHT = 1.5f;
	/** How near its line a block's middle is on it, in blocks, measured across (the user's: a diagonal runs through blocks' corners). */
	public static final double NEAR_LINE = 1;
	/** Its eight ways from a block: straight, then diagonal. */
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private GolemWayfinder() {
	}

	/** How far a search goes, in blocks walked. */
	public static int range() {
		return range;
	}

	/** Sets how far (a reload). */
	public static void configure(int blocks) {
		range = Math.max(1, blocks);
	}

	/** A search's outcome: its way's blocks' middles from where it began to where it comes back onto the line (null: none), and how many places it explored. */
	public record Way(@Nullable List<Vec3> points, int explored) {
	}

	/** A place explored: a block, its floor, its cost and the way walked to it, its estimate, and where it was reached from. */
	private static final class Place {
		final int x, z;
		final double floor;
		float g = Float.POSITIVE_INFINITY, f, walked;
		@Nullable Place from;
		boolean closed;

		Place(int x, double floor, int z) {
			this.x = x;
			this.floor = floor;
			this.z = z;
		}

		Vec3 middle() {
			return new Vec3(x + 0.5, floor, z + 0.5);
		}
	}

	private record Queued(Place place, float f) {
	}

	/**
	 * The way for {@code golem} from the block whose middle is {@code from} (its feet's height) back
	 * onto the line from {@code lineFrom} to {@code lineTo}, farther along it than {@code past} blocks
	 * (or to {@code lineTo}'s block): the nearest it finds.
	 */
	public static Way search(Mob golem, Vec3 from, Vec3 lineFrom, Vec3 lineTo, double past) {
		GolemGround ground = new GolemGround(golem, false);
		Line line = new Line(lineFrom, lineTo, past);
		int reach = range;
		int budget = NODES_PER_BLOCK * reach;
		Long2ObjectMap<Place> places = new Long2ObjectOpenHashMap<>();
		PriorityQueue<Queued> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));
		Place start = new Place((int) Math.floor(from.x), from.y, (int) Math.floor(from.z));
		start.g = 0;
		start.f = (float) line.estimate(start.x + 0.5, start.z + 0.5) * HEURISTIC_WEIGHT;
		places.put(key(start.x, start.floor, start.z), start);
		open.add(new Queued(start, start.f));
		int explored = 0;
		while (!open.isEmpty() && explored < budget) {
			Queued next = open.poll();
			Place place = next.place();
			if (place.closed || next.f() != place.f) {
				continue; // (stale: explored already, or queued again since at a better estimate)
			}
			place.closed = true;
			explored++;
			if (line.back(place.x, place.z)) {
				return new Way(way(place), explored);
			}
			for (int[] way : WAYS) {
				int x = place.x + way[0], z = place.z + way[1];
				float step = way[0] != 0 && way[1] != 0 ? (float) Math.sqrt(2) : 1;
				if (place.walked + step > reach) {
					continue; // (past its range)
				}
				GolemStride stride = new GolemStride(ground, place.x + 0.5, place.z + 0.5, x + 0.5, z + 0.5, place.floor, 0, true);
				while (stride.stopped() == null && !stride.done()) {
					stride.advance(ground, Double.POSITIVE_INFINITY);
				}
				if (stride.stopped() != null) {
					continue; // (no step there: its walk couldn't take it)
				}
				long key = key(x, stride.support(), z);
				Place there = places.get(key);
				if (there == null) {
					there = new Place(x, stride.support(), z);
					places.put(key, there);
				} else if (there.closed) {
					continue;
				}
				float g = place.g + step;
				if (g < there.g) {
					there.from = place;
					there.g = g;
					there.walked = place.walked + step;
					there.f = g + (float) line.estimate(x + 0.5, z + 0.5) * HEURISTIC_WEIGHT;
					open.add(new Queued(there, there.f));
				}
			}
		}
		return new Way(null, explored);
	}

	/** A place's key: its block, its elevation (the block its feet are in). */
	private static long key(int x, double floor, int z) {
		return BlockPos.asLong(x, GolemSteps.elevation(floor), z);
	}

	/** Its way to {@code end}, from where it began: its blocks' middles, on their floors. */
	private static List<Vec3> way(Place end) {
		List<Vec3> points = new ArrayList<>();
		for (Place place = end; place != null; place = place.from) {
			points.add(place.middle());
		}
		java.util.Collections.reverse(points);
		return points;
	}

	/**
	 * A walk's line, across (heights aside), and how far along it a way must come back past: whether
	 * a block is back on it, and how far a place is from coming back (its search's estimate).
	 */
	private record Line(double fromX, double fromZ, double toX, double toZ, double length, double past) {
		Line(Vec3 from, Vec3 to, double past) {
			this(from.x, from.z, to.x, to.z, Math.hypot(to.x - from.x, to.z - from.z), past);
		}

		/** Whether the block {@code x}, {@code z} is back on the line: its target's, or its middle near it, past where it must be. */
		boolean back(int x, int z) {
			if (x == (int) Math.floor(toX) && z == (int) Math.floor(toZ)) {
				return true;
			}
			double mx = x + 0.5, mz = z + 0.5;
			double along = along(mx, mz);
			return along > past + EPSILON && along <= length + EPSILON && across(mx, mz, along) <= NEAR_LINE + EPSILON;
		}

		/** How far from coming back onto the line past where it must a point is, at least, in blocks. */
		double estimate(double x, double z) {
			double along = Math.max(Math.min(along(x, z), length), Math.min(Math.max(past, 0), length));
			return Math.max(0, across(x, z, along) - NEAR_LINE);
		}

		double along(double x, double z) {
			return length < EPSILON ? 0 : ((x - fromX) * (toX - fromX) + (z - fromZ) * (toZ - fromZ)) / length;
		}

		/** How far a point is from the line's point {@code along} it. */
		double across(double x, double z, double along) {
			double t = length < EPSILON ? 0 : along / length;
			return Math.hypot(x - (fromX + (toX - fromX) * t), z - (fromZ + (toZ - fromZ) * t));
		}

		private static final double EPSILON = GolemGround.EPSILON;
	}

	/** How far along the line from {@code from} to {@code to} a point is, in blocks, across (heights aside). */
	public static double along(Vec3 from, Vec3 to, Vec3 at) {
		return new Line(from, to, 0).along(at.x, at.z);
	}

	/** How far a point is from the line from {@code from} to {@code to} (its nearest point on it), in blocks, across. */
	public static double fromLine(Vec3 from, Vec3 to, Vec3 at) {
		Line line = new Line(from, to, 0);
		return line.across(at.x, at.z, Math.max(0, Math.min(line.length(), line.along(at.x, at.z))));
	}

	// ---- flights: the game's way for fliers, agreeing with a flight's moves --------------------

	/**
	 * The game's pathfinder for fliers (a bee's), {@code nodes} places at most, but what bars a
	 * golem's moves (fire, a fluid not water: {@link GolemGround#bars}) is blocked to it, so no way it
	 * finds goes through one, as no flight's move does; what burns others only (a magma block, a lit
	 * campfire) is as it is to the rest; and a chunk not loaded is nothing, kept out of the server's
	 * shared cache of places.
	 */
	public static PathFinder flights(int nodes, Level world, java.util.function.Consumer<FlyNodeEvaluator> made) {
		FlyNodeEvaluator evaluator = new FlyNodeEvaluator() {
			@Override
			public void prepare(PathNavigationRegion region, Mob mob) {
				super.prepare(region, mob);
				currentContext = new Unbarred(region, mob, world);
			}
		};
		made.accept(evaluator);
		return new PathFinder(evaluator, nodes);
	}

	/**
	 * What a place is to a golem's flight: what bars it blocked; what burns others only, as it is
	 * (open, or blocked if no mob's way goes through it); a place in a chunk not loaded open (air: no
	 * floor) without going into the world's cache of places (the server's, every mob's): seen as
	 * nothing there, it would stay nothing to them once its chunk loaded.
	 */
	private static final class Unbarred extends PathfindingContext {
		private final Level world;
		private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

		Unbarred(PathNavigationRegion region, Mob mob, Level world) {
			super(region, mob);
			this.world = world;
		}

		@Override
		public PathType getPathTypeFromState(int x, int y, int z) {
			if (world.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
				return PathType.OPEN;
			}
			var state = level().getBlockState(at.set(x, y, z));
			if (GolemGround.bars(state)) {
				return PathType.BLOCKED;
			}
			PathType type = super.getPathTypeFromState(x, y, z);
			if (type == PathType.FIRE) {
				return state.isPathfindable(PathComputationType.LAND) ? PathType.OPEN : PathType.BLOCKED; // (burning to others, nothing to it)
			}
			return type;
		}
	}
}
