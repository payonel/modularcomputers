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
