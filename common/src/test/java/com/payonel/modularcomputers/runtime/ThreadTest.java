package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * mcos's threads (the kernel's tasks, the thread library): preempted as processes are, each blocked
 * only by its own calls, sharing the process's globals; joined for what they did, cancelled, kept
 * apart by mutexes; ended with their process. Checked by what the programs write, never the screen.
 */
class ThreadTest extends McosHarness {
	/** Runs a program, until its status file says it ended (its errors in /err). */
	private void run(String program) {
		file("t.lua", program);
		boot();
		type("lua /t.lua 2> /err; echo $? > /status\r");
		untilFile(root().resolve("status"), read(root().resolve("status")).isEmpty() ? "" : read(root().resolve("status")));
	}

	private String runFor(String program) {
		file("t.lua", program);
		boot();
		type("lua /t.lua 2> /err; echo $? > /status\r");
		settle(() -> Files.exists(root().resolve("status")) && read(root().resolve("status")).endsWith("\n"));
		return read(root().resolve("status"));
	}

	@Test
	void aBusyThreadDoesntStarveAnother() {
		String status = runFor("""
			local thread = require("thread")
			local sys = require("sys")
			local done = false
			local busy = thread.create(function()
			  local spins = 0
			  while not done do spins = spins + 1 end -- (never yields: only the timer stops it)
			  return spins > 0
			end)
			local sleeper = thread.create(function()
			  sys.sleep(0.2)
			  done = true
			  return "woke"
			end)
			local f = io.open("/out", "w")
			f:write(tostring(select(2, sleeper:join())), " ", tostring(select(2, busy:join())), "\\n")
			f:close()
			""");
		assertThat(status).as(() -> "its errors: " + read(root().resolve("err"))).isEqualTo("0\n");
		assertThat(read(root().resolve("out"))).isEqualTo("woke true\n");
	}

	/**
	 * The user's case: a thread started while its main thread waits (a shell's prompt, here a read)
	 * runs, says it's running, and finishes. A thread's own fields read through to its process's when
	 * unset, once, so it took its main thread's "blocked" and never ran (seen failing).
	 */
	@Test
	void aThreadStartedWhileItsMainThreadWaitsRunsAndFinishes() {
		file("t.lua", """
			local thread = require("thread")
			local sys = require("sys")
			local busy = thread.create(function()
			  local x = 0
			  for i = 1, 200000 do x = x + 1 end -- (only the timer stops it)
			  local f = io.open("/busy", "w") f:write("done") f:close()
			end)
			local watcher = thread.create(function()
			  local f = io.open("/status", "w") f:write(busy:status(), "\\n") f:close()
			end)
			io.read("l") -- (the main thread waits: for a line that comes once the busy thread's done)
			local f = io.open("/after", "w") f:write(tostring(busy:status()), " ", tostring(watcher:status()), "\\n") f:close()
			""");
		boot();
		type("lua /t.lua 2> /err\r");
		untilFile(root().resolve("busy"), "done");
		assertThat(read(root().resolve("status"))).as("running, not waiting: it was never in a call").isEqualTo("running\n");
		untilIdle();
		type("go\r");
		untilFile(root().resolve("after"), "dead dead\n");
		assertThat(read(root().resolve("err"))).isEmpty();
	}

	@Test
	void aThreadBlockedReadingLeavesTheOthersRunning() {
		file("t.lua", """
			local thread = require("thread")
			local sys = require("sys")
			local reader = thread.create(function() return io.read("l") end)
			local f = io.open("/ticked", "w")
			for i = 1, 3 do sys.sleep(0.05) f:write(i) end -- (while the reader waits for a line)
			f:close()
			local ok, line = reader:join()
			f = io.open("/read", "w") f:write(line, "\\n") f:close()
			""");
		boot();
		type("lua /t.lua 2> /err\r");
		settle(() -> Files.exists(root().resolve("ticked")) && read(root().resolve("ticked")).equals("123")
			|| Files.exists(root().resolve("err")) && !read(root().resolve("err")).isEmpty());
		assertThat(read(root().resolve("err"))).isEmpty();
		untilIdle();
		type("hello\r");
		untilFile(root().resolve("read"), "hello\n");
		assertThat(read(root().resolve("err"))).isEmpty();
	}

	@Test
	void joinGivesWhatItReturnedOrItsErrorAndTimesOutAndCancelEndsIt() {
		String status = runFor("""
			local thread = require("thread")
			local sys = require("sys")
			local results = {}
			local function add(...) results[#results + 1] = table.concat(table.pack(...), ",", 1, select("#", ...)) end
			local function strs(...) local t = table.pack(...) for i = 1, t.n do t[i] = tostring(t[i]) end return table.unpack(t, 1, t.n) end
			add(strs(thread.create(function(a, b) return a + b, "sum" end, 2, 3):join()))
			local ok, why = thread.create(function() error("boom", 0) end):join()
			add(strs(ok, why))
			local forever = thread.create(function() sys.sleep(1000) end)
			add(strs(forever:join(0.1)))
			add(forever:status())
			add(strs(forever:kill()))
			add(strs(forever:join()))
			add(strs(forever:status()))
			add(strs(sys.join(thread.current())))
			local quick = thread.create(function() end)
			sys.sleep(0.05)
			add(quick:status()) -- (ended, not yet joined)
			local f = io.open("/out", "w") f:write(table.concat(results, "\\n"), "\\n") f:close()
			""");
		assertThat(status).as(() -> "its errors: " + read(root().resolve("err"))).isEqualTo("0\n");
		assertThat(read(root().resolve("out"))).isEqualTo(String.join("\n",
			"true,5,sum", "false,boom", "nil,Connection timed out,ETIMEDOUT", "suspended", "true", "false,killed", "nil",
			"nil,Resource deadlock avoided,EDEADLK", "dead") + "\n");
		assertThat(read(root().resolve("err"))).as("a thread's error, said on stderr").contains("thread", "boom");
	}

	@Test
	void aMutexLetsOneThreadAtATimeThroughAndOnlyItsHolderUnlocksIt() {
		String status = runFor("""
			local thread = require("thread")
			local sys = require("sys")
			local lock = thread.mutex()
			local count = 0
			local function work()
			  for i = 1, 5 do
			    lock:run(function()
			      local was = count
			      sys.sleep(0) -- (another thread runs here: without the lock, it would read the same count)
			      count = was + 1
			    end)
			  end
			end
			local a, b = thread.create(work), thread.create(work)
			a:join() b:join()
			lock:lock()
			local _, why = thread.create(function() return lock:unlock() end):join()
			local _, _, e = thread.create(function() return select(3, lock:unlock()) end):join()
			local f = io.open("/out", "w") f:write(count, " ", tostring(why), "\\n") f:close()
			""");
		assertThat(status).as(() -> "its errors: " + read(root().resolve("err"))).isEqualTo("0\n");
		assertThat(read(root().resolve("out"))).isEqualTo("10 nil\n");
	}

	@Test
	void theMainThreadEndingEndsItsThreads() {
		String status = runFor("""
			local thread = require("thread")
			local sys = require("sys")
			thread.create(function()
			  sys.sleep(5) -- (100 ticks: long after its main thread ends)
			  local f = io.open("/late", "w") f:write("still here") f:close()
			end)
			local f = io.open("/ps", "w")
			for _, p in ipairs(sys.processes()) do if p.path == "/bin/lua" then f:write(p.threads, "\\n") end end
			f:close()
			""");
		assertThat(status).as(() -> "its errors: " + read(root().resolve("err"))).isEqualTo("0\n");
		assertThat(read(root().resolve("ps"))).as("ps counts its threads").isEqualTo("2\n");
		long end = System.nanoTime() + 1_000_000_000L; // (a thousand ticks: its thread's sleep ten times over)
		settle(() -> System.nanoTime() > end);
		assertThat(root().resolve("late")).as("its thread ended with it").doesNotExist();
	}
}
