package com.payonel.modularcomputers.runtime;

import java.util.List;
import java.util.function.Supplier;

/**
 * A program running on a CPU, as the host drives it: each tick it gets a turn, answering at most a
 * fixed number of motherboard calls on the server thread. A Java CPU's programs run as coroutines on
 * virtual threads ({@link ProgramRunner}); a Lua CPU's in the Lua engine ({@link LuaRunner}).
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
}
