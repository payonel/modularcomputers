package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.device.CraftingCard;
import com.payonel.modularcomputers.device.RoboticsDevice;
import com.payonel.modularcomputers.machine.GolemLayout;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.SlowDevice;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What wakes a command waiting on a buffered device, in mcos (the code review's cases): another
 * device's news (a robot and its crafting card wait on each other), news another thread's waiting
 * command took, a stop written as a line, a close's last line, a write that never came back, and a
 * wait for an end with a time limit while other news keeps coming.
 */
class BufferedWaitsMcosTest extends McosHarness {
	private final RoboticsDevice robotics = new RoboticsDevice();
	/** What the robot's stand-in body was asked, in order. */
	private final List<String> asked = Collections.synchronizedList(new java.util.ArrayList<>());
	/** Ticks till the move under way ends. */
	private final int[] left = {0};
	/** How many ticks a walk takes (a block's worth, about). */
	private static final int WALK_TICKS = 6;

	BufferedWaitsMcosTest() {
		robotics.moveWith(new RoboticsDevice.Body() {
			@Override
			public String walk(double forward, double right, double up) {
				asked.add("walk " + (long) forward);
				left[0] = WALK_TICKS * (int) Math.max(1, Math.abs(forward));
				return null;
			}

			@Override
			public String turn(double degrees) {
				asked.add("turn " + (long) degrees);
				left[0] = 3;
				return null;
			}

			@Override
			public void stop() {
				left[0] = 0;
			}

			@Override
			public Result position() {
				return Result.ok(Map.of("forward", 0L, "right", 0L, "up", 0L, "heading", 0L));
			}
		});
		ticks.add(() -> {
			if (left[0] > 0 && --left[0] == 0) {
				robotics.arrived(); // (the move under way ends)
			}
		});
		devices.put(Motherboard.exp(GolemLayout.ROBOTICS_LANE), robotics);
	}

	/** A crafting card in the robot: its bench makes 4 sticks a craft, and stores all. */
	private CraftingCard card() {
		CraftingCard card = new CraftingCard();
		card.benchIn(new CraftingCard.Bench() {
			@Override
			public boolean robotics() {
				return true;
			}

			@Override
			public RoboticsDevice robot() {
				return robotics;
			}

			@Override
			public Result list() {
				return Result.ok(List.of());
			}

			@Override
			public Result craft() {
				return Result.ok(Map.of("name", "minecraft:stick", "count", 4L));
			}

			@Override
			public Result move(int from, int to, int count) {
				return Result.ok(0L);
			}

			@Override
			public Result equip(int slot) {
				return Result.ok();
			}

			@Override
			public Result pickup() {
				return Result.ok(0L);
			}

			@Override
			public Result drop(String face, int slot, int count) {
				return Result.ok(0L);
			}

			@Override
			public Result store(String face, int slot, int count, int to) {
				asked.add("store " + face);
				return Result.ok(true, 0L);
			}

			@Override
			public Result take(String face, int slot, int count, int to) {
				return Result.ok(0L);
			}
		});
		ticks.add(card::tick);
		devices.put(Motherboard.exp(0), card);
		return card;
	}

	/** The robot waits for its card's craft (the card's end, not the robot's, ends that wait). */
	@Test
	void aRobotsCommandWaitingForItsCardsCraftRunsOnceItsCrafted() {
		card();
		file("craftwalk.lua", """
			local devices = require("devices")
			local card = assert(devices.open("crafting0"))
			local robot = assert(devices.open("robotics0"))
			assert(card.craft(2))
			assert(robot.walk(1)) -- waits: its robot's busy crafting
			assert(devices.ended(robot, "walk"))
			local ok, why, made = devices.ended(card, "craft")
			local out = io.open("/out", "w")
			out:write(tostring(ok), " ", made.count, "\\n")
			out:close()
			""");
		boot();
		type("lua /craftwalk.lua\r");
		untilFile(root().resolve("out"), "true 8\n");
		assertThat(asked).containsExactly("walk 1");
	}

	/** The card waits for its robot's walk (the robot's end ends it). */
	@Test
	void aCardsCommandWaitingForItsRobotsWalkRunsOnceItsWalked() {
		card();
		file("walkstore.lua", """
			local devices = require("devices")
			local card = assert(devices.open("crafting0"))
			local robot = assert(devices.open("robotics0"))
			assert(robot.walk(3))
			local stored, left = card.store("front") -- waits: its robot's walking
			local out = io.open("/out", "w")
			out:write(tostring(stored), " ", tostring(left), "\\n")
			out:close()
			""");
		boot();
		type("lua /walkstore.lua\r");
		untilFile(root().resolve("out"), "true 0\n");
		assertThat(asked).containsExactly("walk 3", "store front");
	}

	/** A robot's stop ends its card's craft, which says so: the robot's free, the card's done. */
	@Test
	void aRobotsStopEndsItsCardsCraftWhichSaysSo() {
		CraftingCard card = card();
		file("stopcraft.lua", """
			local devices = require("devices")
			local card = assert(devices.open("crafting0"))
			local robot = assert(devices.open("robotics0"))
			assert(card.craft(50))
			robot.stop()
			local ok, why = devices.ended(card, "craft")
			assert(robot.walk(1)) -- (free at once)
			local out = io.open("/out", "w")
			out:write(tostring(ok), " ", why, "\\n")
			out:close()
			""");
		boot();
		type("lua /stopcraft.lua\r");
		untilFile(root().resolve("out"), "false stopped\n");
		assertThat(card.state()).as("its craft's over").isEqualTo(CraftingCard.IDLE);
	}

	/** "stop" written as a line (a set's stop) ends the threads waiting to give the robot commands, as stop() does. */
	@Test
	void aStopWrittenAsALineEndsTheWaitsAsAStopDoes() {
		file("linestop.lua", """
			local sys = require("sys")
			local thread = require("thread")
			local fd = assert(sys.open("/dev/robotics0", "w"))
			local answer
			local feeder = thread.create(function()
			  answer = table.pack(sys.write(fd, "walk 60\\nturn right\\nwalk 2\\n")) -- the turn waits for the walk
			end)
			sys.sleep(0.3)
			local stop = assert(sys.open("/dev/robotics0", "w"))
			assert(sys.write(stop, "stop\\n"))
			feeder:join()
			local out = io.open("/out", "w")
			out:write(tostring(answer[1]), " ", tostring(answer[3]), "\\n")
			out:close()
			""");
		boot();
		type("lua /linestop.lua\r");
		untilFile(root().resolve("out"), "nil ECANCELED\n");
		assertThat(asked).as("nothing after the stop").containsExactly("walk 60");
	}

	/** A last line with no end, written to a busy robot, goes at the close, which waits for it (its descriptor open meanwhile). */
	@Test
	void aClosesLastLineWaitsForABusyRobotAndGoes() {
		file("lastline.lua", """
			local sys = require("sys")
			local fd = assert(sys.open("/dev/robotics0", "w"))
			assert(sys.write(fd, "walk 2\\nturn right"))
			local closed, why, errno = sys.close(fd)
			local out = io.open("/out", "w")
			out:write(tostring(closed), " ", tostring(errno), "\\n")
			out:close()
			""");
		boot();
		type("lua /lastline.lua\r");
		untilFile(root().resolve("out"), "true nil\n");
		assertThat(asked).containsExactly("walk 2", "turn 90");
	}

	/** A write that waited and never came back (its thread killed) leaves nothing behind: the next write on the file is its own. */
	@Test
	void aWriteThatNeverCameBackLeavesNoLinesForTheNext() {
		file("killed.lua", """
			local sys = require("sys")
			local thread = require("thread")
			local fd = assert(sys.open("/dev/robotics0", "w"))
			local feeder = thread.create(function()
			  sys.write(fd, "walk 3\\nturn right\\n") -- the turn waits for the walk
			end)
			sys.sleep(0.1)
			feeder:kill()
			assert(sys.write(fd, "walk 1\\n")) -- waits for the walk 3, then goes: only it
			local out = io.open("/out", "w")
			out:write("done\\n")
			out:close()
			""");
		boot();
		type("lua /killed.lua\r");
		untilFile(root().resolve("out"), "done\n");
		assertThat(asked).as("the killed write's turn never went").containsExactly("walk 3", "walk 1");
	}

	/**
	 * News a waiting command's retry took into another thread's file wakes that thread (the retry
	 * counts as the pass's progress): its wait on the device would otherwise sleep through it, the
	 * device's interrupt already down.
	 */
	@Test
	void newsAWaitingCommandTookWakesTheThreadPollingTheSameFile() {
		SlowDevice slow = new SlowDevice();
		devices.put(Motherboard.exp(1), slow);
		ticks.add(slow::tick);
		file("news.lua", """
			local devices = require("devices")
			local thread = require("thread")
			local slow = assert(devices.open("fabricator0"))
			local worker = thread.create(function()
			  assert(slow.work(100000))
			  slow.work(1) -- waits: busy
			end)
			local ready = io.open("/ready", "w")
			ready:write("1\\n")
			ready:close()
			local got
			repeat
			  got = devices.event(slow)
			until got.event == "news"
			local out = io.open("/out", "w")
			out:write(got.event, "\\n")
			out:close()
			""");
		boot();
		type("lua /news.lua\r");
		untilFile(root().resolve("ready"), "1\n");
		settle(() -> slow.working());
		untilIdle(); // (both threads waiting: the worker's command, the main thread's poll)
		// A wake no thread takes (a device joining: the bus's interrupt), so the pass it makes moves no
		// thread on but the waiting command's retry, whose call brings the news.
		server.add(() -> {
			slow.newsAtNextRefusal(); // (the waiting command's retry, in the pass this wake makes: news comes during its call)
			devices.put("id:joining", new SlowDevice());
			com.payonel.modularcomputers.machine.TestBus.of(machine, devices);
		});
		untilFile(root().resolve("out"), "news\n");
	}

	/** An end's wait with a time limit ends then, however much other news comes meanwhile. */
	@Test
	void endedsTimeLimitHoldsWhileOtherNewsKeepsComing() {
		SlowDevice slow = new SlowDevice();
		slow.chatterEvery(5); // (news four times a second: none of it the end waited for)
		devices.put(Motherboard.exp(1), slow);
		ticks.add(slow::tick);
		file("timeout.lua", """
			local devices = require("devices")
			local slow = assert(devices.open("fabricator0"))
			local ok, why = devices.ended(slow, "work", 1)
			local out = io.open("/out", "w")
			out:write(tostring(ok), " ", tostring(why), "\\n")
			out:close()
			""");
		boot();
		type("lua /timeout.lua\r");
		untilFile(root().resolve("out"), "nil timed out\n");
	}

	/** A field's release written as a line ("release": set's) never waits, even while a move runs: a program can always let go. */
	@Test
	void aReleaseWrittenAsALineNeverWaitsForAMove() {
		com.payonel.modularcomputers.device.FieldCard field = new com.payonel.modularcomputers.device.FieldCard();
		boolean[] holding = {false};
		field.projectorIn(new com.payonel.modularcomputers.device.FieldCard.Projector() {
			@Override
			public Result scan(double range) {
				return Result.ok(List.of());
			}

			@Override
			public Result capture(String id, double[] at) {
				holding[0] = true;
				return Result.ok("minecraft:pig");
			}

			@Override
			public Result move(double forward, double right, double up) {
				return Result.ok(true); // (it moves until it's told it arrived: never, here)
			}

			@Override
			public void release() {
				holding[0] = false;
			}

			@Override
			public Map<String, Object> where() {
				return holding[0] ? Map.of("name", "minecraft:pig", "forward", 0.0, "right", 0.0, "up", 0.0) : null;
			}

			@Override
			public String check() {
				return null;
			}
		});
		devices.put(Motherboard.exp(1), field);
		file("letgo.lua", """
			local devices = require("devices")
			local field = assert(devices.open("field0"))
			assert(field.capture("pig"))
			assert(field.move(0, 0, 3)) -- (it never arrives)
			local f = assert(io.open("/dev/field0", "w"))
			f:write("release\\n")
			f:close()
			local ok, why = devices.ended(field, "move")
			local out = io.open("/out", "w")
			out:write(tostring(ok), " ", why, "\\n")
			out:close()
			""");
		boot();
		type("lua /letgo.lua\r");
		untilFile(root().resolve("out"), "false stopped\n");
		assertThat(holding[0]).as("let go").isFalse();
	}

	/** A redstone file read anew from its start (seek, the GPIO way) keeps a command's end for its pull: only the faces' changes are in the state. */
	@Test
	void aRedstoneFileReadAnewKeepsASetsEnd() {
		com.payonel.modularcomputers.device.RedstoneCard card = new com.payonel.modularcomputers.device.RedstoneCard();
		devices.put(Motherboard.exp(1), card);
		file("seek.lua", """
			local sys = require("sys")
			local fd = assert(sys.open("/dev/redstone0"))
			assert(sys.ioctl(fd, "set", {north = {powered = 7}}))
			sys.sleep(0.5) -- (its tick applies it: its end waits)
			sys.poll({[fd] = {PRI = true}}, 1) -- (a poll takes what's come into the file)
			assert(sys.lseek(fd, 0))
			sys.read(fd, 1000)
			local ended = sys.ioctl(fd, "pull")
			local out = io.open("/out", "w")
			out:write(tostring(ended and ended.event), " ", tostring(ended and ended.ok), "\\n")
			out:close()
			""");
		boot();
		type("lua /seek.lua\r");
		untilFile(root().resolve("out"), "set true\n");
	}
}
