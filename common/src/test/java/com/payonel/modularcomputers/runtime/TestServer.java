package com.payonel.modularcomputers.runtime;

import com.payonel.modularcomputers.machine.Machine;

/**
 * The tests' server thread, as a game's paces a computer: before each tick, its program gets up to a
 * tick's time to reach the host (a call, a wait) or end. A loop ticking as fast as it can ran a
 * starved computer's clock ahead of it on a loaded host (its timeouts ran out before its code did).
 */
final class TestServer {
	/** The most a computing program holds a tick back: a game tick's time. */
	private static final long TICK_NANOS = 50_000_000L;
	/** How often it looks meanwhile. */
	private static final long LOOK_NANOS = 50_000L;

	private TestServer() {
	}

	/** Until {@code machine}'s program needs the host (or ends), a tick's time at most. */
	static void awaitHost(Machine machine) {
		if (!(machine.runner().orElse(null) instanceof LuaRunner lua)) {
			return; // (a Java program's turns are the tick's own)
		}
		long end = System.nanoTime() + TICK_NANOS;
		while (lua.alive() && lua.state() == Runner.State.RUNNING && lua.queuedCalls() == 0 && System.nanoTime() < end) {
			java.util.concurrent.locks.LockSupport.parkNanos(LOOK_NANOS);
		}
	}
}
