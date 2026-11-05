package com.payonel.modularcomputers.runtime;

import java.util.List;

/**
 * The arguments a program passed to a device method. A check that fails throws
 * {@link ArgumentError}, which the call turns into a soft failure, {@code bad_argument} (its
 * position, the value given, what it takes: {@code {why = "bad_argument", argument = 2, value =
 * "x", expected = "number"}}): the program gets it back, the machine carries on.
 */
public final class Arguments {
	/** A bad argument: becomes the call's failure, {@link Failure#badArgument}. */
	public static final class ArgumentError extends RuntimeException {
		private final Failure failure;

		/** Argument {@code argument} (from 1) can't be {@code value}: it takes {@code expected} (a word). */
		public ArgumentError(int argument, Object value, String expected) {
			this(Failure.badArgument(argument, value, expected));
		}

		public ArgumentError(Failure failure) {
			super(failure.toString());
			this.failure = failure;
		}

		public Failure failure() {
			return failure;
		}
	}

	private final List<Object> values;
	/** The calling program's memory, for what the device holds for it outside its VM. */
	private final MemoryAccount account;

	public Arguments(List<Object> values) {
		this(values, MemoryAccount.UNLIMITED);
	}

	public Arguments(List<Object> values, MemoryAccount account) {
		this.values = values.stream().map(Values::normalize).toList();
		this.account = account;
	}

	/** The calling program's memory: a device charges what it holds for the program to it. */
	public MemoryAccount account() {
		return account;
	}

	/** Its values, as given. */
	public List<Object> values() {
		return values;
	}

	public int count() {
		return values.size();
	}

	/** The raw value at {@code index} (0-based), or nil. */
	public Object get(int index) {
		return index < values.size() ? values.get(index) : null;
	}

	public long checkInteger(int index) {
		Object value = get(index);
		if (value instanceof Long l) {
			return l;
		}
		if (value instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) {
			return d.longValue(); // Lua converts a float with an exact integer value
		}
		throw bad(index, value, "integer");
	}

	public double checkNumber(int index) {
		Object value = get(index);
		if (value instanceof Long l) {
			return l;
		}
		if (value instanceof Double d) {
			return d;
		}
		throw bad(index, value, "number");
	}

	public String checkString(int index) {
		Object value = get(index);
		if (value instanceof String s) {
			return s;
		}
		if (value instanceof Long || value instanceof Double) {
			return String.valueOf(value); // Lua converts numbers to strings too
		}
		throw bad(index, value, "string");
	}

	public boolean checkBoolean(int index) {
		if (get(index) instanceof Boolean b) {
			return b;
		}
		throw bad(index, get(index), "boolean");
	}

	/** A table, as a map (a sequence's keys are its indexes, from 1; an empty table is an empty map). */
	public java.util.Map<?, ?> checkTable(int index) {
		if (get(index) instanceof java.util.Map<?, ?> map) {
			return map;
		}
		if (get(index) instanceof java.util.List<?> list) {
			java.util.Map<Object, Object> map = new java.util.LinkedHashMap<>();
			for (int i = 0; i < list.size(); i++) {
				map.put((long) i + 1, list.get(i));
			}
			return map;
		}
		throw bad(index, get(index), "table");
	}

	public java.util.Map<?, ?> optTable(int index) {
		return get(index) == null ? null : checkTable(index);
	}

	public long optInteger(int index, long fallback) {
		return get(index) == null ? fallback : checkInteger(index);
	}

	public String optString(int index, String fallback) {
		return get(index) == null ? fallback : checkString(index);
	}

	public boolean optBoolean(int index, boolean fallback) {
		return get(index) == null ? fallback : checkBoolean(index);
	}

	/** Argument {@code index} (from 0) is {@code value}, not what it takes ({@code expected}, a word: a type's name, or what it names). */
	private static ArgumentError bad(int index, Object value, String expected) {
		return new ArgumentError(index + 1, given(value), expected);
	}

	/** A value as a failure says it was given: itself, or its type's name for one a table can't hold as such (a handle). */
	public static Object given(Object value) {
		return value == null || value instanceof String || value instanceof Number || value instanceof Boolean ? value : Values.typeName(value);
	}
}
