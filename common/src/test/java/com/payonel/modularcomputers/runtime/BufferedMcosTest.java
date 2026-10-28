package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.machine.SlowDevice;
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
	void everyFileOpenOnADeviceSeesItsEventsAndEndedSaysHowACommandWent() {
		file("files.lua", """
			local devices = require("devices")
			local one = assert(devices.open("fabricator0"))
			local two = assert(devices.open("fabricator0"))
			assert(one.work(2))
			local a, b = devices.event(one), devices.event(two)
			assert(two.work(50))
			assert(one.stop())
			local ok, why = devices.ended(two, "work")
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
