package com.payonel.modularcomputers.device;

import java.util.Set;

/**
 * A buffered device (.agents/BUFFERED.md): it runs one command at a time. Its methods are commands,
 * but those it names {@link #free} (its live state, its events, {@code stop}): the board refuses a
 * command at once while the device {@link #refusal refuses} one (another is running: {@link #BUSY}),
 * doing nothing, and the device raises an event, named after the command's method, as each ends
 * ({@link EventQueue#ended}), its "idle again". Every other device is direct.
 */
public interface Buffered {
	/** Why a command is refused while another is running. */
	String BUSY = "busy";

	/** Its methods that answer at once, always: not commands. */
	Set<String> free();

	/** Why it can't take a command now (null: it can): {@link #BUSY} while one runs, or its own reason (a golem in a field: held). */
	String refusal();
}
