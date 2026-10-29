package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.SlowDevice;
import com.payonel.modularcomputers.runtime.Result;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * A buffered device in mcos (.agents/BUFFERED.md): a command while one runs blocks its thread only;
 * a stop ends the threads waiting to give it one; every file open on it sees its events.
 */
class BufferedMcosTest extends McosHarness {
	private final SlowDevice slow = new SlowDevice();

	BufferedMcosTest() {
		devices.put(Motherboard.exp(0), slow);
		ticks.add(slow::tick); // (its ticks: a work counts down with the machine's)
	}

	/**
	 * A command refused busy whose running one ends before the kernel looks (its end taken into the
	 * files as the thread blocks) is asked again at once: else it waits for an end already come, for
	 * ever (a loaded host's race: the square example hung on it).
	 */
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 3})
	void aCommandWhoseWaitEndedAsItWasRefusedRunsAtOnce(int refusal) {
		slow.endAsItRefuses(refusal); // (at the first refusal, or a later one: the kernel asks again before it idles)
		file("race.lua", """
			local devices = require("devices")
			local slow = assert(devices.open("fabricator0"))
			assert(slow.work(50))
			assert(slow.work(1)) -- refused busy, and the first's end comes with the refusal
			assert(devices.sync(slow)) -- (both done)
			local out = io.open("/out", "w")
			out:write(slow.peek(), "\\n")
			out:close()
			""");
		boot();
		type("lua /race.lua\r");
		untilFile(root().resolve("out"), "2\n");
	}

	/** The user's case: a thread through a series of blocking commands, another making progress all the while. */
	@Test
	void aThreadRunningBlockingCommandsLeavesItsOtherThreadsRunning() {
		file("series.lua", """
			local devices = require("devices")
			local sys = require("sys")
			local thread = require("thread")
			local slow = assert(devices.open("fabricator0"))
			local counted, done = 0, false
			local counter = thread.create(function()
			  while not done do
			    counted = counted + 1
			    sys.sleep(0.05)
			  end
			end)
			local seen = {}
			for _ = 1, 3 do
			  assert(slow.work(5)) -- the second and third wait for the one before: this thread only
			  seen[#seen + 1] = counted
			end
			local works = slow.peek() -- a command too: it waits for the third work's end
			done = true
			counter:join()
			local out = io.open("/out", "w")
			out:write(works, " ", seen[1], " ", seen[2], " ", seen[3], " ", counted, "\\n")
			out:close()
			""");
		boot();
		type("lua /series.lua\r");
		settle(() -> java.nio.file.Files.isRegularFile(root().resolve("out")) && read(root().resolve("out")).endsWith("\n"));
		long[] got = Arrays.stream(read(root().resolve("out")).trim().split(" ")).mapToLong(Long::parseLong).toArray();
		assertThat(got[0]).as("every work done, in turn").isEqualTo(3);
		assertThat(got[2]).as("the other thread ran while the second waited for the first").isGreaterThan(got[1]);
		assertThat(got[3]).as("and while the third waited").isGreaterThan(got[2]);
		assertThat(got[4]).as("and while the peek did").isGreaterThan(got[3]);
	}

	/**
	 * The user's case on a robot: a thread walks it through a series of blocking commands (each waits
	 * for the one before: the robot does one at a time), another thread counting all the while.
	 */
	@Test
	void aThreadWalksARobotThroughASeriesOfBlockingCommandsWhileAnotherMakesProgress() {
		com.payonel.modularcomputers.device.RoboticsDevice robotics = new com.payonel.modularcomputers.device.RoboticsDevice();
		java.util.List<String> asked = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
		int[] left = {0}; // (ticks till the move under way ends)
		robotics.moveWith(new com.payonel.modularcomputers.device.RoboticsDevice.Body() {
			@Override
			public String walk(double forward, double right, double up) {
				asked.add("walk " + (long) forward);
				left[0] = 6;
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
			}

			@Override
			public Result position() {
				return Result.ok(java.util.Map.of("forward", 0L, "right", 0L, "up", 0L, "heading", 0L));
			}
		});
		ticks.add(() -> {
			if (left[0] > 0 && --left[0] == 0) {
				robotics.arrived(); // (the move under way ends: walk or turn)
			}
		});
		devices.put(Motherboard.exp(com.payonel.modularcomputers.machine.GolemLayout.ROBOTICS_LANE), robotics);
		file("walker.lua", """
			local devices = require("devices")
			local sys = require("sys")
			local thread = require("thread")
			local robot = assert(devices.open("robotics0"))
			local counted, done = 0, false
			local counter = thread.create(function()
			  while not done do
			    counted = counted + 1
			    sys.sleep(0.05)
			  end
			end)
			local seen = {}
			local walker = thread.create(function()
			  for _, step in ipairs({{"walk", 3}, {"turn", "right"}, {"walk", 2}, {"turn", "right"}, {"walk", 1}}) do
			    assert(robot[step[1]](step[2])) -- each waits for the one before, this thread only
			    seen[#seen + 1] = counted
			  end
			  robot.position() -- (a command too: it waits for the last walk's end)
			  seen[#seen + 1] = counted
			end)
			walker:join()
			done = true
			counter:join()
			local out = io.open("/out", "w")
			out:write(table.concat(seen, " "), "\\n")
			out:close()
			""");
		boot();
		type("lua /walker.lua\r");
		settle(() -> java.nio.file.Files.isRegularFile(root().resolve("out")) && read(root().resolve("out")).endsWith("\n"));
		assertThat(asked).as("every command, in order").containsExactly("walk 3", "turn 90", "walk 2", "turn 90", "walk 1");
		long[] seen = Arrays.stream(read(root().resolve("out")).trim().split(" ")).mapToLong(Long::parseLong).toArray();
		for (int i = 1; i < seen.length; i++) {
			assertThat(seen[i]).as("the other thread ran while command " + (i + 1) + " waited for the one before").isGreaterThan(seen[i - 1]);
		}
	}

	@Test
	void aStopEndsTheThreadsWaitingToGiveItACommand() {
		file("stop.lua", """
			local devices = require("devices")
			local sys = require("sys")
			local thread = require("thread")
			local slow = assert(devices.open("fabricator0"))
			assert(slow.work(100000))
			local answer
			local waiter = thread.create(function()
			  answer = table.pack(slow.work(1)) -- waits: the device is busy
			end)
			sys.sleep(0.2)
			assert(slow.stop()) -- free: at once; and the waiter's wait ends with it
			waiter:join()
			local out = io.open("/out", "w")
			out:write(tostring(answer[1]), " ", tostring(answer[2]), " ", tostring(answer[3]), " ", slow.peek(), "\\n")
			out:close()
			""");
		boot();
		type("lua /stop.lua\r");
		untilFile(root().resolve("out"), "nil stopped ECANCELED 0\n");
		assertThat(slow.working()).as("nothing started after the stop").isFalse();
	}

	@Test
	void everyFileOpenOnADeviceSeesItsEventsAndAnEndSaysHowItsCommandWent() {
		file("files.lua", """
			local devices = require("devices")
			local one = assert(devices.open("fabricator0"))
			local two = assert(devices.open("fabricator0"))
			assert(one.work(2))
			local a, b = devices.event(one), devices.event(two)
			assert(two.work(50))
			assert(one.stop())
			local ended = devices.event(two) -- (the second work's end: the stop's)
			local ok, why = ended.ok, ended.why
			local _, refused, errno = one.work(0) -- the device's own refusal: at once, nothing started
			local out = io.open("/out", "w")
			out:write(a.event, " ", tostring(a.ok), " ", b.event, " ", tostring(b.ok), " ", tostring(ok), " ", why, " ", refused, " ", errno, "\\n")
			out:close()
			""");
		boot();
		type("lua /files.lua\r");
		untilFile(root().resolve("out"), "work true work true false stopped ticks: 0 EINVAL\n");
	}
}
