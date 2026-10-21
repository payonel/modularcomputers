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
		/** The program turned the machine off (ACPI soft-off): the board cuts its power. */
		POWERED_OFF,
		/** The program asked for a restart: the board turns off and on again. */
		REBOOTING,
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

	/**
	 * Server side: the program's state as it runs, to save; empty when it can't be saved (it comes
	 * back halted). {@code who}: the computer, as a log names it (which board, where).
	 */
	default java.util.Optional<byte[]> persist(Board board, String who) {
		return java.util.Optional.empty();
	}

	/** Bytes of memory the program holds now (a Lua CPU counts its VM's), for {@link Board#hardware}. */
	default long memoryUsed() {
		return 0;
	}

	/**
	 * Server thread: the sources with an interrupt raised now (the bus, devices with data they still
	 * hold), published as soon as one rises, like an interrupt controller's status register.
	 */
	default void interruptsRaised(List<String> raised) {
	}

	/** Server thread: these addresses' devices left the bus (or were replaced): what was taken from them for the program and not yet handed over goes. */
	default void devicesLeft(java.util.Collection<String> addresses) {
	}

	/** The program's memory, which devices charge what they hold for it to ({@link Arguments#account}). */
	default MemoryAccount memoryAccount() {
		return MemoryAccount.UNLIMITED;
	}

		/** Given how to ask the server thread to {@link #tick} it soon (a runner on its own thread asks when a request comes in). */
	default void serveWith(Runnable serveSoon) {
	}
}
