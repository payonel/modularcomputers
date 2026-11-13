package com.payonel.modularcomputers.runtime;

import java.util.ArrayDeque;
import java.util.concurrent.locks.LockSupport;

/**
 * The host's cores for Lua (the user's, 2026-11-12: a pool, configurable, 4 by default, and the
 * machines spin): however many computers there are, at most {@link #size()} run Lua at once, as an
 * OS schedules its runnable threads on its CPUs. A computer holds a core only while its firmware
 * runs (a resume, up to its next request: a slice spent, a board call, a wait), and gives it up at
 * each, so a busy loop gets a slice a turn, in turn (first come, first served), and a computer
 * waiting on the server or an interrupt holds none. A computer's thread waits for a core parked,
 * and is woken by a core handed to it, or by its own news (a stop: {@code woken}).
 * <p>
 * OpenComputers ran its computers on a pool of threads (its {@code threads} setting, 4); its
 * computers had to yield, so a pool thread was free at each. Ours never yield (their syscalls
 * freeze), so each keeps its own thread, parked whenever it holds no core: the same bound on the
 * host's cores, without making the runner a task a pool can resume.
 */
public final class CpuCores {
	/** How many computers run Lua at once by default (OpenComputers' threads). */
	public static final int DEFAULT = 4;
	/** The setting that sets it. */
	public static final String KEY = "cpu_cores";

	private static volatile int size = DEFAULT;
	private static int held;
	/** The threads waiting for a core, first come first served; a core freed goes to the head. */
	private static final ArrayDeque<Waiter> waiting = new ArrayDeque<>();
	private static final Object LOCK = new Object();

	private CpuCores() {
	}

	/** How many there are now. */
	public static int size() {
		return size;
	}

	/** Sets how many (a reload: more are handed out at once, fewer as cores are given up). */
	public static void configure(int cores) {
		synchronized (LOCK) {
			size = Math.max(1, cores);
			grant();
		}
	}

	/** A thread waiting for a core. */
	private static final class Waiter {
		final Thread thread = Thread.currentThread();
		volatile boolean granted;
	}

	/**
	 * Takes a core, waiting for one parked. {@code woken} runs each time the thread wakes without
	 * one (its own news: a stop throws); a throw leaves the line with no core held.
	 */
	public static void acquire(Runnable woken) {
		Waiter me;
		synchronized (LOCK) {
			if (held < size && waiting.isEmpty()) {
				held++;
				return;
			}
			me = new Waiter();
			waiting.add(me);
		}
		try {
			while (!me.granted) {
				LockSupport.park(CpuCores.class);
				if (!me.granted) {
					woken.run();
				}
			}
		} catch (RuntimeException | Error leaving) {
			synchronized (LOCK) {
				if (!waiting.remove(me)) {
					held--; // handed one as it left: it goes to the next
					grant();
				}
			}
			throw leaving;
		}
	}

	/** Gives a core up: to the thread waiting longest, if one is. */
	public static void release() {
		synchronized (LOCK) {
			held--;
			grant();
		}
	}

	/** Hands free cores to the threads waiting, in their order. */
	private static void grant() {
		while (held < size && !waiting.isEmpty()) {
			Waiter next = waiting.poll();
			held++;
			next.granted = true;
			LockSupport.unpark(next.thread);
		}
	}
}
