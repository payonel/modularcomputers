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
	/** What it needs to do it is missing ({@code missing}). */
	public static final String SYSTEM_FAILURE = "system_failure";
	/** An argument it can't use: which ({@code argument}), as given ({@code value}), what it takes ({@code expected}). */
	public static final String BAD_ARGUMENT = "bad_argument";
	/** Not allowed ({@code reason}), and what was refused ({@code value}). */
	public static final String DENIED = "denied";
	/** Over a limit: of what ({@code of}), and its most ({@code max}). */
	public static final String LIMIT = "limit";
	/** Failed in transit, or the other end answered badly ({@code reason}), and what was said of it ({@code message}). */
	public static final String NETWORK = "network";
	/** What it names isn't there: what kind of thing ({@code kind}), and as given ({@code value}). */
	public static final String NOT_FOUND = "not_found";
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

	public static Failure systemFailure(String missing) {
		return new Failure(SYSTEM_FAILURE, "missing", missing);
	}

	public static Failure badArgument(int argument, Object value, String expected) {
		return new Failure(BAD_ARGUMENT, "argument", (long) argument, "value", value == null ? "" : value, "expected", expected);
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

	public static Failure missingInputs(String name) {
		return new Failure(MISSING_INPUTS, "name", name);
	}

	public static Failure noSpace(String name) {
		return new Failure(NO_SPACE, "name", name);
	}

	/**
	 * A refusal not yet given a why of its own (the user's, 2026-11-04: every refusal a table now,
	 * these named one by one later): its old text as its why, and no fields. Nothing new uses it.
	 */
	public static Failure legacy(String text) {
		return new Failure(text == null ? "unknown error" : text);
	}

	public String why() {
		return why;
	}

	/** Its fields, by name (none for a legacy one). */
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
