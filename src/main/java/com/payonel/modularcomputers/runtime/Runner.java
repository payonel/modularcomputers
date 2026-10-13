package com.payonel.modularcomputers.runtime;

import java.util.List;
import java.util.function.Supplier;

/**
 * A program running on a CPU, as the host drives it: each tick (and when it asks) the server thread
 * carries out its motherboard calls. A Java CPU's programs run as coroutines on virtual threads, a
 * fixed number of calls a tick ({@link ProgramRunner}); a Lua CPU's on the computer's own thread,
 * its calls limited by server time ({@link LuaRunner}).
 */
public interface Runner {
	/** What a turn ended in. */
	enum State {
		/** Paused mid-run: the next tick carries on. */
		RUNNING,
		/** Waiting for an interrupt. */
		WAITING,
		/** The program halted the CPU; power stays on. */
		HALTED,
		/** The program returned instead of halting: a panic, like Linux's init exiting. */
		RETURNED,
		/** The program threw; see {@link #crash()}. */
		CRASHED,
	}

	State state();

	/** How many calls the host has answered, all told. */
	long answered();

	/** Why the program crashed, when it did. */
	Throwable crash();

	/**
	 * Host side: lets the program run, answering at most {@code calls} motherboard calls with
	 * {@code board}, the real one. {@code now} is the game tick; {@code interrupts} lists the
	 * pending interrupts, for a waiting program.
	 */
	State tick(Board board, int calls, long now, Supplier<List<String>> interrupts);

	/** Stops the program, wherever it's paused: the machine lost power or unloaded. */
	void stop();

	/** Whether the host limits it to so many calls a tick ({@link #tick}'s {@code calls}); a Lua CPU is limited by server time instead. */
	default boolean callLimited() {
		return true;
	}

	/** Given how to ask the server thread to {@link #tick} it soon (a runner on its own thread asks when a request comes in). */
	default void serveWith(Runnable serveSoon) {
	}
}
