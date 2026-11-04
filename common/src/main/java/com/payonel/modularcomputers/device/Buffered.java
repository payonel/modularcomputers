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
	 * Why a command ended: the game won't let what it acted on be broken (the user's, one shape: {@link
	 * EventQueue#unbreakable}): its name, its id ("" for a block) and where it is, as {@link #LOST}'s.
	 */
	String UNBREAKABLE = "unbreakable";

	/**
	 * Why a craft ended: what's in its grid makes nothing (the user's, one shape: {@link
	 * EventQueue#missingInputs}): an empty grid, or no recipe its pattern matches; and its call.
	 */
	String MISSING_INPUTS = "missing_inputs";

	/**
	 * Why a craft ended: there's no room for what it makes (the user's, one shape: {@link
	 * EventQueue#noSpace}): its storage can't take it, or a leftover its grid can't take back; and its call.
	 */
	String NO_SPACE = "no_space";

	/**
	 * Why a command ended, a call was refused, or a notice: what it needs to do it is missing (the
	 * user's, one shape, {@link EventQueue#systemFailure}): {@code missing} says what, one of the
	 * {@code MISSING_} words. A call refused so answers {@code false, "system_failure", missing}.
	 */
	String SYSTEM_FAILURE = "system_failure";
	/** What's missing: a robot's legs (it's on its thrusters, or they're gone). */
	String MISSING_LEGS = "legs";
	/** What's missing: a robot's thrusters (a tier 1 has none). */
	String MISSING_THRUSTERS = "thrusters";
	/** What's missing: power. */
	String MISSING_POWER = "power";
	/** What's missing: what works a crafting card's items (it's in nothing that does). */
	String MISSING_BENCH = "bench";
	/** What's missing: a robot's robotics, to work a crafting card (in a case there's none). */
	String MISSING_ROBOTICS = "robotics";

	/** A refusal: what it needs is missing ({@link #SYSTEM_FAILURE}, and what). */
	static com.payonel.modularcomputers.runtime.Result missing(String what) {
		return com.payonel.modularcomputers.runtime.Result.fail(SYSTEM_FAILURE, what);
	}

	/** A notice's why: a robot shoved off where its last command left it ({@code drift}), once the shove's over. */
	String SHOVE = "shove";
	/**
	 * A notice's why: a robot's fall nothing asked for, said as it begins and as it lands (the user's,
	 * 2026-11-04): {@code drift}, how far it is off where its last command left it, and {@code below},
	 * the block under it then.
	 */
	String FALL = "fall";
	/**
	 * A refusal (wait: a field holds it), and a notice's why: a field taking hold of a robot, and
	 * letting go ({@code id}: the field's, or "").
	 */
	String HELD = "held";

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
