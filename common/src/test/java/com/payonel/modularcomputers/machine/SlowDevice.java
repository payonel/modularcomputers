package com.payonel.modularcomputers.machine;

import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.device.Buffered;
import com.payonel.modularcomputers.device.EventQueue;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.Map;
import java.util.Set;

/**
 * The tests' buffered device: {@code work(ticks)} runs for that many of its ticks ({@link #tick},
 * the test's), {@code peek()} is a command done at once (its answer: works done), {@code get},
 * {@code pull} and {@code stop} are free.
 */
public final class SlowDevice implements com.payonel.modularcomputers.bus.Device, Buffered {
	private final InterruptLine line = new InterruptLine();
	private boolean accepting;
	private final EventQueue events = new EventQueue(line, () -> {
	}, () -> accepting);
	/** Ticks left of the command running (0: idle). */
	private long left;
	private long done;
	/**
	 * Which refusal (busy) the work running ends at, just as it refuses (0: none): its end comes
	 * between the refusal and the kernel's next look, the race a loaded host makes now and then, made
	 * on purpose.
	 */
	private int endsAtRefusal;
	private int refusals;

	/** Its work's end comes just as it refuses a command, the {@code nth} time it does. */
	public void endAsItRefuses(int nth) {
		endsAtRefusal = nth;
	}

	/** Which refusal raises news as it's given (0: none): news coming during a waiting command's call, on purpose. */
	private int newsAtRefusal;

	public void newsAsItRefuses(int nth) {
		newsAtRefusal = nth;
	}

	/** Its next refusal raises news as it's given. */
	public void newsAtNextRefusal() {
		newsAtRefusal = refusals + 1;
	}

	/** How often it raises news of its own, in its ticks (0: never): a device that's never quiet. */
	private int chatter;
	private int ticked;

	public void chatterEvery(int ticks) {
		chatter = ticks;
	}

	@Override
	public DeviceType type() {
		return DeviceType.FABRICATOR;
	}

	@Override
	public Set<String> free() {
		return Set.of("get", "pull", "stop");
	}

	@Override
	public String refusal(String method) {
		if (left <= 0) {
			return null;
		}
		refusals++;
		if (refusals == newsAtRefusal) {
			events.add(Map.of("event", "news"));
		}
		if (refusals == endsAtRefusal) {
			left = 1;
			tick(); // (busy, and then done: its end is waiting before anyone looks)
		}
		return BUSY;
	}

	@Method(doc = "work(ticks): works that many ticks; true at once (its event: work)")
	public Result work(Arguments args) {
		long ticks = args.checkInteger(0);
		if (ticks < 1) {
			return Result.fail("ticks: " + ticks);
		}
		left = ticks;
		return Result.ok(true);
	}

	@Method(doc = "peek(): how many works it's done")
	public Result peek(Arguments args) {
		return Result.ok(done);
	}

	@Method(doc = "get(): {working}")
	public Result get(Arguments args) {
		return Result.ok(Map.of("working", left > 0));
	}

	@Method(doc = "stop(): stops its work (its event: work, stopped)")
	public Result stop(Arguments args) {
		if (left > 0) {
			left = 0;
			events.ended("work", false, "stopped", null);
		}
		return Result.ok();
	}

	@Method(doc = "pull(): its oldest event, or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	/** One of its ticks: a work counts down, and ends; its chatter, if it has some. */
	public void tick() {
		if (chatter > 0 && ++ticked % chatter == 0) {
			events.add(Map.of("event", "news"));
		}
		if (left > 0 && --left == 0) {
			done++;
			events.ended("work", true, null, null);
		}
	}

	public boolean working() {
		return left > 0;
	}

	@Override
	public String streamMethod() {
		return "pull";
	}

	@Override
	public boolean interruptPending() {
		return events.pending();
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	@Override
	public void accepting(boolean accepting) {
		this.accepting = accepting;
		if (!accepting) {
			events.clear();
			left = 0;
		}
	}
}
