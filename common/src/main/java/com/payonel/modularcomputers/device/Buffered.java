package com.payonel.modularcomputers.device;

import java.util.Set;

/**
 * A buffered device (AGENTS.md: buffered devices): it runs one command at a time. Its methods are
 * commands, but those it names {@link #free} (its live state, its events, {@code stop}): the board refuses a
 * command at once while the device {@link #refusal refuses} one (another is running: {@link #BUSY}),
 * doing nothing; a command that runs over time raises an event, named after its method, as it ends
 * ({@link EventQueue#ended}), its "idle again". Every other device is direct.
 */
public interface Buffered {
	/** Why a command is refused while another is running. */
	String BUSY = "busy";

	/** Its methods that answer at once, always: not commands. */
	Set<String> free();

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
