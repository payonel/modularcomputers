package com.payonel.modularcomputers.gametest;

import java.util.ArrayDeque;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A maze, generated from a seed (a depth-first carve: a perfect maze, one way between any two
 * cells), for the golem's pathfinding tests: {@code across} by {@code deep} cells (east, south), each a block of
 * floor, walled by stone too high to jump, built on a stone floor in the world from {@code corner}
 * (its north-west corner, at its floor's height plus one: where a golem's feet stand).
 */
final class Maze {
	/** How high its walls are, in blocks: more than a jump. */
	static final int WALL_HEIGHT = 2;
	/** How many blocks a cell takes, with the wall between it and the next. */
	static final int PITCH = 2;

	final int across, deep;
	final BlockPos corner;
	/** Its blocks, x then z, true for a wall. */
	private final boolean[][] wall;

	/** A square maze. */
	Maze(int cells, long seed, BlockPos corner) {
		this(cells, cells, seed, corner);
	}

	Maze(int across, int deep, long seed, BlockPos corner) {
		this.across = across;
		this.deep = deep;
		this.corner = corner;
		wall = new boolean[sizeX()][sizeZ()];
		for (boolean[] column : wall) {
			java.util.Arrays.fill(column, true);
		}
		carve(new Random(seed));
	}

	/** Its sides, in blocks: east and south. */
	int sizeX() {
		return across * PITCH + 1;
	}

	int sizeZ() {
		return deep * PITCH + 1;
	}

	/** The block a golem stands in at a cell (its feet's). */
	BlockPos cell(int x, int z) {
		return corner.offset(x * PITCH + 1, 0, z * PITCH + 1);
	}

	/** Walls a cell in (its open sides closed): what's there can't be reached. */
	void seal(int x, int z) {
		int bx = x * PITCH + 1;
		int bz = z * PITCH + 1;
		wall[bx - 1][bz] = wall[bx + 1][bz] = wall[bx][bz - 1] = wall[bx][bz + 1] = true;
	}

	/** Opens every wall but its rim: an open floor, the maze's size (the pathfinder's best case). */
	void open() {
		for (int x = 1; x < sizeX() - 1; x++) {
			for (int z = 1; z < sizeZ() - 1; z++) {
				wall[x][z] = false;
			}
		}
	}

	/** Puts a wall at a block of it ({@code x}, {@code z} from its corner), or takes one away: before it's built. */
	void wall(int x, int z, boolean wall) {
		this.wall[x][z] = wall;
	}

	private void carve(Random random) {
		boolean[][] seen = new boolean[across][deep];
		ArrayDeque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[] {0, 0});
		seen[0][0] = true;
		wall[1][1] = false;
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!stack.isEmpty()) {
			int[] at = stack.peek();
			int[] order = {0, 1, 2, 3};
			for (int i = 3; i > 0; i--) {
				int j = random.nextInt(i + 1);
				int swap = order[i];
				order[i] = order[j];
				order[j] = swap;
			}
			boolean went = false;
			for (int i : order) {
				int nx = at[0] + ways[i][0];
				int nz = at[1] + ways[i][1];
				if (nx < 0 || nz < 0 || nx >= across || nz >= deep || seen[nx][nz]) {
					continue;
				}
				seen[nx][nz] = true;
				wall[at[0] * PITCH + 1 + ways[i][0]][at[1] * PITCH + 1 + ways[i][1]] = false;
				wall[nx * PITCH + 1][nz * PITCH + 1] = false;
				stack.push(new int[] {nx, nz});
				went = true;
				break;
			}
			if (!went) {
				stack.pop();
			}
		}
	}

	/** Its chunks kept loaded and ticking (the test runner lets them go at its batch's end). */
	void forceChunks(ServerLevel level) {
		ChunkPos from = ChunkPos.containing(corner);
		ChunkPos to = ChunkPos.containing(corner.offset(sizeX(), 0, sizeZ()));
		for (int x = from.x() - 1; x <= to.x() + 1; x++) {
			for (int z = from.z() - 1; z <= to.z() + 1; z++) {
				level.setChunkForced(x, z, true);
			}
		}
	}

	/** Builds it: its floor, its walls, air over its cells (up to its walls' height and one more). */
	void build(ServerLevel level) {
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = 0; x < sizeX(); x++) {
			for (int z = 0; z < sizeZ(); z++) {
				level.setBlock(at.setWithOffset(corner, x, -1, z), stone, flags);
				for (int y = 0; y <= WALL_HEIGHT; y++) {
					level.setBlock(at.setWithOffset(corner, x, y, z), wall[x][z] && y < WALL_HEIGHT ? stone : air, flags);
				}
			}
		}
	}

	/** Takes it down again (air where it was, its floor too). */
	void clear(ServerLevel level) {
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = 0; x < sizeX(); x++) {
			for (int z = 0; z < sizeZ(); z++) {
				for (int y = -1; y <= WALL_HEIGHT; y++) {
					level.setBlock(at.setWithOffset(corner, x, y, z), air, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** The cells of its one way from one cell to another, counted (a breadth-first walk): how long the way is, in blocks. */
	int wayLength(int fromX, int fromZ, int toX, int toZ) {
		int[][] far = new int[sizeX()][sizeZ()];
		for (int[] column : far) {
			java.util.Arrays.fill(column, -1);
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		int sx = fromX * PITCH + 1, sz = fromZ * PITCH + 1;
		far[sx][sz] = 0;
		queue.add(new int[] {sx, sz});
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] way : ways) {
				int nx = at[0] + way[0], nz = at[1] + way[1];
				if (nx >= 0 && nz >= 0 && nx < sizeX() && nz < sizeZ() && !wall[nx][nz] && far[nx][nz] < 0) {
					far[nx][nz] = far[at[0]][at[1]] + 1;
					queue.add(new int[] {nx, nz});
				}
			}
		}
		return far[toX * PITCH + 1][toZ * PITCH + 1];
	}
}
