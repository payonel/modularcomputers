package com.payonel.modularcomputers.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Why a call was refused (the user's, 2026-11-04: one shape a why): a word, and its own named fields,
 * every one always there ({@code ""} when it has nothing to say, never missing). Only the builders
 * here make one, a builder a why, each taking all of its why's fields, so no device can give a why
 * another shape. A program gets it as a table, {@code false, {why = ..., ...}}; a field's name means
 * the same everywhere (the field dictionary: {@code name} a registry id, {@code id} a UUID...).
 */
public final class Failure {
	/** Wait: the device named ({@code id}) has news when it's free. */
	public static final String BUSY = "busy";
	/** Wait: a field holds the robot ({@code id}: the robot's device, whose news ends it). */
	public static final String HELD = "held";
	/** Positional, off its spot: how far off ({@code drift}). */
	public static final String DRIFTED = "drifted";
	/**
	 * Something wrong with the machine itself, not with how it was asked: what's wrong ({@code
	 * condition}: {@link #MISSING}, {@link #IO_ERROR}, {@link #INTERNAL_ERROR}), with what ({@code
	 * part}), and a person's words for it ({@code message}: information, never a string to parse).
	 */
	public static final String FAULT = "fault";
	/** A fault's condition: a part it needs isn't there. */
	public static final String MISSING = "missing";
	/** A fault's condition: its storage failed as it read or wrote (the host's error logged, never said). */
	public static final String IO_ERROR = "io_error";
	/** A fault's condition: the host failed (a bug: the exception logged, never said: it could tell of the host). */
	public static final String INTERNAL_ERROR = "internal_error";
	/** What a fault with its storage says. */
	public static final String IO_ERROR_MESSAGE = "i/o error";
	/** What the host's failure says. */
	public static final String INTERNAL_ERROR_MESSAGE = "internal error";
	/** Each part a fault can find missing, and what it says (a person's words, never parsed). */
	private static final Map<String, String> REQUIRED = Map.ofEntries(
		Map.entry("legs", "legs required"),
		Map.entry("thrusters", "thrusters required"),
		Map.entry("power", "power required"),
		Map.entry("bench", "crafting bench required"),
		Map.entry("robotics", "robotics required"),
		Map.entry("body", "robot body required"),
		Map.entry("position", "known position required"),
		Map.entry("projector", "field projector required"),
		Map.entry("destination", "storage disk target required"),
		Map.entry("source", "source disk required"));
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(com.payonel.modularcomputers.ModularComputers.MOD_ID);
	/** An argument it can't use: which ({@code argument}), as given ({@code value}), what it takes ({@code expected}). */
	public static final String BAD_ARGUMENT = "bad_argument";
	/**
	 * The call is fine, but what it acts on isn't in the state it needs (the user's, 2026-11-05: the
	 * twin of a bad argument): what kind of thing ({@code of}), which one as named ({@code value}),
	 * the state the call needs ({@code expected}, a word).
	 */
	public static final String BAD_STATE = "bad_state";
	/** Not allowed ({@code reason}), and what was refused ({@code value}). */
	public static final String DENIED = "denied";
	/** Over a limit: of what ({@code of}), and its most ({@code max}). */
	public static final String LIMIT = "limit";
	/** Failed in transit, or the other end answered badly ({@code reason}), and what was said of it ({@code message}). */
	public static final String NETWORK = "network";
	/** What it names isn't there: what kind of thing ({@code kind}), and as given ({@code value}). */
	public static final String NOT_FOUND = "not_found";
	/**
	 * A path met a symbolic link (the user's, 2026-11-05; SMB2's STATUS_STOPPED_ON_SYMLINK): the disk
	 * never follows one, so the call stops there, saying the link's path on the disk ({@code at}) and
	 * its target ({@code target}), and the OS's VFS goes on from it.
	 */
	public static final String SYMBOLIC_LINK = "symbolic_link";
	/** A craft's grid makes nothing ({@code name}: what it was making, "" none). */
	public static final String MISSING_INPUTS = "missing_inputs";
	/** No room for what it makes ({@code name}: what). */
	public static final String NO_SPACE = "no_space";

	private final String why;
	private final Map<String, Object> fields;

	private Failure(String why, Object... fields) {
		this.why = why;
		Map<String, Object> named = new LinkedHashMap<>();
		for (int i = 0; i < fields.length; i += 2) {
			String field = (String) fields[i];
			named.put(field, Values.normalize(Objects.requireNonNull(fields[i + 1], () -> why + "'s " + field + " is missing")));
		}
		this.fields = Collections.unmodifiableMap(named);
	}

	public static Failure busy(String id) {
		return new Failure(BUSY, "id", id);
	}

	public static Failure held(String id) {
		return new Failure(HELD, "id", id);
	}

	public static Failure drifted(Map<?, ?> drift) {
		return new Failure(DRIFTED, "drift", drift);
	}

	/** A part it needs isn't there ({@code part}: one {@link #REQUIRED} names). */
	public static Failure missing(String part) {
		String message = REQUIRED.get(part);
		if (message == null) {
			throw new IllegalArgumentException("no missing part " + part);
		}
		return fault(MISSING, part, message);
	}

	/** Its storage ({@code part}) failed as it read or wrote: the host's error goes to the log, never to the program. */
	public static Failure ioError(String part, Throwable cause) {
		LOGGER.warn("{}: i/o error", part, cause);
		return fault(IO_ERROR, part, IO_ERROR_MESSAGE);
	}

	/**
	 * The host failed doing it ({@code part}: the device's type, or {@code board}): the exception goes
	 * to the log, never to the program (its text could tell of the host: paths, its classes).
	 */
	public static Failure internalError(String part, Throwable cause) {
		LOGGER.error("{}: internal error", part, cause);
		return fault(INTERNAL_ERROR, part, INTERNAL_ERROR_MESSAGE);
	}

	private static Failure fault(String condition, String part, String message) {
		return new Failure(FAULT, "condition", condition, "part", part, "message", message);
	}

	public static Failure badArgument(int argument, Object value, String expected) {
		return new Failure(BAD_ARGUMENT, "argument", (long) argument, "value", value == null ? "" : value, "expected", expected);
	}

	/**
	 * {@code of} (a word: {@code hold}, {@code path}, {@code slot}, {@code handle}, {@code entity}),
	 * {@code value} (which: a path, a slot, an id; "" none), {@code expected} (a word: {@code empty},
	 * {@code holding}, {@code absent}, {@code file}, {@code link}, {@code not_root}, {@code
	 * outside_source}, {@code open}, {@code readable}, {@code writable}, {@code free}).
	 */
	public static Failure badState(String of, Object value, String expected) {
		return new Failure(BAD_STATE, "of", of, "value", value == null ? "" : value, "expected", expected);
	}

	public static Failure denied(String reason, String value) {
		return new Failure(DENIED, "reason", reason, "value", value);
	}

	public static Failure limit(String of, long max) {
		return new Failure(LIMIT, "of", of, "max", max);
	}

	public static Failure network(String reason, String message) {
		return new Failure(NETWORK, "reason", reason, "message", message);
	}

	/**
	 * What it names isn't there: {@code kind} (a word: {@code device}, {@code method}, {@code entity},
	 * {@code file}, {@code boot_entry}, {@code offset}, {@code container}, {@code slot}, {@code block}),
	 * and {@code value}, what was named (an address, a name, a face, a number).
	 */
	public static Failure notFound(String kind, Object value) {
		return new Failure(NOT_FOUND, "kind", kind, "value", value == null ? "" : value);
	}

	public static Failure symbolicLink(String at, String target) {
		return new Failure(SYMBOLIC_LINK, "at", at, "target", target);
	}

	public static Failure missingInputs(String name) {
		return new Failure(MISSING_INPUTS, "name", name);
	}

	public static Failure noSpace(String name) {
		return new Failure(NO_SPACE, "name", name);
	}

	public String why() {
		return why;
	}

	/** Its fields, by name. */
	public Map<String, Object> fields() {
		return fields;
	}

	/** A field's value (null: it has none). */
	public Object get(String field) {
		return fields.get(field);
	}

	/** As a program gets it: {why, ...its fields}. */
	public Map<String, Object> table() {
		Map<String, Object> table = new LinkedHashMap<>();
		table.put("why", why);
		table.putAll(fields);
		return table;
	}

	@Override
	public String toString() {
		return table().toString();
	}
}
