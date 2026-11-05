package com.payonel.modularcomputers.runtime;

import java.util.Arrays;
import java.util.List;

/**
 * What a call to a device (or the motherboard) gives back: values, or a failure. Failures are soft,
 * like a device's error bit: the program decides what to do, and the machine never stops because of
 * one. A Lua runtime returns a failure as {@code false, {why, ...}}: its {@link Failure}, built only
 * by that why's own builder.
 */
public record Result(List<Object> values, Failure failure) {
	public static Result ok(Object... values) {
		return new Result(Arrays.stream(values).map(Values::normalize).toList(), null);
	}

	public static Result fail(Failure failure) {
		return new Result(List.of(), java.util.Objects.requireNonNull(failure));
	}

	public boolean failed() {
		return failure != null;
	}

	/** Why it failed (its failure's why), or null: it didn't. */
	public String error() {
		return failure == null ? null : failure.why();
	}

	/** The first value, or nil (null). */
	public Object first() {
		return values.isEmpty() ? null : values.getFirst();
	}
}
