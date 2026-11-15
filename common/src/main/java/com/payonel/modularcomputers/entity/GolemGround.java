package com.payonel.modularcomputers.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

/**
 * What a golem's walk stands on and passes (the user's, 2026-11-14), judged one way for its walks
 * and for its wayfinder's search, so a way the search finds is a way its walk takes, and nothing its
 * walk refuses is a way: a block's floor (the highest its body fits on, from a jump over its feet
 * to its drop under them), a block with no floor there (nothing to stand on: a drop it can't take),
 * a block its body doesn't fit in at its height (a wall), and a block whose fire or fluid bars it
 * (fire, a fluid that isn't water: {@link GolemSteps#bars}). It sees the loaded world as it is, a
 * chunk not loaded as nothing (air: no floor), never loading one by a look; what it's seen is kept
 * for one look (a tick's, a search's): the world changes between them.
 */
public final class GolemGround implements BlockGetter {
	/** Heights this close count as the same, in blocks (floating point's). */
	static final double EPSILON = 1e-7;

	private final Level world;
	private final Mob golem;
	/** Whether other entities are in its way too, shoved or stopping it (a walk's), or only blocks (a search's: entities move, the user's). */
	private final boolean entities;
	private final double half;
	private final double height;
	private final CollisionContext context;
	/** The chunks it's looked into (a chunk not loaded: {@link #NOTHING}). */
	private final Long2ObjectMap<Object> seen = new Long2ObjectOpenHashMap<>();
	/** What it sees in a chunk not loaded: nothing. */
	private static final Object NOTHING = new Object();
	/** Its blocks judged, by block and elevation: a few feet heights each (mostly one), what it found for each. */
	private final Long2ObjectMap<Looks> cells = new Long2ObjectOpenHashMap<>();

	/** The ground as {@code golem} meets it now (its size, its collisions); {@code entities}: whether other entities are in its way too ({@link #inTheWay}). */
	public GolemGround(Mob golem, boolean entities) {
		this.world = golem.level();
		this.golem = golem;
		this.entities = entities;
		half = golem.getBbWidth() / 2;
		height = golem.getBbHeight();
		context = CollisionContext.of(golem);
	}

	/** Half its body's width, in blocks. */
	double half() {
		return half;
	}

	/**
	 * Whether a block's state bars every move of a golem's (the user's, 2026-11-14): fire, or a fluid
	 * that isn't water (lava, any other mod's); water passes, still or flowing, as air does.
	 */
	public static boolean bars(BlockState state) {
		FluidState fluid = state.getFluidState();
		return GolemSteps.bars(state.is(BlockTags.FIRE), !fluid.isEmpty(), fluid.is(FluidTags.WATER));
	}

	/** What a block is to a step: a floor, nothing (no floor within its drop), a wall, or barred (fire, a fluid not water). */
	enum Kind {
		FLOOR,
		VOID,
		WALL,
		BARRED
	}

	/** A block judged: what it is, its floor's height (a floor's; else NaN), and the block to blame when it stops a step. */
	record Cell(Kind kind, double floor, BlockPos blame) {
	}

	/** A block's judgments, for each feet height it was judged from. */
	private static final class Looks {
		double[] feet = new double[1];
		Cell[] cells = new Cell[1];
		int count;
	}

	/**
	 * The block {@code x}, {@code z} to a golem whose feet are at {@code feet}: its floor there (the
	 * highest its body fits on, a jump above its feet to its drop below them), or why it has none.
	 */
	Cell cell(int x, int z, double feet) {
		Looks looks = cells.computeIfAbsent(BlockPos.asLong(x, GolemSteps.elevation(feet), z), key -> new Looks());
		for (int i = 0; i < looks.count; i++) {
			if (looks.feet[i] == feet) {
				return looks.cells[i];
			}
		}
		Cell cell = judge(x, z, feet);
		if (looks.count == looks.feet.length) {
			looks.feet = java.util.Arrays.copyOf(looks.feet, looks.count * 2);
			looks.cells = java.util.Arrays.copyOf(looks.cells, looks.count * 2);
		}
		looks.feet[looks.count] = feet;
		looks.cells[looks.count++] = cell;
		return cell;
	}

	private Cell judge(int x, int z, double feet) {
		double middleX = x + 0.5, middleZ = z + 0.5;
		double floor = floorAt(middleX, middleZ, feet);
		if (Double.isNaN(floor)) {
			BlockPos at = BlockPos.containing(middleX, feet + EPSILON, middleZ);
			if (fits(middleX, feet, middleZ)) {
				BlockPos barred = barred(x, z, feet - GolemSteps.MAX_DROP - 1, feet + height);
				return barred != null ? new Cell(Kind.BARRED, Double.NaN, barred) : new Cell(Kind.VOID, Double.NaN, at); // (nothing to stand on: the air it'd step into)
			}
			BlockPos wall = getBlockState(at).isAir() ? at.above() : at;
			return new Cell(Kind.WALL, Double.NaN, wall);
		}
		BlockPos barred = barred(x, z, Math.min(floor, feet), Math.max(floor, feet) + height);
		return barred != null ? new Cell(Kind.BARRED, Double.NaN, barred) : new Cell(Kind.FLOOR, floor, BlockPos.containing(middleX, floor - EPSILON, middleZ));
	}

	/** The first block in column {@code x}, {@code z} from height {@code from} to {@code to} whose fire or fluid bars it, or null. */
	private @Nullable BlockPos barred(int x, int z, double from, double to) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int y = (int) Math.floor(from + EPSILON); y <= (int) Math.floor(to - EPSILON); y++) {
			if (bars(getBlockState(at.set(x, y, z)))) {
				return at.immutable();
			}
		}
		return null;
	}

	/**
	 * Where its feet would stand at the block whose middle is {@code x}, {@code z}: the highest floor
	 * over {@code feet}, a jump at most, its body fitting on it; else, its body fitting where it is,
	 * the first floor under it (a drop it falls straight down: never one under something solid), a
	 * drop's at most, its body fitting on it; NaN for none.
	 */
	private double floorAt(double x, double z, double feet) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		for (int y = (int) Math.floor(feet + GolemSteps.MAX_JUMP); y >= (int) Math.floor(feet) - 1; y--) {
			var shape = getBlockState(at.set(bx, y, bz)).getCollisionShape(this, at, context);
			if (!shape.isEmpty()) {
				double surface = y + shape.max(Direction.Axis.Y);
				if (surface > feet + EPSILON && surface <= feet + GolemSteps.MAX_JUMP + EPSILON && fits(x, surface, z)) {
					return surface; // (up onto it)
				}
			}
		}
		if (!fits(x, feet, z)) {
			return Double.NaN; // (no room at its height, none a jump up: a wall)
		}
		for (int y = (int) Math.floor(feet + EPSILON); y >= (int) Math.floor(feet) - GolemSteps.MAX_DROP - 1; y--) {
			var shape = getBlockState(at.set(bx, y, bz)).getCollisionShape(this, at, context);
			if (shape.isEmpty()) {
				continue;
			}
			double surface = y + shape.max(Direction.Axis.Y);
			if (surface > feet + EPSILON) {
				continue; // (over its feet: no floor under them)
			}
			return surface >= feet - GolemSteps.MAX_DROP - EPSILON && fits(x, surface, z) ? surface : Double.NaN; // (the first under it: farther than its drop, or no room on it, none)
		}
		return Double.NaN;
	}

	/** Whether its body fits with its feet at {@code x}, {@code y}, {@code z}: no block's collision in it. */
	boolean fits(double x, double y, double z) {
		return blocking(x, y, z) == null;
	}

	/** The first block in its body's way with its feet at {@code x}, {@code y}, {@code z}, or null. */
	@Nullable BlockPos blocking(double x, double y, double z) {
		AABB box = new AABB(x - half, y, z - half, x + half, y + height, z + half);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		// (from the block under its feet: a fence's, a wall's collision rises over its own block)
		for (int bx = (int) Math.floor(box.minX); bx <= (int) Math.floor(box.maxX - EPSILON); bx++) {
			for (int bz = (int) Math.floor(box.minZ); bz <= (int) Math.floor(box.maxZ - EPSILON); bz++) {
				for (int by = (int) Math.floor(box.minY) - 1; by <= (int) Math.floor(box.maxY - EPSILON); by++) {
					at.set(bx, by, bz);
					var shape = getBlockState(at).getCollisionShape(this, at, context);
					if (shape.isEmpty()) {
						continue;
					}
					if (shape == Shapes.block()) {
						if (box.intersects(bx, by, bz, bx + 1, by + 1, bz + 1)) {
							return at.immutable(); // (a whole block: its bounds are it)
						}
						continue;
					}
					for (AABB part : shape.toAabbs()) {
						if (part.move(at).intersects(box)) {
							return at.immutable();
						}
					}
				}
			}
		}
		return null;
	}

	/** What stopped its body that isn't a block: a thing's name (its type's registry id), the side of its body that met it (a world direction), and where it is. */
	record Met(String name, net.minecraft.world.phys.Vec3 side, BlockPos at) {
	}

	/**
	 * Its body going on from {@code fromX}, {@code fromZ} to {@code toX}, {@code toZ}, its feet at
	 * {@code feet} (a walk's, with entities): what's in the space it goes into that can be pushed,
	 * shoved out of it ({@link GolemShove}); the first thing there it can't push out of its way, or
	 * null (its way's clear; always, without entities: a search's).
	 */
	@Nullable Met inTheWay(double fromX, double fromZ, double toX, double toZ, double feet) {
		if (!entities) {
			return null;
		}
		AABB to = new AABB(toX - half, feet, toZ - half, toX + half, feet + height, toZ + half);
		var met = GolemShove.clear(golem, to, new net.minecraft.world.phys.Vec3(toX - fromX, 0, toZ - fromZ));
		return met == null ? null : new Met(GolemShove.name(met), GolemShove.side(to, met), met.blockPosition());
	}

	// ---- its body's way across the blocks: a supercover as wide as it is -----------------------

	/**
	 * A block its body enters on a straight move: where, how far along the move it goes in and comes
	 * out (in blocks; one it's in from the start goes in at 0, one it's still in at the end comes out
	 * at the move's length), and the side of its body that went in first (the world's axis, x or z,
	 * and which way along it: its body's face that meets what's there).
	 */
	record Crossing(int x, int z, double in, double out, boolean alongX, int sign) {
	}

	/**
	 * The blocks a body crosses on a straight move ({@link Crossing}s, each {@code dx}, {@code dz} from
	 * its own block: a step's from a block's middle are worked out once, for every block), in the
	 * order it goes into them, and {@code byOut} their order coming out.
	 */
	record Sweep(List<Crossing> crossings, int[] byOut, int dx, int dz) {
		int size() {
			return crossings.size();
		}

		Crossing get(int index) {
			return crossings.get(index);
		}

		int x(int index) {
			return crossings.get(index).x() + dx;
		}

		int z(int index) {
			return crossings.get(index).z() + dz;
		}

		/** As blocks in the world, each where it is. */
		List<Crossing> placed() {
			List<Crossing> placed = new ArrayList<>(crossings.size());
			for (Crossing c : crossings) {
				placed.add(new Crossing(c.x() + dx, c.z() + dz, c.in(), c.out(), c.alongX(), c.sign()));
			}
			return placed;
		}

		static Sweep of(List<Crossing> crossings, int dx, int dz) {
			Integer[] order = new Integer[crossings.size()];
			for (int i = 0; i < order.length; i++) {
				order[i] = i;
			}
			java.util.Arrays.sort(order, (a, b) -> Double.compare(crossings.get(a).out(), crossings.get(b).out()));
			int[] byOut = new int[order.length];
			for (int i = 0; i < order.length; i++) {
				byOut[i] = order[i];
			}
			return new Sweep(crossings, byOut, dx, dz);
		}
	}

	/**
	 * Every block a body {@code half} wide either side of its middle crosses moving straight from
	 * {@code fromX}, {@code fromZ} to {@code toX}, {@code toZ} (a supercover: a corner its body
	 * clips is crossed), in the order it goes into them.
	 */
	static Sweep sweep(double fromX, double fromZ, double toX, double toZ, double half) {
		double steppedX = toX - fromX, steppedZ = toZ - fromZ;
		if (middle(fromX) && middle(fromZ) && Math.abs(steppedX) <= 1 && Math.abs(steppedZ) <= 1 && whole(steppedX) && whole(steppedZ)) {
			// (a step from a block's middle to a neighbour's, or none: the same crossings for any block, worked out once)
			Sweep step = STEPS.computeIfAbsent(new Step((int) Math.rint(steppedX), (int) Math.rint(steppedZ), half),
				key -> Sweep.of(crossings(0.5, 0.5, 0.5 + key.x(), 0.5 + key.z(), key.half()), 0, 0));
			return new Sweep(step.crossings(), step.byOut(), (int) Math.floor(fromX), (int) Math.floor(fromZ));
		}
		return Sweep.of(crossings(fromX, fromZ, toX, toZ, half), 0, 0);
	}

	/** A step from a block's middle: which way, a block at most each way, and its body's half width. */
	private record Step(int x, int z, double half) {
	}

	/** The crossings of each step from a block's middle to a neighbour's, worked out once. */
	private static final Map<Step, Sweep> STEPS = new java.util.concurrent.ConcurrentHashMap<>();

	private static boolean middle(double at) {
		return Math.abs(at - Math.floor(at) - 0.5) < EPSILON;
	}

	private static boolean whole(double at) {
		return Math.abs(at - Math.rint(at)) < EPSILON;
	}

	private static List<Crossing> crossings(double fromX, double fromZ, double toX, double toZ, double half) {
		double dx = toX - fromX, dz = toZ - fromZ;
		double length = Math.sqrt(dx * dx + dz * dz);
		double ux = length > 0 ? dx / length : 0, uz = length > 0 ? dz / length : 0;
		List<Crossing> crossings = new ArrayList<>();
		for (int x = (int) Math.floor(Math.min(fromX, toX) - half); x <= (int) Math.floor(Math.max(fromX, toX) + half); x++) {
			double[] across = overlap(fromX, ux, x, half, length);
			if (across == null) {
				continue;
			}
			double first = Math.max(0, across[0]), last = Math.max(0, across[1]);
			double zLow = Math.min(fromZ + uz * first, fromZ + uz * last) - half;
			double zHigh = Math.max(fromZ + uz * first, fromZ + uz * last) + half;
			for (int z = (int) Math.floor(zLow); z <= (int) Math.floor(zHigh); z++) {
				double[] down = overlap(fromZ, uz, z, half, length);
				if (down == null) {
					continue;
				}
				double in = Math.max(across[0], down[0]), out = Math.min(across[1], down[1]);
				if (out - in > EPSILON || length == 0 && out >= in) {
					boolean alongX = across[0] >= down[0]; // (the side that came to it last went in: the other was already over it)
					int sign = alongX ? (int) Math.signum(ux) : (int) Math.signum(uz);
					if (in <= 0) {
						alongX = Math.abs(ux) >= Math.abs(uz); // (in it from the start: met as it goes)
						sign = alongX ? (int) Math.signum(ux) : (int) Math.signum(uz);
					}
					crossings.add(new Crossing(x, z, Math.max(0, in), out, alongX, sign));
				}
			}
		}
		crossings.sort((a, b) -> a.in != b.in ? Double.compare(a.in, b.in) : Double.compare(a.out, b.out));
		return crossings;
	}

	/**
	 * How far along a move (from {@code from}, {@code way} a unit a block, {@code length} long) a body
	 * {@code half} wide either side overlaps block {@code at} on one axis (strictly: touching isn't
	 * overlapping): {in, out}, clamped to the move, or null for never.
	 */
	private static double @Nullable [] overlap(double from, double way, int at, double half, double length) {
		if (Math.abs(way) < EPSILON) {
			return from + half > at + EPSILON && from - half < at + 1 - EPSILON ? new double[] {Double.NEGATIVE_INFINITY, length} : null;
		}
		double a = (at - half - from) / way, b = (at + 1 + half - from) / way;
		double in = Math.min(a, b), out = Math.min(Math.max(a, b), length);
		if (out < Math.max(in, 0) || out <= in + EPSILON) {
			return null;
		}
		if (in < 0 && out <= EPSILON && length > 0) {
			return null; // (left behind at the start)
		}
		return new double[] {in, out};
	}

	// ---- the world as it sees it ---------------------------------------------------------------

	/** A chunk as it is, or null when it isn't loaded (nothing there to it, and no look loads it). */
	private @Nullable ChunkAccess chunk(int chunkX, int chunkZ) {
		long key = ChunkPos.pack(chunkX, chunkZ);
		Object chunk = seen.get(key);
		if (chunk == null) {
			chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
			seen.put(key, chunk == null ? NOTHING : chunk);
		}
		return chunk instanceof ChunkAccess loaded ? loaded : null;
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		ChunkAccess chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
		return chunk == null ? null : chunk.getBlockEntity(pos);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		ChunkAccess chunk = isOutsideBuildHeight(pos) ? null : chunk(pos.getX() >> 4, pos.getZ() >> 4);
		return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		ChunkAccess chunk = isOutsideBuildHeight(pos) ? null : chunk(pos.getX() >> 4, pos.getZ() >> 4);
		return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
	}

	@Override
	public int getHeight() {
		return world.getHeight();
	}

	@Override
	public int getMinY() {
		return world.getMinY();
	}
}
