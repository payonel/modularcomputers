package com.payonel.modularcomputers.runtime;

import java.util.Arrays;
import java.util.List;

/**
 * What a call to a device (or the motherboard) gives back: values, or a failure with a
 * message. Failures are soft, like a device's error bit: the program decides what to do, and the
 * machine never stops because of one. A Lua runtime returns a failure as {@code nil, message}.
 */
public record Result(List<Object> values, String error) {
	public static Result ok(Object... values) {
		return new Result(Arrays.stream(values).map(Values::normalize).toList(), null);
	}

	public static Result fail(String message) {
		return new Result(List.of(), message == null ? "unknown error" : message);
	}

	/** A failure that says more: why, then values (a refusal's device to wait on). */
	public static Result fail(String message, Object... values) {
		return new Result(Arrays.stream(values).map(Values::normalize).toList(), message == null ? "unknown error" : message);
	}

	public boolean failed() {
		return error != null;
	}

	/** The first value, or nil (null). */
	public Object first() {
		return values.isEmpty() ? null : values.getFirst();
	}
}
