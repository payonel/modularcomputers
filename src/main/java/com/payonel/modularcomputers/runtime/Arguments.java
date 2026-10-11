package com.payonel.modularcomputers.runtime;

import java.util.List;

/**
 * The arguments a program passed to a device method. A check that fails throws
 * {@link ArgumentError}, which the call turns into a soft failure ("bad argument #2 (number
 * expected, got string)"): the program gets it back, the machine carries on.
 */
public final class Arguments {
	/** A bad argument: becomes the call's failure message. */
	public static final class ArgumentError extends RuntimeException {
		public ArgumentError(String message) {
			super(message);
		}
	}

	private final List<Object> values;

	public Arguments(List<Object> values) {
		this.values = values.stream().map(Values::normalize).toList();
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
		throw bad(index, value instanceof Double ? "number has no integer representation" : "number expected, got " + Values.typeName(value));
	}

	public double checkNumber(int index) {
		Object value = get(index);
		if (value instanceof Long l) {
			return l;
		}
		if (value instanceof Double d) {
			return d;
		}
		throw bad(index, "number expected, got " + Values.typeName(value));
	}

	public String checkString(int index) {
		Object value = get(index);
		if (value instanceof String s) {
			return s;
		}
		if (value instanceof Long || value instanceof Double) {
			return String.valueOf(value); // Lua converts numbers to strings too
		}
		throw bad(index, "string expected, got " + Values.typeName(value));
	}

	public boolean checkBoolean(int index) {
		if (get(index) instanceof Boolean b) {
			return b;
		}
		throw bad(index, "boolean expected, got " + Values.typeName(get(index)));
	}

	public long optInteger(int index, long fallback) {
		return get(index) == null ? fallback : checkInteger(index);
	}

	public String optString(int index, String fallback) {
		return get(index) == null ? fallback : checkString(index);
	}

	private static ArgumentError bad(int index, String why) {
		return new ArgumentError("bad argument #" + (index + 1) + " (" + why + ")");
	}
}
