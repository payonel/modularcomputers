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
 * The golem's pathfinding (the user's, 2026-11-14: Bug2's): a walk with pathfinding goes along its
 * line as far as it's told, round what's on it and back onto it (GolemWayfinder), and ends blocked
 * where no way back onto it is within its range; a stop and a load mid-way keep its rules. And what
 * a search costs the server thread, logged, in mazes and open floors (generated, seeded): the game's
 * pathfinder by its range and a maze's size, reachable or not, its ways compared searched at once, a
 * step at a time, a step to the next chunks at a time; and the wayfinder's one search.
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
	 * The walks' corridor: a long one, 52 cells east (straight across, 102 blocks: past the
	 * pathfinding range of 96) and 2 south (5 blocks), open, its line along its north row.
	 */
	private static final int LONG_ACROSS = 52;
	private static final int LONG_DEEP = 2;
	/** How far east its last cell is from its first, in blocks. */
	private static final long ACROSS = (LONG_ACROSS - 1) * Maze.PITCH;
	/** Where pillars stand on the long corridor's line, in blocks east of its corner: each gone round. */
	private static final int[] PILLARS = {30, 70};
	/** The load test's corridor, 12 cells east (and LONG_DEEP south); its walk, to its last cell; a pillar on its line past where the load finds it. */
	private static final int LOAD_ACROSS = 12;
	private static final long LOAD_WALK = (LOAD_ACROSS - 1) * Maze.PITCH;
	private static final int LOAD_PILLAR = 17;

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
	 * A search by the wayfinder (the mod's own, all at once in a tick: its way back onto a walk's line,
	 * the line from {@code start} to {@code target}, farther along it than {@code share} of it), timed
	 * over repeats: the median and the worst, the memory, what it explored against its cap.
	 */
	private static String wayfinder(ServerLevel level, BlockPos start, BlockPos target, double share) {
		BotGolem probe = probe(level, start);
		net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3.atBottomCenterOf(start), to = net.minecraft.world.phys.Vec3.atBottomCenterOf(target);
		double past = share * from.subtract(to).horizontalDistance();
		long[] nanos = new long[REPEATS];
		long bytes = 0;
		com.payonel.modularcomputers.entity.GolemWayfinder.Way way = null;
		for (int i = 0; i < REPEATS; i++) {
			long allocated = THREADS.getCurrentThreadAllocatedBytes();
			long began = System.nanoTime();
			way = com.payonel.modularcomputers.entity.GolemWayfinder.search(probe, from, from, to, past);
			nanos[i] = System.nanoTime() - began;
			bytes = THREADS.getCurrentThreadAllocatedBytes() - allocated;
		}
		probe.discard();
		java.util.Arrays.sort(nanos);
		int cap = com.payonel.modularcomputers.entity.GolemWayfinder.NODES_PER_BLOCK * com.payonel.modularcomputers.entity.GolemWayfinder.range();
		return String.format("wayfinder, back on its line past %3.0f%% of it: %s, explored %5d of %d, way %3d blocks, %7.3f ms median, %7.3f ms worst, %6d KB",
			share * 100, way.points() != null ? "found" : "none ", way.explored(), cap, way.points() == null ? 0 : way.points().size() - 1,
			nanos[REPEATS / 2] / 1e6, nanos[REPEATS - 1] / 1e6, bytes / 1024);
	}

	/** How far along its line the stress test's searches must come back onto it: at once (a wall's detour), halfway, and only at its end (its target: the worst, when it's sealed in). */
	private static final double[] PASTS = {0, 0.5, 1};

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
			for (double share : PASTS) {
				steps.add(() -> log.add("  " + wayfinder(level, maze[0].cell(0, 0), maze[0].cell(cells - 1, cells - 1), share)));
			}
			for (int chunks : new int[] {1, 2}) { // (last: a region cut off at chunks leaves the world's cache of places wrong at its edges)
				steps.add(() -> log.add("  progressive " + progressive(level, maze[0].cell(0, 0), maze[0].cell(cells - 1, cells - 1), 16 * chunks + 16, chunks)));
			}
			steps.add(() -> {
				maze[0].clear(level); // (the world's cache of places afresh)
				maze[0].seal(cells - 1, cells - 1);
				maze[0].build(level);
				log.add("  sealed (unreachable):");
			});
			for (double share : PASTS) {
				steps.add(() -> log.add("  " + wayfinder(level, maze[0].cell(0, 0), maze[0].cell(cells - 1, cells - 1), share)));
			}
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
			for (double share : PASTS) {
				steps.add(() -> log.add("  " + wayfinder(level, maze[0].cell(0, 0), maze[0].cell(cells - 1, cells - 1), share)));
			}
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

	/** An open corridor over the test, {@code up} blocks over its space, its chunks kept loaded, pillars on its first row (blocks east of its corner). */
	private static Maze corridor(GameTestHelper helper, int across, int deep, int up, int... pillars) {
		Maze corridor = new Maze(across, deep, SEED, helper.absolutePos(new BlockPos(0, up, 0)));
		corridor.open();
		for (int x : pillars) {
			corridor.wall(x, 1, true);
		}
		corridor.forceChunks(helper.getLevel());
		corridor.build(helper.getLevel());
		return corridor;
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
	 * A walk past the pathfinding range (102 blocks east) goes along its line however far (only a
	 * search has a range), round the pillars on it, and ends exactly on its target's middle, facing
	 * as it was.
	 */
	@GameTest(maxTicks = 6000)
	public void aWalkPastTheRangeGoesAlongItsLineRoundWhatsOnIt(GameTestHelper helper) {
		Maze maze = corridor(helper, LONG_ACROSS, LONG_DEEP, REACH_UP, PILLARS);
		BotGolem golem = golemIn(helper, maze);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> helper.assertTrue(!golem.robotics().walk(args(ACROSS)).failed(), "it starts"))
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), WALKED, "round the pillars");
				same(helper, position(golem), at(ACROSS, 0, 0), "exactly there");
				same(helper, real(golem), at(ACROSS, 0, 0), "really there");
				same(helper, golem.getYRot(), BotGolemGameTest.EAST, "facing as it was");
			})
			.thenSucceed();
	}

	/**
	 * A walk to where no way goes (a corridor's end walls it in, its target 200 blocks on) walks its
	 * line to the wall, finds no way back onto it past the wall, and ends blocked there, on the last
	 * middle it reached, no one block to name, its face toward its target.
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
		Maze maze = corridor(helper, LONG_ACROSS, LONG_DEEP, STOP_UP);
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
	 * last command left it (its line the same), on from where it is, round the pillar past where the
	 * load found it, and it ends where it would have. (Its robotics as a load restores it: what was
	 * saved, then its program running again.)
	 */
	@GameTest(maxTicks = 6000)
	public void savedMidWayAWalkGoesOnAfterALoadAndEndsWhereItWouldHave(GameTestHelper helper) {
		Maze maze = corridor(helper, LOAD_ACROSS, LONG_DEEP, LOAD_UP, LOAD_PILLAR);
		BotGolem golem = golemIn(helper, maze);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				golem.robotics().called("walk", List.of(LOAD_WALK)); // (as the board records a command's call: what a save keeps)
				golem.robotics().walk(args(LOAD_WALK));
			})
			.thenExecuteAfter(60, () -> {
				helper.assertTrue(events.isEmpty(), "still walking");
				helper.assertTrue(golem.getX() < maze.corner.getX() + LOAD_PILLAR - 1, "short of the pillar: " + golem.getX());
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
	private static final int WALL_REACH = com.payonel.modularcomputers.entity.GolemWayfinder.range() + 8;

	/**
	 * A search goes only the pathfinding range (the user's, 2026-11-14: 96 blocks walked): its target
	 * just over a wall whose ends are farther than that, it finds no way back onto its line past the
	 * wall, and is blocked where it got, on the last middle it reached, no one block to name, its face
	 * toward its target.
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

	// ---- its line (the user's, 2026-11-14: Bug2's) ----------------------------------------------

	/**
	 * How far over the test's space the line tests build, in blocks: over every maze and wall here, and
	 * apart (each a field's floor and walls, and the air over them).
	 */
	private static final int ROUND_UP = 76;
	private static final int PLACED_UP = 80;
	private static final int NO_WAY_UP = 84;
	private static final int ONE_TICK_UP = 88;
	/** The line tests' field, in cells (21 blocks east, 13 south), the golem's row its middle one, and its walk east along it. */
	private static final int FIELD_ACROSS = 10;
	private static final int FIELD_DEEP = 6;
	private static final int FIELD_ROW = FIELD_DEEP * Maze.PITCH / 2;
	private static final long FIELD_WALK = 16;
	/** Where the wall across its line stands, in blocks east of the field's corner (the golem starts 1 east), and how far it reaches either side of the line. */
	private static final int WALL_AT = 8;
	private static final int WALL_SIDE = 2;
	/** How far off its line it may be seen walking it, in blocks: a hair. */
	private static final double ON_LINE = 0.01;
	/** How far off its line the line's rule says it's on it, in blocks (the user's: a middle within a block of it). */
	private static final double NEAR_LINE = 1;

	/** An open field (stone, walled in all round) over the test, {@code up} blocks over its space, a wall across its golem's line or not. */
	private static Maze field(GameTestHelper helper, int up, boolean wall, int side) {
		Maze field = new Maze(FIELD_ACROSS, FIELD_DEEP, SEED, helper.absolutePos(new BlockPos(0, up, 0)));
		field.open();
		if (wall) {
			for (int z = FIELD_ROW - side; z <= FIELD_ROW + side; z++) {
				field.wall(WALL_AT, z, true);
			}
		}
		field.forceChunks(helper.getLevel());
		field.build(helper.getLevel());
		return field;
	}

	/** A golem with pathfinding on, in a field's row, a block in from its west wall, facing east. */
	private static BotGolem golemAt(GameTestHelper helper, BlockPos at) {
		BotGolem golem = GolemFlightGameTest.golem(helper, false);
		golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, BotGolemGameTest.EAST, 0);
		golem.setYBodyRot(BotGolemGameTest.EAST);
		golem.setYHeadRot(BotGolemGameTest.EAST);
		golem.robotics().setPathfinding(args(true));
		return golem;
	}

	/** Where it is each tick, from where it started (east, south). */
	private static List<net.minecraft.world.phys.Vec3> track(GameTestHelper helper, BotGolem golem, BlockPos from) {
		List<net.minecraft.world.phys.Vec3> track = new ArrayList<>();
		net.minecraft.world.phys.Vec3 start = net.minecraft.world.phys.Vec3.atBottomCenterOf(from);
		helper.onEachTick(() -> track.add(golem.position().subtract(start)));
		return track;
	}

	/**
	 * A wall across its line, within its range (Bug2's way, the user's): it walks the line till the
	 * wall, goes round it, comes back onto the line past it (a block from it at most), and goes on to
	 * its target, ending exactly on its middle, facing as it was. (Its old way, the shortest, left the
	 * line from the start: seen failing.)
	 */
	@GameTest(maxTicks = 600)
	public void aWallAcrossItsLineIsGoneRoundAndItComesBackOntoTheLine(GameTestHelper helper) {
		Maze field = field(helper, ROUND_UP, true, WALL_SIDE);
		BlockPos from = field.corner.offset(1, 0, FIELD_ROW);
		BotGolem golem = golemAt(helper, from);
		List<Object> events = events(helper, golem);
		List<net.minecraft.world.phys.Vec3> track = track(helper, golem, from);
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				track.clear();
				golem.robotics().walk(args(FIELD_WALK));
			})
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), WALKED, "round the wall");
				same(helper, position(golem), at(FIELD_WALK, 0, 0), "exactly there");
				same(helper, golem.getYRot(), BotGolemGameTest.EAST, "facing as it was");
				var left = track.stream().filter(at -> Math.abs(at.z) > ON_LINE).findFirst().orElse(null);
				helper.assertTrue(left != null && left.x >= WALL_AT - 3, "on its line till the wall: it left it at " + left);
				helper.assertTrue(track.stream().anyMatch(at -> Math.abs(at.z) > WALL_SIDE), "round the wall");
				helper.assertTrue(track.stream().filter(at -> at.x > WALL_AT + 3).allMatch(at -> Math.abs(at.z) <= NEAR_LINE + ON_LINE),
					"back on its line past it: " + track.stream().filter(at -> at.x > WALL_AT + 3).mapToDouble(at -> Math.abs(at.z)).max());
			})
			.thenSucceed();
	}

	/**
	 * A wall put across its line as it walks it (something it didn't know of): it's stopped by it,
	 * searches once from there, and goes round, ending exactly there.
	 */
	@GameTest(maxTicks = 600)
	public void aWallPutAcrossItsLineAsItWalksIsGoneRound(GameTestHelper helper) {
		Maze field = field(helper, PLACED_UP, false, 0);
		BlockPos from = field.corner.offset(1, 0, FIELD_ROW);
		BotGolem golem = golemAt(helper, from);
		List<Object> events = events(helper, golem);
		List<net.minecraft.world.phys.Vec3> track = track(helper, golem, from);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(FIELD_WALK)))
			.thenWaitUntil(() -> helper.assertTrue(golem.getX() - from.getX() > 3, "on its way"))
			.thenExecute(() -> wallAcross(helper, field, WALL_SIDE))
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), WALKED, "round the wall");
				same(helper, position(golem), at(FIELD_WALK, 0, 0), "exactly there");
				helper.assertTrue(track.stream().anyMatch(at -> Math.abs(at.z) > WALL_SIDE), "round the wall");
			})
			.thenSucceed();
	}

	/**
	 * A wall put right across the field as it walks (no way round): it's stopped by it, searches, finds
	 * no way back onto its line past it, and is blocked, no one block to name, on the last middle it
	 * reached, its face toward its target.
	 */
	@GameTest(maxTicks = 600)
	public void aWallPutAcrossItsLineWithNoWayRoundBlocksIt(GameTestHelper helper) {
		Maze field = field(helper, NO_WAY_UP, false, 0);
		BlockPos from = field.corner.offset(1, 0, FIELD_ROW);
		BotGolem golem = golemAt(helper, from);
		List<Object> events = events(helper, golem);
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(FIELD_WALK)))
			.thenWaitUntil(() -> helper.assertTrue(golem.getX() - from.getX() > 3, "on its way"))
			.thenExecute(() -> wallAcross(helper, field, FIELD_ROW))
			.thenWaitUntil(() -> helper.assertTrue(!events.isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), java.util.Map.of("event", "walk", "ok", false, "why", "blocked", "name", "", "face", "front"), "no way round");
				same(helper, position(golem), at(WALL_AT - 2, 0, 0), "against the wall, on its middle");
			})
			.thenSucceed();
	}

	/** How far over the test's space the notice test builds, in blocks: over the other line tests. */
	private static final int ON_ITS_WAY_UP = 92;

	/**
	 * A block put on the way its search found, as it goes round a wall (something that way didn't
	 * expect): it says so once, a notice (the user's, 2026-11-14: blocked, by that block, met on one of
	 * its faces, where it stood then, as position gives one), searches again from there, and goes round
	 * the wall's other side, ending exactly there. (The wall on its line, met first, is said by nothing:
	 * its line was all it had.)
	 */
	@GameTest(maxTicks = 600)
	public void aBlockPutOnItsWaySaysSoOnceAndItSearchesAgain(GameTestHelper helper) {
		Maze field = field(helper, ON_ITS_WAY_UP, true, WALL_SIDE);
		BlockPos from = field.corner.offset(1, 0, FIELD_ROW);
		BotGolem golem = golemAt(helper, from);
		List<Object> events = new ArrayList<>();
		List<java.util.Map<String, Object>> stoodAt = new ArrayList<>();
		helper.onEachTick(() -> {
			Object event;
			while ((event = golem.robotics().pull(args()).first()) != null) {
				events.add(event);
				stoodAt.add(real(golem)); // (where it is as it says it: it searches again in the tick it met it, and goes on at its next)
			}
		});
		int[] side = {0};
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(FIELD_WALK)))
			.thenWaitUntil(() -> helper.assertTrue(Math.abs(golem.getZ() - (from.getZ() + 0.5)) > 1.5, "going round the wall"))
			.thenExecute(() -> {
				side[0] = golem.getZ() < from.getZ() + 0.5 ? -1 : 1;
				for (int z = FIELD_ROW + side[0] * (WALL_SIDE + 1); z > 0 && z < field.sizeZ() - 1; z += side[0]) {
					for (int y = 0; y < Maze.WALL_HEIGHT; y++) { // (the wall on to the rim, its side: the way it found)
						helper.getLevel().setBlock(field.corner.offset(WALL_AT, y, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
							net.minecraft.world.level.block.Block.UPDATE_ALL);
					}
				}
			})
			.thenWaitUntil(() -> helper.assertTrue(!walks(events).isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				List<Integer> notices = new ArrayList<>();
				for (int i = 0; i < events.size(); i++) {
					if (events.get(i) instanceof java.util.Map<?, ?> m && "notice".equals(m.get("event")) && "blocked".equals(m.get("why"))) {
						notices.add(i);
					}
				}
				same(helper, notices.size(), 1, "one notice: " + events);
				var notice = (java.util.Map<?, ?>) events.get(notices.getFirst());
				same(helper, notice.get("name"), "minecraft:stone", "by the block put on its way");
				helper.assertTrue(java.util.Set.of("front", "back", "left", "right").contains(notice.get("face")), "met on a face of its: " + notice);
				same(helper, notice.get("position"), stoodAt.get(notices.getFirst()), "where it stood");
				same(helper, last(events), WALKED, "round the wall's other side");
				same(helper, position(golem), at(FIELD_WALK, 0, 0), "exactly there");
			})
			.thenSucceed();
	}

	/** A wall put across a field's row at {@link #WALL_AT}, {@code side} blocks either side of it, too high to jump. */
	private static void wallAcross(GameTestHelper helper, Maze field, int side) {
		for (int z = FIELD_ROW - side; z <= FIELD_ROW + side; z++) {
			for (int y = 0; y < Maze.WALL_HEIGHT; y++) {
				helper.getLevel().setBlock(field.corner.offset(WALL_AT, y, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
					net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
		}
	}

	/** The one-tick test's field, in cells a side (97 blocks: thousands of places on its side of the wall, more than a search's cap). */
	private static final int WIDE_CELLS = 48;

	/**
	 * A search fits in a tick (the user's: it moves or is blocked within a server tick): against a
	 * wall right across a wide field, with nothing past it to come back to its line on, its walk is
	 * blocked by the tick after the one it was given in.
	 */
	@GameTest(maxTicks = 600)
	public void aSearchWithNoWayEndsTheWalkByTheNextTick(GameTestHelper helper) {
		Maze field = new Maze(WIDE_CELLS, SEED, helper.absolutePos(new BlockPos(0, ONE_TICK_UP, 0)));
		field.open();
		int middle = WIDE_CELLS * Maze.PITCH / 2;
		for (int z = 1; z < field.sizeZ() - 1; z++) {
			field.wall(middle + 1, z, true); // (right in front of it)
		}
		field.forceChunks(helper.getLevel());
		field.build(helper.getLevel());
		BotGolem golem = golemAt(helper, field.corner.offset(middle, 0, middle));
		List<Object> events = events(helper, golem);
		long[] given = {-1};
		long[] ended = {-1};
		helper.onEachTick(() -> {
			if (ended[0] < 0 && !walks(events).isEmpty()) {
				ended[0] = helper.getTick();
			}
		});
		helper.startSequence()
			.thenExecuteAfter(3, () -> {
				given[0] = helper.getTick();
				golem.robotics().walk(args(10L));
			})
			.thenWaitUntil(() -> helper.assertTrue(ended[0] >= 0, "its walk ended"))
			.thenExecute(() -> {
				same(helper, GameTests.withoutCall(walks(events).getFirst()), java.util.Map.of("event", "walk", "ok", false, "why", "blocked", "name", "", "face", "front"), "no way");
				helper.assertTrue(ended[0] - given[0] <= ONE_TICK, "ended " + (ended[0] - given[0]) + " ticks after it was given");
			})
			.thenSucceed();
	}

	/** How many ticks after a walk is given its end may be seen in: the next (its tick), and the test's look after it. */
	private static final int ONE_TICK = 2;

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
		var from = net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(edge, surface, z));
		com.payonel.modularcomputers.entity.GolemWayfinder.search(probe, from, from, from.add(8, 0, 0), 0);
		probe.discard();
		BlockPos ground = new BlockPos(edge + 1, surface - 1, z); // (in the chunk east, under where the search looked first)
		level.getChunk(chunkX + 1, chunkZ);
		helper.assertTrue(!level.getBlockState(ground).isAir(), "ground there once it's loaded");
		var type = level.getPathTypeCache().getOrCompute(level, ground);
		helper.assertTrue(type != net.minecraft.world.level.pathfinder.PathType.OPEN, "the server's cache says what's there: " + type);
		helper.succeed();
	}

	/** How far over the test's space the thing-on-its-way test builds, in blocks: over the notice test's. */
	private static final int THING_UP = 96;
	/** The one gap in that test's wall, in blocks south of its line: its way goes through it. */
	private static final int GAP_SOUTH = 3;

	/**
	 * A thing it can't push put on the way its search found (an armor stand, in the one gap of a wall
	 * across the field) stops it as a block would (the user's, 2026-11-15): it says so, a notice, by
	 * the thing's type, met on one of its faces, where it stood then, and searches again from there
	 * (its search sees no things: the same way), stopped by it again while it stays; once it's gone,
	 * the walk goes on through the gap and ends exactly there. (No notice, the thing walked through:
	 * seen failing.)
	 */
	@GameTest(maxTicks = 1200)
	public void aThingItCantPushOnItsWaySaysSoAndItSearchesAgain(GameTestHelper helper) {
		Maze field = new Maze(FIELD_ACROSS, FIELD_DEEP, SEED, helper.absolutePos(new BlockPos(0, THING_UP, 0)));
		field.open();
		for (int z = 1; z < field.sizeZ() - 1; z++) {
			field.wall(WALL_AT, z, z != FIELD_ROW + GAP_SOUTH); // (right across, but for its gap)
		}
		field.forceChunks(helper.getLevel());
		field.build(helper.getLevel());
		BlockPos from = field.corner.offset(1, 0, FIELD_ROW);
		BotGolem golem = golemAt(helper, from);
		List<Object> events = new ArrayList<>();
		List<java.util.Map<String, Object>> stoodAt = new ArrayList<>();
		helper.onEachTick(() -> {
			Object event;
			while ((event = golem.robotics().pull(args()).first()) != null) {
				events.add(event);
				stoodAt.add(real(golem));
			}
		});
		net.minecraft.world.entity.decoration.ArmorStand[] stand = new net.minecraft.world.entity.decoration.ArmorStand[1];
		helper.startSequence()
			.thenExecuteAfter(3, () -> golem.robotics().walk(args(FIELD_WALK)))
			.thenWaitUntil(() -> helper.assertTrue(golem.getZ() - (from.getZ() + 0.5) > 1, "on its way to the gap"))
			.thenExecute(() -> {
				stand[0] = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				BlockPos gap = field.corner.offset(WALL_AT, 0, FIELD_ROW + GAP_SOUTH);
				stand[0].snapTo(gap.getX() + 0.5, gap.getY(), gap.getZ() + 0.5, 0, 0);
				helper.getLevel().addFreshEntity(stand[0]);
			})
			.thenWaitUntil(() -> helper.assertTrue(thingNotices(events).size() >= 2, "stopped by it, searched again, and stopped again: " + events))
			.thenExecute(() -> {
				helper.assertTrue(walks(events).isEmpty(), "still walking: " + events);
				int first = thingNotices(events).getFirst();
				var notice = (java.util.Map<?, ?>) events.get(first);
				same(helper, notice.get("name"), "minecraft:armor_stand", "by the thing's type");
				helper.assertTrue(java.util.Set.of("front", "back", "left", "right").contains(notice.get("face")), "met on a face of its: " + notice);
				same(helper, notice.get("position"), stoodAt.get(first), "where it stood");
				stand[0].discard();
			})
			.thenWaitUntil(() -> helper.assertTrue(!walks(events).isEmpty(), "its walk ended"))
			.thenExecute(() -> {
				same(helper, last(events), WALKED, "through the gap once it's gone");
				same(helper, position(golem), at(FIELD_WALK, 0, 0), "exactly there");
			})
			.thenSucceed();
	}

	/** Where among {@code events} the blocked notices are, by a thing (an armor stand). */
	private static List<Integer> thingNotices(List<Object> events) {
		List<Integer> notices = new ArrayList<>();
		for (int i = 0; i < events.size(); i++) {
			if (events.get(i) instanceof java.util.Map<?, ?> m && "notice".equals(m.get("event")) && "blocked".equals(m.get("why"))
				&& "minecraft:armor_stand".equals(m.get("name"))) {
				notices.add(i);
			}
		}
		return notices;
	}
}
