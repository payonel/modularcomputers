package com.payonel.modularcomputers.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jspecify.annotations.Nullable;

/**
 * A walk's way, found a slice a tick (a robot's global planner): an A* search of its own, over the
 * game's moves for a walking mob ({@link WalkNodeEvaluator}: what it can step, jump and drop, and
 * the costs its malus gives), in the loaded world (unloaded chunks are seen as nothing: no floor to
 * walk on), a slice of server time a tick ({@link #NANOS_PER_TICK}). It searches a leg at a time
 * (the user's, 2026-11-14): only places within the pathfinding range's blocks walked of where the
 * leg starts ({@link #range()}, the {@code pathfinding_range} setting); a target past them, its way
 * is to the place it explored nearest the target, the robot searching on from that leg's end, and
 * a leg that comes no nearer is no way on (blocked).
 */
public final class GolemWayfinder {
	/** How far a leg searches by default, in blocks walked: the game's own pathfinding range for its mobs. */
	public static final int DEFAULT_RANGE = 96;
	/** The setting that sets it. */
	public static final String RANGE_KEY = "pathfinding_range";
	private static volatile int range = DEFAULT_RANGE;
	/** How much server time it searches a tick, at most, in nanoseconds: a millisecond, as a computer's board calls get. */
	public static final long NANOS_PER_TICK = 1_000_000;
	/** How many places it explores between looks at the clock: a slice runs over its time by these at most (about 0.1 ms). */
	private static final int NODES_BETWEEN_CLOCKS = 32;
	/** How much more the distance left weighs than the way walked: the game's own (its PathFinder's), quicker than the shortest way, a little longer. */
	private static final float HEURISTIC_WEIGHT = 1.5f;
	/** How many moves a place has at most (the game's: four straight, four diagonal, a few spare). */
	private static final int MOST_MOVES = 32;

	/** A place explored: where, its cost from the start, its estimate through it, and where it was reached from. */
	private static final class Place {
		final int x, y, z;
		final PathType type;
		/** Its cost from the start (none yet: unreached), and its estimate through it. */
		float g = Float.POSITIVE_INFINITY, f, h;
		/** How far its way from the start walks, in blocks (its cost without the malus). */
		float walked;
		@Nullable Place from;
		boolean closed;

		Place(Node node) {
			x = node.x;
			y = node.y;
			z = node.z;
			type = node.type;
		}
	}

	/** A place queued, at the estimate it had then (a later, better one queues it again: this one's then stale). */
	private record Queued(Place place, float f) {
	}

	private final BlockPos target;
	private final Moves moves = new Moves();
	private final Long2ObjectMap<Place> places = new Long2ObjectOpenHashMap<>();
	private final PriorityQueue<Queued> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));
	private final Node[] neighbors = new Node[MOST_MOVES];
	private final Loaded world;
	/** How far this leg searches, in blocks walked (the setting's, as it began). */
	private final int reach = range;
	private @Nullable Place nearest;
	private @Nullable Place reached;
	private boolean finished;
	private int explored;

	/** How far a leg searches, in blocks walked. */
	public static int range() {
		return range;
	}

	/** Sets how far (a reload: a leg searching keeps the range it began with). */
	public static void configure(int blocks) {
		range = Math.max(1, blocks);
	}

	/** A search for {@code mob}'s way from where it stands to {@code target} (its feet's block). */
	public GolemWayfinder(Mob mob, BlockPos target) {
		this.world = new Loaded(mob.level(), mob.blockPosition());
		this.target = onSurface(mob.level(), target);
		moves.prepare(world, mob);
		Node start = moves.getStart();
		if (start == null) {
			finished = true; // (nowhere to start from: no way)
			return;
		}
		Place first = place(start);
		first.g = 0;
		first.h = distance(first);
		first.f = first.h * HEURISTIC_WEIGHT;
		nearest = first;
		open.add(new Queued(first, first.f));
	}

	/**
	 * The game's way of judging a target: in the air, the ground under it (or the first air over it,
	 * in a block); in a chunk not loaded, as it is. So a walk to a point over a drop finds the floor
	 * there, not nothing.
	 */
	private static BlockPos onSurface(Level level, BlockPos pos) {
		var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		if (chunk == null) {
			return pos;
		}
		BlockPos.MutableBlockPos at = pos.mutable();
		if (chunk.getBlockState(at).isAir()) {
			at.move(Direction.DOWN);
			while (at.getY() >= level.getMinY() && chunk.getBlockState(at).isAir()) {
				at.move(Direction.DOWN);
			}
			if (at.getY() >= level.getMinY()) {
				return at.above().immutable();
			}
			at.setY(pos.getY() + 1);
			while (at.getY() <= level.getMaxY() && chunk.getBlockState(at).isAir()) {
				at.move(Direction.UP);
			}
		}
		while (at.getY() <= level.getMaxY() && chunk.getBlockState(at).isSolid()) {
			at.move(Direction.UP);
		}
		return at.immutable();
	}

	/**
	 * Searches on, for {@code nanos} of server time at most; true once it's done: the target found, or
	 * every place it could reach within its range explored.
	 */
	public boolean search(long nanos) {
		long until = System.nanoTime() + nanos;
		world.forget(); // (chunks loaded since its last slice are seen now)
		int sinceClock = 0;
		while (!finished) {
			if (++sinceClock >= NODES_BETWEEN_CLOCKS) {
				sinceClock = 0;
				if (System.nanoTime() >= until) {
					return false;
				}
			}
			step();
		}
		return true;
	}

	/** One place explored: its moves' places queued, or the search done. */
	private void step() {
		Queued next = open.poll();
		if (next == null) {
			finish();
			return;
		}
		Place place = next.place();
		if (place.closed || next.f() != place.f) {
			return; // (stale: explored already, or queued again since at a better estimate)
		}
		place.closed = true;
		explored++;
		if (place.x == target.getX() && place.y == target.getY() && place.z == target.getZ()) {
			reached = place;
			finish();
			return;
		}
		Node at = moves.at(place.x, place.y, place.z);
		int count = moves.getNeighbors(neighbors, at);
		for (int i = 0; i < count; i++) {
			Node move = neighbors[i];
			Place to = places.get(BlockPos.asLong(move.x, move.y, move.z));
			if (to == null) {
				to = place(move);
			} else if (to.closed) {
				continue;
			}
			float step = at.distanceTo(move);
			if (place.walked + step > reach) {
				continue; // (past this leg's range: the next leg's, from its end)
			}
			float g = place.g + step + move.costMalus;
			if (g < to.g) {
				to.from = place;
				to.g = g;
				to.walked = place.walked + step;
				to.h = distance(to);
				to.f = g + to.h * HEURISTIC_WEIGHT;
				open.add(new Queued(to, to.f));
				if (to.h < nearest.h) {
					nearest = to;
				}
			}
		}
	}

	private Place place(Node node) {
		Place place = new Place(node);
		places.put(BlockPos.asLong(node.x, node.y, node.z), place);
		return place;
	}

	private float distance(Place place) {
		float dx = place.x - target.getX(), dy = place.y - target.getY(), dz = place.z - target.getZ();
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private void finish() {
		if (!finished) {
			finished = true;
			moves.done();
			open.clear();
		}
	}

	/** Whether its way goes all the way to the target. */
	public boolean reached() {
		return reached != null;
	}

	/** How many places it has explored. */
	public int explored() {
		return explored;
	}

	/** The target it searches for, judged as the game does ({@link #onSurface}). */
	public BlockPos target() {
		return target;
	}

	/**
	 * Its way, once done: to the target, or to the place nearest it that it explored (a leg); null when
	 * it found nowhere to go but where it stands.
	 */
	public @Nullable Path way() {
		Place end = reached != null ? reached : nearest;
		if (end == null || end.from == null) {
			return null;
		}
		List<Node> nodes = new ArrayList<>();
		for (Place place = end; place != null; place = place.from) {
			Node node = new Node(place.x, place.y, place.z);
			node.type = place.type;
			nodes.add(node);
		}
		java.util.Collections.reverse(nodes); // (built from its end: a way thousands of places long, reversed once, not shifted a place at a time)
		return new Path(nodes, target, reached != null);
	}

	/** Stops searching (its caches let go). */
	public void cancel() {
		finish();
	}

	/**
	 * The game's moves for a walking mob, from any place: its node cache cleared at each (the game keys
	 * its nodes by a hash that repeats every 128 or 256 blocks, which a search of its own range never
	 * spans, and this one may).
	 */
	private static final class Moves extends WalkNodeEvaluator {
		Node at(int x, int y, int z) {
			nodes.clear();
			return getStartNode(new BlockPos(x, y, z));
		}

		@Override
		public void prepare(PathNavigationRegion region, Mob mob) {
			super.prepare(region, mob);
			currentContext = new Unpoisoned((Loaded) region, mob);
		}

		/**
		 * What a place is, as the game judges it, but a place in a chunk not loaded is open (air: no
		 * floor) without going into the world's cache of places (the server's, every mob's): seen as
		 * nothing there, it would stay nothing to them once its chunk loaded, till the place changed.
		 */
		private static final class Unpoisoned extends PathfindingContext {
			private final Loaded region;

			Unpoisoned(Loaded region, Mob mob) {
				super(region, mob);
				this.region = region;
			}

			@Override
			public PathType getPathTypeFromState(int x, int y, int z) {
				return region.loaded(x >> 4, z >> 4) ? super.getPathTypeFromState(x, y, z) : PathType.OPEN;
			}
		}
	}

	/**
	 * The world as the search sees it: every loaded chunk as it is, any other nothing (air), looked up
	 * as it's reached (the game's region holds a fixed box of chunks, the search's range round it).
	 */
	private static final class Loaded extends PathNavigationRegion {
		private final Level world;
		private final EmptyLevelChunk nothing;
		private final Long2ObjectMap<ChunkAccess> seen = new Long2ObjectOpenHashMap<>();

		Loaded(Level world, BlockPos at) {
			super(world, at, at);
			this.world = world;
			nothing = new EmptyLevelChunk(world, ChunkPos.ZERO, world.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
		}

		/** Forgets the chunks it's seen (one slice's: a chunk loaded or unloaded since is seen as it is). */
		void forget() {
			seen.clear();
		}

		private ChunkAccess chunk(int chunkX, int chunkZ) {
			long key = ChunkPos.pack(chunkX, chunkZ);
			ChunkAccess chunk = seen.get(key);
			if (chunk == null) {
				chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk == null) {
					chunk = nothing;
				}
				seen.put(key, chunk);
			}
			return chunk;
		}

		/** Whether a chunk is loaded (seen as it is). */
		boolean loaded(int chunkX, int chunkZ) {
			return chunk(chunkX, chunkZ) != nothing;
		}

		private ChunkAccess chunk(BlockPos pos) {
			return chunk(pos.getX() >> 4, pos.getZ() >> 4);
		}

		@Override
		public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
			return chunk(chunkX, chunkZ);
		}

		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return chunk(pos).getBlockEntity(pos);
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			return isOutsideBuildHeight(pos) ? Blocks.AIR.defaultBlockState() : chunk(pos).getBlockState(pos);
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return isOutsideBuildHeight(pos) ? Fluids.EMPTY.defaultFluidState() : chunk(pos).getFluidState(pos);
		}
	}
}
