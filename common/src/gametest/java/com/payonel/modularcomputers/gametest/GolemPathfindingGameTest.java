package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.ModEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * The golem's pathfinding in mazes (generated, seeded): a walk with pathfinding goes as far as it's
 * told, its way found by its wayfinder in legs (GolemWayfinder), round a maze longer than the game's
 * pathfinder's range, and ends blocked where no way comes nearer; a stop and a load mid-way keep
 * its rules. And what a search costs the server thread, logged: the game's pathfinder by its range
 * and a maze's size, reachable or not, and its ways compared searched at once, a step at a time,
 * a step to the next chunks at a time, and by the wayfinder.
 */
public class GolemPathfindingGameTest {
	/**
	 * How far over the test's own space each test builds its maze, in blocks: clear of every other
	 * test (their sensors' reach too), and of each other (tests run side by side, a maze reaching
	 * over its neighbours' spaces).
	 */
	private static final int STRESS_UP = 48;
	private static final int REACH_UP = 56;
	private static final int PEN_UP = 60;
	private static final int STOP_UP = 64;
	private static final int LOAD_UP = 68;
	/** The stress test's mazes, in cells a side (33 and 129 blocks; the report's table took 8 to 96 cells), and their seed. */
	private static final int[] STRESS_CELLS = {16, 64};
	private static final long SEED = 20261113L;
	/** The ranges searched, in blocks (the game's: a range's search visits at most 16 nodes a block); the report's took 16 to 256. */
	private static final float[] RANGES = {16, 32, 96, 256};
	/** Each search's repetitions: the median and the worst are logged. */
	private static final int REPEATS = 3;
	/**
	 * The walks' maze: a long one, 52 cells east (straight across, 102 blocks: past the game's
	 * pathfinder's range of 96) and 2 south, its way round longer still.
	 */
	private static final int LONG_ACROSS = 52;
	private static final int LONG_DEEP = 2;
	/** How far east its last cell is from its first, in blocks. */
	private static final long ACROSS = (LONG_ACROSS - 1) * Maze.PITCH;
	/** The load test's maze, 12 cells east (and LONG_DEEP south): its way within a leg's range; and its walk, to its last cell. */
	private static final int LOAD_ACROSS = 12;
	private static final long LOAD_WALK = (LOAD_ACROSS - 1) * Maze.PITCH;

	/** What a search took: server time, memory allocated, whether it reached, the way's nodes, nodes it explored. */
	record Search(long nanos, long bytes, Path path, int explored) {
		boolean reached() {
			return path != null && path.canReach();
		}
	}

	private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();

	/**
	 * A search by the game's pathfinder for {@code probe} (a golem standing where it searches from) to
	 * {@code target}, {@code range} far at most, in a region round it (or in {@code chunks} chunks
	 * round its own, every other chunk unseen: void).
	 */
	static Search search(ServerLevel level, BotGolem probe, BlockPos target, float range, int chunks, boolean debug) {
		BlockPos from = probe.blockPosition();
		int radius = (int) range + 8;
		BlockPos low = from.offset(-radius, -radius, -radius);
		BlockPos high = from.offset(radius, radius, radius);
		if (chunks >= 0) {
			int cx = from.getX() >> 4, cz = from.getZ() >> 4;
			low = new BlockPos((cx - chunks) << 4, low.getY(), (cz - chunks) << 4);
			high = new BlockPos(((cx + chunks) << 4) + 15, high.getY(), ((cz + chunks) << 4) + 15);
		}
		long bytes = THREADS.getCurrentThreadAllocatedBytes();
		long start = System.nanoTime();
		PathFinder finder = new PathFinder(new WalkNodeEvaluator(), (int) (range * 16));
		if (debug) {
			finder.setCaptureDebug(() -> true);
		}
		PathNavigationRegion region = new PathNavigationRegion(level, low, high);
		Path path = finder.findPath(region, probe, Set.of(target), range, 0, 1);
		long nanos = System.nanoTime() - start;
		bytes = THREADS.getCurrentThreadAllocatedBytes() - bytes;
		int explored = debug && path != null && path.debugData() != null ? path.debugData().closedSet().size() : -1;
		return new Search(nanos, bytes, path, explored);
	}

	static BotGolem probe(ServerLevel level, BlockPos at) {
		BotGolem probe = ModEntities.TIER1_BOT_GOLEM.create(level, EntitySpawnReason.COMMAND);
		probe.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		return probe;
	}

	/** Repeated searches: the median and worst time, the median memory. */
	private static String measure(ServerLevel level, BotGolem probe, BlockPos target, float range, int chunks) {
		Search shape = search(level, probe, target, range, chunks, true);
		long[] nanos = new long[REPEATS];
		long[] bytes = new long[REPEATS];
		for (int i = 0; i < REPEATS; i++) {
			Search s = search(level, probe, target, range, chunks, false);
			nanos[i] = s.nanos();
			bytes[i] = s.bytes();
		}
		java.util.Arrays.sort(nanos);
		java.util.Arrays.sort(bytes);
		return String.format("range %3.0f: %7.3f ms median, %7.3f ms worst, %6d KB, explored %5d, way %3d nodes, %s",
			range, nanos[REPEATS / 2] / 1e6, nanos[REPEATS - 1] / 1e6, bytes[REPEATS / 2] / 1024, shape.explored(),
			shape.path() == null ? 0 : shape.path().getNodeCount(), shape.reached() ? "reached" : "not reached");
	}

	/**
	 * A walk a step at a time, as a robot would make it (searched from each step's end, the probe
	 * moved there): each search's way's end is the next step, while it comes nearer the target than
	 * any before it by a block; else it gives up.
	 */
	private static String progressive(ServerLevel level, BlockPos start, BlockPos target, float range, int chunks) {
		BotGolem probe = probe(level, start);
		double best = Math.sqrt(start.distSqr(target));
		long total = 0, worst = 0;
		int searches = 0, walked = 0;
		boolean reached = false;
		while (searches < 1000) {
			Search s = search(level, probe, target, range, chunks, false);
			searches++;
			total += s.nanos();
			worst = Math.max(worst, s.nanos());
			if (s.path() == null) {
				break;
			}
			walked += s.path().getNodeCount() - 1;
			BlockPos end = s.path().getEndNode().asBlockPos();
			if (s.reached()) {
				reached = true;
				break;
			}
			double left = Math.sqrt(end.distSqr(target));
			if (left > best - 1) {
				break; // (no nearer: stuck)
			}
			best = left;
			probe.snapTo(end.getX() + 0.5, end.getY(), end.getZ() + 0.5, 0, 0);
		}
		probe.discard();
		return String.format("%s %3.0f: %s after %3d searches, %4d blocks walked, %8.3f ms in all, %7.3f ms worst%s",
			chunks >= 0 ? "chunks ±" + chunks + ", range" : "range", range, reached ? "reached" : "stuck  ", searches, walked, total / 1e6, worst / 1e6,
			reached ? "" : String.format(", %.0f blocks short", best));
	}

	/**
	 * A walk by the wayfinder (the mod's own search, a slice of server time a tick): legs of the
	 * pathfinding range each, the next from the last's end while it comes nearer.
	 */
	private static String wayfinder(ServerLevel level, BlockPos start, BlockPos target) {
		BotGolem probe = probe(level, start);
		double best = Math.sqrt(start.distSqr(target));
		long total = 0, worst = 0, bytes = 0;
		int legs = 0, slices = 0, walked = 0, explored = 0;
		boolean reached = false;
		while (legs < 1000) {
			legs++;
			long allocated = THREADS.getCurrentThreadAllocatedBytes();
			var finder = new com.payonel.modularcomputers.entity.GolemWayfinder(probe, target);
			while (true) {
				long began = System.nanoTime();
				boolean done = finder.search(com.payonel.modularcomputers.entity.GolemWayfinder.NANOS_PER_TICK);
				long took = System.nanoTime() - began;
				total += took;
				worst = Math.max(worst, took);
				slices++;
				if (done) {
					break;
				}
			}
			Path way = finder.way();
			bytes = Math.max(bytes, THREADS.getCurrentThreadAllocatedBytes() - allocated);
			explored += finder.explored();
			if (way == null) {
				break;
			}
			walked += way.getNodeCount() - 1;
			if (finder.reached()) {
				reached = true;
				break;
			}
			BlockPos end = way.getEndNode().asBlockPos();
			double left = Math.sqrt(end.distSqr(target));
			if (left > best - 1) {
				break;
			}
			best = left;
			probe.snapTo(end.getX() + 0.5, end.getY(), end.getZ() + 0.5, 0, 0);
		}
		probe.discard();
		return String.format("wayfinder: %s after %2d legs, %5d explored, %4d blocks walked, %8.3f ms in all over %3d slices, %6.3f ms worst slice, %6d KB a leg%s",
			reached ? "reached" : "stuck  ", legs, explored, walked, total / 1e6, slices, worst / 1e6, bytes / 1024,
			reached ? "" : String.format(", %.0f blocks short", best));
	}

	/** The pathfinder's cost in mazes, logged (no assertion on time: a loaded host's timing is its own). */
	@GameTest(maxTicks = 2000)
	public void aMazeStressesThePathfinder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos corner = helper.absolutePos(new BlockPos(0, STRESS_UP, 0));
		ArrayDeque<Runnable> steps = new ArrayDeque<>();
		List<String> log = new ArrayList<>();
		for (int cells : STRESS_CELLS) {
			Maze[] maze = new Maze[1];
			steps.add(() -> {
				maze[0] = new Maze(cells, SEED, corner);
				maze[0].forceChunks(level);
				maze[0].build(level);
			});
			steps.add(() -> {
				Maze m = maze[0];
				BlockPos start = m.cell(0, 0), far = m.cell(cells - 1, cells - 1);
				log.add(String.format("maze %d cells (%d blocks a side), corner to corner: way %d blocks, %.0f straight", cells, m.sizeX(),
					m.wayLength(0, 0, cells - 1, cells - 1), Math.sqrt(start.distSqr(far))));
			});
			for (float range : RANGES) {
				steps.add(() -> {
					BotGolem probe = probe(level, maze[0].cell(0, 0));
					log.add("  " + measure(level, probe, maze[0].cell(cells - 1, cells - 1), range, -1));
					probe.discard();
				});
			}
			for (float range : new float[] {16, 32, 96}) {
				steps.add(() -> log.add("  progressive " + progressive(level, maze[0].cell(0, 0), maze[0].cell(cells - 1, cells - 1), range, -1)));
			}
			steps.add(() -> log.add("  " + wayfinder(level, maze[0].cell(0, 0), maze[0].cell(cells - 1, cells - 1))));
			for (int chunks : new int[] {1, 2}) { // (last: a region cut off at chunks leaves the world's cache of places wrong at its edges)
				steps.add(() -> log.add("  progressive " + progressive(level, maze[0].cell(0, 0), maze[0].cell(cells - 1, cells - 1), 16 * chunks + 16, chunks)));
			}
			steps.add(() -> {
				maze[0].clear(level); // (the world's cache of places afresh)
				maze[0].seal(cells - 1, cells - 1);
				maze[0].build(level);
				log.add("  sealed (unreachable):");
			});
			steps.add(() -> log.add("  " + wayfinder(level, maze[0].cell(0, 0), maze[0].cell(cells - 1, cells - 1))));
			for (float range : RANGES) {
				steps.add(() -> {
					BotGolem probe = probe(level, maze[0].cell(0, 0));
					log.add("  " + measure(level, probe, maze[0].cell(cells - 1, cells - 1), range, -1));
					probe.discard();
				});
			}
			steps.add(() -> {
				maze[0].open();
				maze[0].clear(level); // (the world's cache of places afresh)
				maze[0].seal(cells - 1, cells - 1);
				maze[0].build(level);
				log.add("  open floor, target sealed in (unreachable):");
			});
			steps.add(() -> log.add("  " + wayfinder(level, maze[0].cell(0, 0), maze[0].cell(cells - 1, cells - 1))));
			for (float range : RANGES) {
				steps.add(() -> {
					BotGolem probe = probe(level, maze[0].cell(0, 0));
					log.add("  " + measure(level, probe, maze[0].cell(cells - 1, cells - 1), range, -1));
					probe.discard();
				});
			}
			steps.add(() -> maze[0].clear(level));
		}
		helper.onEachTick(() -> {
			if (!steps.isEmpty()) {
				steps.poll().run();
				if (steps.isEmpty()) {
					ModularComputers.LOGGER.info("Pathfinding in mazes:\n{}", String.join("\n", log));
				}
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(steps.isEmpty(), "measured"));
	}

	// ---- walks -----------------------------------------------------------------------------------

	private static com.payonel.modularcomputers.runtime.Arguments args(Object... values) {
		return new com.payonel.modularcomputers.runtime.Arguments(java.util.Arrays.asList(values));
	}

	private static void same(GameTestHelper helper, Object got, Object expected, String what) {
		helper.assertTrue(java.util.Objects.equals(got, expected), what + ": expected " + expected + ", got " + got);
	}

	/** A maze built over the test, {@code up} blocks over its space, its chunks kept loaded. */
	private static Maze maze(GameTestHelper helper, int across, int deep, int up) {
		Maze maze = new Maze(across, deep, SEED, helper.absolutePos(new BlockPos(0, up, 0)));
		maze.forceChunks(helper.getLevel());
		maze.build(helper.getLevel());
		return maze;
	}

	/** A golem with pathfinding on, on a maze's first cell, facing east (its forward), its program waiting. */
	private static BotGolem golemIn(GameTestHelper helper, Maze maze) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		BlockPos at = maze.cell(0, 0);
		golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, BotGolemGameTest.EAST, 0);
		golem.setYBodyRot(BotGolemGameTest.EAST);
		golem.setYHeadRot(BotGolemGameTest.EAST);
		golem.robotics().setPathfinding(args(true));
		return golem;
	}

	/** Each robotics event, as it comes. */
	private static List<Object> events(GameTestHelper helper, BotGolem golem) {
		List<Object> events = new ArrayList<>();
		helper.onEachTick(() -> {
			Object event;
			while ((event = golem.robotics().pull(args()).first()) != null) {
				events.add(event);
			}
		});
		return events;
	}

	private static Object last(List<Object> events) {
		return events.isEmpty() ? null : GameTests.withoutCall(events.getLast());
	}

	/** Where its last command left it, as a program reads it; and where it really is. */
	@SuppressWarnings("unchecked")
	private static java.util.Map<String, Object> position(BotGolem golem) {
		return (java.util.Map<String, Object>) golem.robotics().position(args()).first();
	}

	@SuppressWarnings("unchecked")
	private static java.util.Map<String, Object> real(BotGolem golem) {
		return (java.util.Map<String, Object>) ((java.util.Map<String, Object>) golem.robotics().get(args()).first()).get("position");
	}

	private static java.util.Map<String, Object> at(long forward, long right, long up) {
		return java.util.Map.of("forward", forward, "right", right, "up", up, "heading", 0L);
	}

	private static final java.util.Map<String, Object> WALKED = java.util.Map.of("event", "walk", "ok", true);

	/** The walk's ends among {@code events} (stopped mid-step, it may come down after: a fall's notices, nothing a walk did). */
	private static List<java.util.Map<?, ?>> walks(List<Object> events) {
		return events.stream().filter(e -> e instanceof java.util.Map<?, ?> m && "walk".equals(m.get("event"))).<java.util.Map<?, ?>>map(e -> (java.util.Map<?, ?>) e).toList();
	}

	/**
	 * A walk past the game's pathfinder's range (102 blocks east; its way round the maze longer) finds
	 * its way, and ends exactly on its target's middle, facing as it was. (The game's pathfinder alone
	 * found no way there: blocked, seen failing.)
	 */
	@GameTest(maxTicks = 6000)
	public void aWalkPastThePathfindersRangeFindsItsWayRoundAMaze(GameTestHelper helper) {
		Maze maze = maze(helper, LONG_ACROSS, LONG_DEEP, REACH_UP);
		ModularComputers.LOGGER.info("The long maze's way: {} blocks, {} straight", maze.wayLength(0, 0, LONG_ACROSS - 1, 0), ACROSS);
		BotGolem golem = golemIn(helper, maze);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> helper.assertTrue(!golem.robotics().walk(args(ACROSS)).failed(), "it starts"))
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), WALKED, "round the maze");
				same(helper, position(golem), at(ACROSS, 0, 0), "exactly there");
				same(helper, real(golem), at(ACROSS, 0, 0), "really there");
				same(helper, golem.getYRot(), BotGolemGameTest.EAST, "facing as it was");
			})
			.thenSucceed();
	}

	/**
	 * A walk to where no way goes (a corridor's end walls it in, its target 200 blocks on) walks as
	 * near as its way goes, and once no way comes nearer, ends blocked there, on the last middle it
	 * reached, no one block to name, its face toward its target.
	 */
	@GameTest(maxTicks = 300)
	public void aWalkWithNoWayNearerEndsBlockedOnTheLastMiddleItReached(GameTestHelper helper) {
		Maze pen = maze(helper, 3, 1, PEN_UP); // (a corridor 5 blocks long, walled in)
		BotGolem golem = golemIn(helper, pen);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(200L)))
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), java.util.Map.of("event", "walk", "ok", false, "why", "blocked", "name", "", "face", "front"), "no way on");
				same(helper, position(golem), at(4, 0, 0), "at the corridor's end, on its middle");
				same(helper, events.size(), 1, "one end");
			})
			.thenSucceed();
	}

	/**
	 * A stop mid-way stops it at once where it is (the user's), its way gone with it: it goes no
	 * further, and its next command goes on from there.
	 */
	@GameTest(maxTicks = 400)
	public void aStopMidWayStopsItWhereItIsAndItsWayWithIt(GameTestHelper helper) {
		Maze maze = maze(helper, LONG_ACROSS, LONG_DEEP, STOP_UP);
		BotGolem golem = golemIn(helper, maze);
		List<Object> events = events(helper, golem);
		net.minecraft.world.phys.Vec3[] stopped = new net.minecraft.world.phys.Vec3[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(ACROSS)))
			.thenExecuteAfter(60, () -> {
				helper.assertTrue(events.isEmpty(), "still walking");
				golem.robotics().stop(args());
				stopped[0] = golem.position();
				helper.assertTrue(stopped[0].distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(maze.cell(0, 0))) > 2, "it had gone some way: " + stopped[0]);
				var from = new java.util.HashMap<>(position(golem));
				var there = new java.util.HashMap<>(real(golem));
				from.remove("heading"); // (a stop squares its heading for its next command: the nearest right angle)
				there.remove("heading");
				same(helper, from, there, "its next command from where it is");
			})
			.thenWaitUntil(() -> helper.assertTrue(!walks(events).isEmpty(), "its walk ended"))
			.thenExecuteAfter(40, () -> {
				helper.assertTrue(walks(events).size() == 1 && "stopped".equals(walks(events).get(0).get("why")), "one end, stopped: " + events);
				helper.assertTrue(golem.position().subtract(stopped[0]).horizontalDistance() < 1e-3, "it went no further: " + golem.position() + ", stopped at " + stopped[0]);
			})
			.thenSucceed();
	}

	/**
	 * Saved mid-way, a walk goes on after the load (the user's): given again as it was, from where its
	 * last command left it, its way found afresh from where it is, and it ends where it would have.
	 * (Its robotics as a load restores it: what was saved, then its program running again.) Its maze's
	 * way is within one leg's range from anywhere in it, so where the load finds it doesn't decide the
	 * way (a leg from some places in a longer maze ends in a dead end, blocked: the range's rule).
	 */
	@GameTest(maxTicks = 6000)
	public void savedMidWayAWalkGoesOnAfterALoadAndEndsWhereItWouldHave(GameTestHelper helper) {
		Maze maze = maze(helper, LOAD_ACROSS, LONG_DEEP, LOAD_UP);
		helper.assertTrue(maze.wayLength(0, 0, LOAD_ACROSS - 1, 0) <= com.payonel.modularcomputers.entity.GolemWayfinder.range(), "its way within one leg");
		BotGolem golem = golemIn(helper, maze);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				golem.robotics().called("walk", List.of(LOAD_WALK)); // (as the board records a command's call: what a save keeps)
				golem.robotics().walk(args(LOAD_WALK));
			})
			.thenExecuteAfter(60, () -> {
				helper.assertTrue(events.isEmpty(), "still walking");
				var saved = golem.robotics().saved();
				golem.robotics().accepting(false); // (its program gone: its robotics stops, as at a save's load)
				golem.robotics().restore(saved);
				golem.robotics().accepting(true); // (its program running again: what was under way goes on)
			})
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), WALKED, "it went on, and arrived");
				same(helper, position(golem), at(LOAD_WALK, 0, 0), "where it would have ended");
				same(helper, golem.getYRot(), BotGolemGameTest.EAST, "facing as it was");
			})
			.thenSucceed();
	}

	/** How far over the test's space the long wall's test builds, in blocks: over every maze here. */
	private static final int WALL_UP = 72;
	/** How far the long wall reaches either side of the golem, in blocks: its ends farther than a leg's range. */
	private static final int WALL_REACH = com.payonel.modularcomputers.entity.GolemWayfinder.DEFAULT_RANGE + 8;

	/**
	 * A leg searches only the pathfinding range (the user's, 2026-11-14: 96 blocks walked): its target
	 * just over a wall whose ends are farther than that, it finds no way nearer, and is blocked where
	 * it got, on the last middle it reached, no one block to name, its face toward its target.
	 */
	@GameTest(maxTicks = 1200)
	public void aWayRoundFartherThanTheRangeIsBlocked(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(0, WALL_UP, 0));
		for (int cx = (origin.getX() >> 4) - 1; cx <= ((origin.getX() + 8) >> 4) + 1; cx++) {
			for (int cz = ((origin.getZ() - WALL_REACH - 2) >> 4) - 1; cz <= ((origin.getZ() + WALL_REACH + 2) >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		// Two lanes, x 1 and 2 and x 4 and 5, walled in all round (other tests' mazes lie under it: a way off
		// its floor's edge would reach them), the wall between them ending WALL_REACH either side: its way
		// round, out along one lane and back along the other, over twice the range.
		var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
		var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		int end = WALL_REACH + 2;
		for (int z = -end; z <= end; z++) {
			for (int x = 0; x <= 6; x++) {
				level.setBlock(origin.offset(x, -1, z), stone, net.minecraft.world.level.block.Block.UPDATE_CLIENTS); // (its floor)
				boolean wall = x == 0 || x == 6 || Math.abs(z) == end || x == 3 && Math.abs(z) <= WALL_REACH;
				for (int y = 0; y <= Maze.WALL_HEIGHT; y++) {
					level.setBlock(origin.offset(x, y, z), wall && y < Maze.WALL_HEIGHT ? stone : air, net.minecraft.world.level.block.Block.UPDATE_CLIENTS); // (walls too high to jump)
				}
			}
		}
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		golem.snapTo(origin.getX() + 1.5, origin.getY(), origin.getZ() + 0.5, BotGolemGameTest.EAST, 0);
		golem.setYBodyRot(BotGolemGameTest.EAST);
		golem.setYHeadRot(BotGolemGameTest.EAST);
		golem.robotics().setPathfinding(args(true));
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(4L))) // (to the far side of the wall, 4 east)
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), java.util.Map.of("event", "walk", "ok", false, "why", "blocked", "name", "", "face", "front"), "no way within its range");
				same(helper, position(golem), at(1, 0, 0), "against the wall, on its middle");
			})
			.thenSucceed();
	}

	/** How far from the test its unloaded chunk test goes, in blocks: away from every other test's chunks. */
	private static final int FAR_AWAY = 4096;

	/**
	 * A search reaching into a chunk not loaded sees nothing there, and leaves the server's cache of
	 * places (every mob's) alone: once the chunk loads, its ground is ground to everyone. (Cached as
	 * the nothing the search saw, it stayed nothing, a hole in the world for every mob's way: seen
	 * failing, and the stress test's ways cut off at chunks had done the same.)
	 */
	@GameTest
	public void aSearchIntoAChunkNotLoadedLeavesTheServersCacheOfPlacesAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(0, 0, FAR_AWAY);
		int chunkX = far.getX() >> 4, chunkZ = far.getZ() >> 4;
		level.getChunk(chunkX, chunkZ); // (loaded now, its neighbour east not yet)
		helper.assertTrue(level.getChunkSource().getChunkNow(chunkX + 1, chunkZ) == null, "the chunk east not loaded");
		int edge = (chunkX << 4) + 15, z = (chunkZ << 4) + 8;
		int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, edge, z);
		BotGolem probe = probe(level, new BlockPos(edge, surface, z));
		var finder = new com.payonel.modularcomputers.entity.GolemWayfinder(probe, new BlockPos(edge + 8, surface, z));
		finder.search(Long.MAX_VALUE);
		probe.discard();
		BlockPos ground = new BlockPos(edge + 1, surface - 1, z); // (in the chunk east, under where the search looked first)
		level.getChunk(chunkX + 1, chunkZ);
		helper.assertTrue(!level.getBlockState(ground).isAir(), "ground there once it's loaded");
		var type = level.getPathTypeCache().getOrCompute(level, ground);
		helper.assertTrue(type != net.minecraft.world.level.pathfinder.PathType.OPEN, "the server's cache says what's there: " + type);
		helper.succeed();
	}
}
