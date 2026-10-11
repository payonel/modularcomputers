package com.payonel.modularcomputers.runtime;

import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs a program as a coroutine on its own virtual thread, handing control back and forth with
 * the host (the server tick) so only one side runs at a time. Every motherboard call the program
 * makes is a request the host carries out on its own thread, then answers; so device methods
 * always run on the server thread.
 * <p>
 * The host decides when the program runs: each {@link #tick} it answers at most a fixed number
 * of calls, then leaves the program paused in its next call until the next tick. A program
 * waiting for an interrupt stays paused until one is pending or its time is up. The program
 * can't refuse: every call is a point where the host takes over.
 * <p>
 * Not saveable: a running program's state lives in its thread. When the machine unloads, the
 * program stops.
 */
public final class ProgramRunner {
	/** Something the program asks the host for. */
	public sealed interface Request {
	}

	/** A motherboard call, answered with its result. */
	public record Call(Function<Board, Object> call) implements Request {
	}

	/** Wait for an interrupt until game tick {@code until}; answered with the pending addresses. */
	public record Wait(long until) implements Request {
	}

	/** The program returned. */
	private record Done() implements Request {
	}

	/** The program threw: the CPU's abort screen. */
	private record Crashed(Throwable error) implements Request {
	}

	/** Unwinds a stopped program's thread. An Error, so ordinary catch blocks let it through. */
	private static final class Stopped extends Error {
		Stopped() {
			super(null, null, false, false);
		}
	}

	/** What the host's {@link #tick} found. */
	public enum State {
		/** Paused mid-run: the next tick carries on. */
		RUNNING,
		/** Waiting for an interrupt. */
		WAITING,
		/** The program returned; the CPU halts, power stays on. */
		HALTED,
		/** The program threw; see {@link #crash()}. */
		CRASHED,
	}

	private final SynchronousQueue<Object> toProgram = new SynchronousQueue<>();
	private final SynchronousQueue<Request> toHost = new SynchronousQueue<>();
	private final Thread thread;
	private Request pending;
	private Throwable crash;
	private State state = State.RUNNING;

	/** {@code program} runs with a board that sends each call to the host. */
	public ProgramRunner(Consumer<Board> program, String name) {
		Board board = new RemoteBoard(this);
		this.thread = Thread.ofVirtual().name(name).unstarted(() -> {
			Request last;
			try {
				awaitHost();
				program.accept(board);
				last = new Done();
			} catch (Stopped stopped) {
				return;
			} catch (Throwable error) {
				last = new Crashed(error);
			}
			try {
				toHost.put(last);
			} catch (InterruptedException e) {
				// stopped while reporting: nobody's listening
			}
		});
	}

	public State state() {
		return state;
	}

	/** Why the program crashed, when it did. */
	public Throwable crash() {
		return crash;
	}

	/**
	 * Host side: lets the program run, answering at most {@code calls} motherboard calls with
	 * {@code board}, the real one. {@code now} is the game tick; {@code interrupts} lists the
	 * pending interrupts, for a waiting program.
	 */
	public State tick(Board board, int calls, long now, java.util.function.Supplier<List<String>> interrupts) {
		if (state == State.HALTED || state == State.CRASHED) {
			return state;
		}
		Object answer = null;
		if (pending == null) {
			thread.start(); // the first tick: the program begins
		} else if (pending instanceof Wait wait) {
			List<String> ready = interrupts.get();
			if (ready.isEmpty() && now < wait.until()) {
				return state = State.WAITING;
			}
			answer = ready;
		} else if (pending instanceof Call call) {
			answer = call.call().apply(board);
			calls--;
		}
		while (true) {
			pending = exchange(answer);
			switch (pending) {
				case Done done -> {
					return state = State.HALTED;
				}
				case Crashed crashed -> {
					crash = crashed.error();
					return state = State.CRASHED;
				}
				case Wait wait -> {
					List<String> ready = interrupts.get();
					if (ready.isEmpty() && now < wait.until()) {
						return state = State.WAITING;
					}
					answer = ready;
				}
				case Call call -> {
					if (calls <= 0) {
						return state = State.RUNNING; // out of calls this tick: the call waits for the next
					}
					answer = call.call().apply(board);
					calls--;
				}
			}
		}
	}

	/** Stops the program, wherever it's paused: the machine lost power or unloaded. */
	public void stop() {
		state = State.HALTED;
		thread.interrupt();
	}

	private Request exchange(Object answer) {
		try {
			toProgram.put(answer == null ? NOTHING : answer);
			return toHost.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while running a program", e);
		}
	}

	private static final Object NOTHING = new Object();

	/** Program side: send a request, wait for the answer. */
	Object request(Request request) {
		try {
			toHost.put(request);
			return awaitHost();
		} catch (InterruptedException e) {
			throw new Stopped();
		}
	}

	private Object awaitHost() throws InterruptedException {
		Object answer = toProgram.take();
		return answer == NOTHING ? null : answer;
	}

	/** The board a program holds: every call becomes a request to the host. */
	private record RemoteBoard(ProgramRunner runner) implements Board {
		@SuppressWarnings("unchecked")
		private <T> T call(Function<Board, Object> call) {
			return (T) runner.request(new Call(call));
		}

		@Override
		public List<String> list() {
			return call(Board::list);
		}

		@Override
		public com.payonel.modularcomputers.bus.BusStatus status() {
			return call(Board::status);
		}

		@Override
		public java.util.Optional<String> type(String address) {
			return call(board -> board.type(address));
		}

		@Override
		public List<String> methods(String address) {
			return call(board -> board.methods(address));
		}

		@Override
		public java.util.Optional<String> doc(String address, String method) {
			return call(board -> board.doc(address, method));
		}

		@Override
		public Result invoke(String address, String method, Object... args) {
			return call(board -> board.invoke(address, method, args));
		}

		@Override
		@SuppressWarnings("unchecked")
		public List<String> waitForInterrupt(double seconds) {
			long now = (long) (Long) runner.request(new Call(Board::uptime));
			return (List<String>) runner.request(new Wait(now + Math.max(0, Math.round(seconds * 20))));
		}

		@Override
		public long uptime() {
			return call(Board::uptime);
		}
	}
}
