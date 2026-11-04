package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.Set;

/**
 * A buffered device (AGENTS.md: buffered devices): it runs one command at a time. Its methods are
 * commands, but {@link #SYNC} and those it names {@link #free} (its live state, its events, {@code stop}): the board refuses a
 * command at once while the device {@link #refusal refuses} one (another is running: {@link #BUSY}),
 * doing nothing; a command that runs over time raises an event, named after its method, as it ends
 * ({@link EventQueue#ended}), its "idle again". Every other device is direct.
 */
public interface Buffered {
	/** Why a command is refused while another is running. */
	String BUSY = "busy";

	/**
	 * Why a move ended: a block in its way (the user's: one shape, wherever it's said). Its event
	 * says which block ({@code block}, the block's id; "" when no one block can be named, never nil) and
	 * which face of what moved met it ({@code face}, in the device's own terms), as {@link EventQueue#blocked}
	 * gives them ({@link EventQueue#blocked}, the one way it's built).
	 */
	String BLOCKED = "blocked";

	/** Why something ended: a program called its device's stop (the user's: one shape, {@link EventQueue#stopped}). */
	String STOPPED = "stopped";

	/**
	 * Why a positional command can't act (the user's: positional, always): its device is off where
	 * its last command left it (a shove, a fall). Answering at once, it fails so; over time, its
	 * event ends so ({@link EventQueue#drifted}).
	 */
	String DRIFTED = "drifted";

	/**
	 * Why a command under way at a save ended at the load: it couldn't go on (the user's: a fallback;
	 * a command goes on after a load when it can). One shape ({@link EventQueue#interrupted}).
	 */
	String INTERRUPTED = "interrupted";

	/**
	 * Why a command ended: what it was acting on is gone (the user's, one shape: {@link
	 * EventQueue#lost}), its name (registry id), its own id ("" for a block) and where it last was.
	 */
	String LOST = "lost";

	/**
	 * The board gives a command (a method not free) with these arguments, as the program gave them,
	 * before calling it: a stopped, blocked or drifted end says them in its call.
	 */
	default void called(String method, java.util.List<Object> args) {
	}

	/** Its methods that answer at once, always: not commands (and {@link #SYNC}, every one's). */
	Set<String> free();

	/** Whether {@code method} answers at once, always: {@link #SYNC}, or one it names {@link #free}. */
	default boolean isFree(String method) {
		return SYNC.equals(method) || free().contains(method);
	}

	/** Whether a command of its own is under way: its queue (one deep) isn't empty. */
	boolean busy();

	/** Every buffered device's sync: whether its queue is empty (the user's: as the sound card's, OSS's). */
	String SYNC = "sync";

	@Method(doc = "sync(): true when no command of its own is under way (its queue is empty), else false, its interrupt saying when; never waits (mcos's sync waits on it). How its commands went is in its events")
	default Result sync(Arguments args) {
		return Result.ok(!busy());
	}

	/**
	 * Why it can't take {@code method}'s command now (null: it can): {@link #BUSY} while one runs, or
	 * its own reason (a golem in a field: held). Asked only for commands, as each comes. No method of a
	 * device fails with these reasons itself: they mean "wait" (mcos blocks on them).
	 */
	String refusal(String method);

	/**
	 * The device whose news ends the wait {@link #refusal} just gave, when it isn't this one (null): a
	 * crafting card waiting for its robot's moves to end. The board's
	 * refusal names it, so a program (and mcos) knows which device to wait on.
	 */
	default Object blocker(String method) {
		return null;
	}
}
